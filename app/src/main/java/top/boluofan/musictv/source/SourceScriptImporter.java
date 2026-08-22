package top.boluofan.musictv.source;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class SourceScriptImporter {
    public static final int MAX_SCRIPT_BYTES = 1024 * 1024;
    private static final long DEFAULT_TOTAL_TIMEOUT_MS = 35000;

    private final OkHttpClient client;
    private final long totalTimeoutMs;

    public SourceScriptImporter() {
        this(new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build(), DEFAULT_TOTAL_TIMEOUT_MS);
    }

    SourceScriptImporter(OkHttpClient client, long totalTimeoutMs) {
        this.client = client;
        this.totalTimeoutMs = totalTimeoutMs;
    }

    public ImportedSource download(String rawUrl) throws IOException {
        String url = SourceScriptMetadata.normalizeImportUrl(rawUrl);
        if (url == null) throw new IOException("只支持 HTTP/HTTPS 音源脚本地址");

        okhttp3.HttpUrl current = okhttp3.HttpUrl.parse(url);
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs);
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (System.nanoTime() >= deadlineNanos) throw new IOException("下载音源超时");
            Request request = new Request.Builder()
                    .url(current)
                    .header("User-Agent", "MugglesMusic/2")
                    .build();
            Call call = client.newCall(request);
            call.timeout().deadlineNanoTime(deadlineNanos);
            try (Response response = call.execute()) {
                String location = response.header("Location");
                if (response.code() >= 300 && response.code() < 400 && location != null) {
                    if (redirect == 5) throw new IOException("音源地址重定向次数过多");
                    okhttp3.HttpUrl next = response.request().url().resolve(location);
                    if (next == null || !("http".equals(next.scheme()) || "https".equals(next.scheme()))) {
                        throw new IOException("音源地址重定向无效");
                    }
                    current = next;
                    continue;
                }
                if (!response.isSuccessful()) {
                    throw new IOException("下载音源失败：HTTP " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) throw new IOException("音源地址返回空内容");
                long declaredLength = body.contentLength();
                if (declaredLength > MAX_SCRIPT_BYTES) throw new IOException("音源脚本超过 1 MB");
                byte[] bytes = readLimited(body.byteStream(), MAX_SCRIPT_BYTES, "音源脚本超过 1 MB");
                String script = new String(bytes, StandardCharsets.UTF_8);
                try {
                    SourceScriptMetadata metadata = SourceScriptMetadata.parse(script);
                    return new ImportedSource(metadata, response.request().url().toString(), script,
                            new JsonObject());
                } catch (IllegalArgumentException e) {
                    throw new IOException(e.getMessage(), e);
                }
            } catch (java.io.InterruptedIOException error) {
                throw new IOException("下载音源超时", error);
            }
        }
        throw new IOException("音源地址重定向失败");
    }

    private static byte[] readLimited(InputStream input, int limit, String error) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 64 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException(error);
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
