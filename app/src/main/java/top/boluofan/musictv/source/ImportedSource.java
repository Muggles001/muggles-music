package top.boluofan.musictv.source;

import com.google.gson.JsonObject;

public final class ImportedSource {
    public final SourceScriptMetadata metadata;
    public final String importUrl;
    public final String script;
    public final JsonObject capabilities;

    public ImportedSource(SourceScriptMetadata metadata, String importUrl, String script,
                          JsonObject capabilities) {
        this.metadata = metadata;
        this.importUrl = importUrl;
        this.script = script;
        this.capabilities = capabilities == null ? new JsonObject() : capabilities.deepCopy();
    }
}
