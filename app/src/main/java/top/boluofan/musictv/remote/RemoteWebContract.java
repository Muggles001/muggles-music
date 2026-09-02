package top.boluofan.musictv.remote;

public final class RemoteWebContract {
    public static final int PORT = 8088;

    public static final String SERVER_CONFIG_PATH = "/server-config";
    public static final String SERVER_CONFIG_SUBMIT_PATH = "/server-config/submit";
    public static final String SOURCE_IMPORT_PATH = "/source-import";
    public static final String SOURCE_IMPORT_SUBMIT_PATH = "/source-import/submit";
    public static final String SEARCH_PATH = "/search";
    public static final String SEARCH_SUBMIT_PATH = "/search/submit";
    public static final String PLAYLIST_IMPORT_PATH = "/playlist-import";
    public static final String PLAYLIST_IMPORT_SUBMIT_PATH = "/playlist-import/submit";

    private RemoteWebContract() {
    }

    public static String url(String address, String path) {
        return "http://" + address + ':' + PORT + path;
    }
}
