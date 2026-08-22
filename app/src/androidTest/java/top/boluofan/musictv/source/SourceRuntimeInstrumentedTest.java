package top.boluofan.musictv.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SourceRuntimeInstrumentedTest {
    @Test
    public void loadsOfficialContractAndResolvesPromiseUrl() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String script = "/**\n * @name Runtime Fixture\n * @version 1.0.0\n */\n"
                + "const {EVENT_NAMES,on,send}=globalThis.lx;"
                + "on(EVENT_NAMES.request,({source,action,info})=>Promise.resolve("
                + "'http://127.0.0.1:19000/'+source+'/'+info.musicInfo.songmid+'.mp3'));"
                + "send(EVENT_NAMES.inited,{sources:{wy:{name:'网易云',type:'music',"
                + "actions:['musicUrl'],qualitys:['128k','320k']}}});";
        SourceScriptMetadata metadata = SourceScriptMetadata.parse(script);
        ImportedSource source = new ImportedSource(metadata, "http://127.0.0.1/source.js",
                script, new JsonObject());
        SourceRuntimeEngine engine = new SourceRuntimeEngine(context);
        CountDownLatch loadLatch = new CountDownLatch(1);
        AtomicReference<String> loadError = new AtomicReference<>();
        engine.load(source, new SourceRuntimeEngine.LoadCallback() {
            @Override public void onLoaded(JsonObject capabilities) {
                assertTrue(capabilities.has("wy"));
                loadLatch.countDown();
            }
            @Override public void onError(String error) {
                loadError.set(error);
                loadLatch.countDown();
            }
        });
        assertTrue(loadLatch.await(6, TimeUnit.SECONDS));
        assertNull(loadError.get());

        CountDownLatch resolveLatch = new CountDownLatch(1);
        AtomicReference<String> resolved = new AtomicReference<>();
        AtomicReference<String> resolveError = new AtomicReference<>();
        JsonObject musicInfo = new JsonObject();
        musicInfo.addProperty("songmid", "123");
        JsonObject info = new JsonObject();
        info.addProperty("type", "320k");
        info.add("musicInfo", musicInfo);
        engine.resolve("wy", "musicUrl", info, new SourceRuntimeEngine.ResolveCallback() {
            @Override public void onSuccess(JsonElement result) {
                resolved.set(result.getAsString());
                resolveLatch.countDown();
            }
            @Override public void onError(String error) {
                resolveError.set(error);
                resolveLatch.countDown();
            }
        });
        assertTrue(resolveLatch.await(5, TimeUnit.SECONDS));
        assertNull(resolveError.get());
        assertEquals("http://127.0.0.1:19000/wy/123.mp3", resolved.get());
        engine.close();
    }
}
