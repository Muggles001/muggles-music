package top.boluofan.musictv.remote.playlistimport;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import fi.iki.elonen.NanoHTTPD;
import top.boluofan.musictv.remote.RemoteWebContract;
import top.boluofan.musictv.remote.RemoteWebResponse;

public final class PlaylistImportWebServer extends NanoHTTPD
        implements PlaylistImportRemoteSession.Server {
    public static final int MAX_LINK_LENGTH = 4096;
    private final String token;
    private final Listener listener;
    private final AtomicBoolean consumed = new AtomicBoolean();

    public PlaylistImportWebServer(int port, String token, Listener listener) {
        super(port);
        this.token = token;
        this.listener = listener;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (!tokenMatches(session)) return response(Response.Status.NOT_FOUND, "页面不存在");
        if (Method.GET.equals(session.getMethod())
                && RemoteWebContract.PLAYLIST_IMPORT_PATH.equals(session.getUri())) {
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8",
                    page(token));
        }
        if (!Method.POST.equals(session.getMethod())
                || !RemoteWebContract.PLAYLIST_IMPORT_SUBMIT_PATH.equals(session.getUri())) {
            return response(Response.Status.NOT_FOUND, "页面不存在");
        }
        if (consumed.get()) return response(Response.Status.CONFLICT, "链接已提交");
        if (bodyTooLarge(session)) return response(Response.Status.BAD_REQUEST, "输入过长");
        try {
            session.parseBody(new HashMap<>());
            String link = session.getParms().get("playlistLink");
            if (link == null || link.trim().isEmpty()) {
                return response(Response.Status.BAD_REQUEST, "请输入歌单链接");
            }
            link = link.trim();
            if (link.length() > MAX_LINK_LENGTH) {
                return response(Response.Status.BAD_REQUEST, "输入过长");
            }
            if (!consumed.compareAndSet(false, true)) {
                return response(Response.Status.CONFLICT, "链接已提交");
            }
            String deliveredLink = link;
            return RemoteWebResponse.afterSent(
                    Response.Status.OK, "text/html; charset=utf-8", successPage(), () -> {
                        if (listener != null) listener.onPlaylistLink(deliveredLink);
                    });
        } catch (IOException | ResponseException error) {
            return response(Response.Status.INTERNAL_ERROR, "提交失败，请重试");
        }
    }

    private boolean tokenMatches(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        return params != null && token != null && token.equals(params.get("token"));
    }

    private static boolean bodyTooLarge(IHTTPSession session) {
        String length = session.getHeaders().get("content-length");
        if (length == null) return false;
        try {
            return Long.parseLong(length) > MAX_LINK_LENGTH + 1024L;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private static Response response(Response.Status status, String message) {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", message);
    }

    private static String page(String token) {
        return "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>麻瓜音乐 · 导入歌单</title><style>"
                + "body{margin:0;min-height:100vh;display:grid;place-items:center;padding:20px;box-sizing:border-box;"
                + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#eef8f3;color:#10231b}"
                + ".panel{width:min(88vw,520px);padding:28px;border:1px solid rgba(255,255,255,.85);border-radius:26px;"
                + "background:rgba(255,255,255,.68);box-shadow:0 18px 48px rgba(31,105,75,.14)}"
                + "h1{margin:0 0 8px;font-size:26px}.sub{margin:0 0 22px;color:#315746;line-height:1.5}"
                + "textarea{box-sizing:border-box;width:100%;min-height:132px;padding:14px 16px;border-radius:16px;"
                + "border:2px solid rgba(0,140,85,.28);background:rgba(255,255,255,.82);color:#10231b;font-size:16px;resize:vertical}"
                + "textarea:focus{outline:none;border-color:#008c55}button{width:100%;height:56px;margin-top:14px;border:0;"
                + "border-radius:16px;background:#00c878;color:#10231b;font-size:16px;font-weight:700}"
                + "</style></head><body><main class='panel'><h1>导入歌单</h1>"
                + "<p class='sub'>粘贴音乐平台歌单链接，提交后请回到电视确认导入。</p>"
                + "<form method='post' action='" + RemoteWebContract.PLAYLIST_IMPORT_SUBMIT_PATH
                + "?token=" + token + "'>"
                + "<textarea name='playlistLink' maxlength='4096' required placeholder='粘贴歌单链接或分享文本'></textarea>"
                + "<button type='submit'>发送到电视</button></form></main></body></html>";
    }

    private static String successPage() {
        return "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><style>"
                + "body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:-apple-system,sans-serif;"
                + "background:#eef8f3;color:#10231b}.panel{padding:34px;border-radius:26px;background:rgba(255,255,255,.72);"
                + "box-shadow:0 18px 48px rgba(31,105,75,.14);text-align:center}h1{color:#008c55}</style>"
                + "</head><body><main class='panel'><h1>已发送</h1>"
                + "<p>请回到电视查看歌单预览并确认导入。</p></main></body></html>";
    }

    public interface Listener {
        void onPlaylistLink(String link);
    }
}
