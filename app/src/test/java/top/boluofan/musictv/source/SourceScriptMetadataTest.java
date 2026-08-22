package top.boluofan.musictv.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SourceScriptMetadataTest {
    @Test
    public void parsesLxHeaderAndStableDigest() {
        String script = "/**\n * @name 测试音源\n * @description 电视测试\n"
                + " * @version 2.1.0\n * @author Muggles\n"
                + " * @homepage https://example.com\n */\n"
                + "globalThis.lx.send(globalThis.lx.EVENT_NAMES.inited,{sources:{}})";

        SourceScriptMetadata first = SourceScriptMetadata.parse(script);
        SourceScriptMetadata second = SourceScriptMetadata.parse(script);

        assertEquals("测试音源", first.name);
        assertEquals("2.1.0", first.version);
        assertEquals(first.sha256, second.sha256);
        assertEquals(first.id, second.id);
        assertNotEquals("", first.sha256);
    }

    @Test
    public void importUrlAllowsExplicitLoopbackHttp() {
        assertEquals("http://127.0.0.1:9527/source.js",
                SourceScriptMetadata.normalizeImportUrl("http://127.0.0.1:9527/source.js"));
        assertEquals("http://localhost/source.js",
                SourceScriptMetadata.normalizeImportUrl("http://localhost/source.js"));
        assertNull(SourceScriptMetadata.normalizeImportUrl("ftp://example.com/source.js"));
        assertNull(SourceScriptMetadata.normalizeImportUrl("example.com/source.js"));
    }
}
