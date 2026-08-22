package top.boluofan.musictv;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import fi.iki.elonen.NanoHTTPD;

public final class SourceImportWebServer extends NanoHTTPD {
    public interface Listener {
        void onSourceUrl(String url);
    }

    private final Listener listener;

    public SourceImportWebServer(int port, Listener listener) {
        super(port);
        this.listener = listener;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (Method.POST.equals(session.getMethod())) {
            try {
                session.parseBody(new HashMap<>());
                Map<String, String> params = session.getParms();
                String url = params.get("sourceUrl");
                if (url == null || url.trim().isEmpty()) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST,
                            "text/html; charset=utf-8", page("请输入音源地址"));
                }
                listener.onSourceUrl(url.trim());
                return newFixedLengthResponse(Response.Status.OK,
                        "text/html; charset=utf-8", successPage());
            } catch (IOException | NanoHTTPD.ResponseException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                        "text/plain; charset=utf-8", e.getMessage());
            }
        }
        return newFixedLengthResponse(Response.Status.OK,
                "text/html; charset=utf-8", page(""));
    }

    private static String page(String message) {
        return "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>麻瓜音乐 · 导入音源</title><style>"
                + "body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:-apple-system,"
                + "BlinkMacSystemFont,'Segoe UI',sans-serif;background:linear-gradient(135deg,#f7fbf9,#75e1b4);color:#17221d}"
                + ".card{width:min(88vw,520px);padding:30px;border:1px solid rgba(255,255,255,.8);border-radius:26px;"
                + "background:rgba(255,255,255,.82);backdrop-filter:blur(18px);box-shadow:0 18px 48px rgba(0,80,50,.14)}"
                + "h1{margin:0 0 8px;font-size:28px}.sub{color:#617068;margin-bottom:24px}"
                + "input{box-sizing:border-box;width:100%;height:56px;border:1px solid rgba(0,120,75,.25);border-radius:16px;"
                + "padding:0 16px;font-size:16px;background:rgba(255,255,255,.8);outline:none}"
                + "button{width:100%;height:56px;margin-top:16px;border:0;border-radius:16px;background:#00C878;color:white;"
                + "font-size:17px;font-weight:700}.msg{color:#bd3c36;margin:10px 0}</style></head><body><main class='card'>"
                + "<h1>导入 LX 自定义音源</h1><div class='sub'>仅支持 HTTP/HTTPS 的落雪兼容 JavaScript 音源地址</div>"
                + (message.isEmpty() ? "" : "<div class='msg'>" + message + "</div>")
                + "<form method='post'><input name='sourceUrl' type='url' required placeholder='https://example.com/source.js'>"
                + "<button type='submit'>发送到电视并检测</button></form></main></body></html>";
    }

    private static String successPage() {
        return "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><style>"
                + "body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:-apple-system,sans-serif;"
                + "background:linear-gradient(135deg,#f7fbf9,#75e1b4);color:#17221d}"
                + ".card{padding:36px;border:1px solid rgba(255,255,255,.8);border-radius:26px;background:rgba(255,255,255,.82);box-shadow:0 18px 48px rgba(0,80,50,.14);text-align:center}"
                + "h1{color:#00a96d}</style></head><body><main class='card'><h1>已发送</h1>"
                + "<p>电视正在下载并检测音源，可以关闭此页面。</p></main></body></html>";
    }
}
