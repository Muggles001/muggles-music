package top.boluofan.musictv.source;

final class SourceRuntimeProtocol {
    static final int LOAD = 1;
    static final int RESOLVE = 2;
    static final int RESULT = 100;
    static final String REQUEST_ID = "request_id";
    static final String SCRIPT_PATH = "script_path";
    static final String IMPORT_URL = "import_url";
    static final String SOURCE = "source";
    static final String ACTION = "action";
    static final String INFO = "info";
    static final String OK = "ok";
    static final String DATA = "data";
    static final String ERROR = "error";
    static final String PID = "pid";

    private SourceRuntimeProtocol() {}
}
