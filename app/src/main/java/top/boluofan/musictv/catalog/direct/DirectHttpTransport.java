package top.boluofan.musictv.catalog.direct;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class DirectHttpTransport {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private final OkHttpClient client;

    public DirectHttpTransport() {
        client = new OkHttpClient.Builder()
                .callTimeout(25, TimeUnit.SECONDS)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public JsonObject getJson(String url) throws IOException {
        return getJson(url, Collections.emptyMap());
    }

    public JsonObject getJson(String url, Map<String, String> headers) throws IOException {
        return parseObject(getText(url, headers));
    }

    public JsonObject postJson(String url, JsonObject body, Map<String, String> headers)
            throws IOException {
        Request.Builder builder = requestBuilder(url, headers)
                .post(RequestBody.create(JSON, GSON.toJson(body)));
        return parseObject(execute(builder.build()));
    }

    public String getText(String url, Map<String, String> headers) throws IOException {
        return execute(requestBuilder(url, headers).get().build());
    }

    private JsonObject parseObject(String text) throws IOException {
        try {
            JsonElement parsed = new JsonParser().parse(text);
            if (!parsed.isJsonObject()) throw new IOException("接口返回的不是 JSON 对象");
            return parsed.getAsJsonObject();
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("音乐目录响应解析失败", error);
        }
    }

    private Request.Builder requestBuilder(String url, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                        + " AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/120.0.0.0 Safari/537.36");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder;
    }

    private String execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("目录接口返回 HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("目录接口返回空内容");
            return body.string();
        }
    }
}
