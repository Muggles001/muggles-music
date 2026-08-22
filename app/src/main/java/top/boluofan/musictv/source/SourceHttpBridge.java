package top.boluofan.musictv.source;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class SourceHttpBridge {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    String request(String rawUrl, String optionsJson) {
        JsonObject envelope = new JsonObject();
        try {
            okhttp3.HttpUrl url = okhttp3.HttpUrl.parse(rawUrl);
            if (url == null || !("http".equals(url.scheme()) || "https".equals(url.scheme()))) {
                throw new IOException("只允许 HTTP/HTTPS 请求");
            }
            JsonObject options = parseObject(optionsJson);
            String method = string(options, "method", "GET").toUpperCase(java.util.Locale.US);
            Request.Builder builder = new Request.Builder().url(url);
            builder.header("User-Agent", "Mozilla/5.0 (Linux; Android TV) MugglesMusic/2");

            JsonObject headers = object(options, "headers");
            for (Map.Entry<String, JsonElement> entry : headers.entrySet()) {
                if (!entry.getValue().isJsonNull()) {
                    builder.header(entry.getKey(), entry.getValue().getAsString());
                }
            }

            RequestBody body = buildBody(options, builder);
            if (requiresBody(method) && body == null) body = RequestBody.create(new byte[0]);
            builder.method(method, requiresBody(method) ? body : null);

            try (Response response = client.newCall(builder.build()).execute()) {
                ResponseBody responseBody = response.body();
                if (responseBody != null && responseBody.contentLength() > MAX_RESPONSE_BYTES) {
                    throw new IOException("音源响应超过 4 MB");
                }
                byte[] bytes = responseBody == null ? new byte[0]
                        : readLimited(responseBody.byteStream(), MAX_RESPONSE_BYTES);

                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("statusCode", response.code());
                responseJson.addProperty("statusMessage", response.message());
                responseJson.addProperty("url", response.request().url().toString());
                responseJson.addProperty("ok", response.isSuccessful());
                responseJson.add("headers", headersToJson(response.headers()));
                String text = new String(bytes, StandardCharsets.UTF_8);
                try {
                    responseJson.add("body", new JsonParser().parse(text));
                } catch (Exception ignored) {
                    responseJson.addProperty("body", text);
                }
                envelope.add("response", responseJson);
            }
        } catch (Exception e) {
            envelope.addProperty("error", e.getMessage() == null ? "音源网络请求失败" : e.getMessage());
        }
        return GSON.toJson(envelope);
    }

    private static RequestBody buildBody(JsonObject options, Request.Builder request) {
        JsonObject form = object(options, "form");
        if (!form.entrySet().isEmpty()) {
            FormBody.Builder builder = new FormBody.Builder();
            for (Map.Entry<String, JsonElement> entry : form.entrySet()) {
                builder.add(entry.getKey(), entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString());
            }
            return builder.build();
        }
        if (!options.has("body") || options.get("body").isJsonNull()) return null;
        JsonElement body = options.get("body");
        String contentType = request.build().header("Content-Type");
        MediaType mediaType = contentType == null ? JSON : MediaType.parse(contentType);
        String value = body.isJsonPrimitive() ? body.getAsString() : GSON.toJson(body);
        return RequestBody.create(mediaType, value);
    }

    private static boolean requiresBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private static JsonObject headersToJson(Headers headers) {
        JsonObject result = new JsonObject();
        for (String name : headers.names()) result.addProperty(name, headers.get(name));
        return result;
    }

    private static JsonObject parseObject(String raw) {
        try {
            JsonElement value = new JsonParser().parse(raw == null ? "{}" : raw);
            return value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static JsonObject object(JsonObject parent, String name) {
        return parent.has(name) && parent.get(name).isJsonObject()
                ? parent.getAsJsonObject(name) : new JsonObject();
    }

    private static String string(JsonObject parent, String name, String fallback) {
        return parent.has(name) && parent.get(name).isJsonPrimitive()
                ? parent.get(name).getAsString() : fallback;
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 64 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("音源响应超过 4 MB");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
