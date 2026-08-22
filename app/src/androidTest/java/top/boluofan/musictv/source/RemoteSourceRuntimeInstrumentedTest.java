package top.boluofan.musictv.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteSourceRuntimeInstrumentedTest {
    @Test
    public void isolatedProcessLoadsAndResolvesLxSource() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String script = "/**\n * @name Remote Runtime Fixture\n * @version 1.0.0\n */\n"
                + "const {EVENT_NAMES,on,send}=globalThis.lx;"
                + "on(EVENT_NAMES.request,({source,info})=>Promise.resolve("
                + "'http://127.0.0.1:19000/'+source+'/'+info.musicInfo.songmid+'.mp3'));"
                + "send(EVENT_NAMES.inited,{sources:{wy:{name:'网易云',type:'music',"
                + "actions:['musicUrl'],qualitys:['128k','320k']}}});";
        File file = File.createTempFile("remote-source-", ".js", context.getCacheDir());
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(script.getBytes(StandardCharsets.UTF_8));
        }

        RemoteSourceRuntimeClient client = new RemoteSourceRuntimeClient(context);
        JsonObject capabilities = client.load(file, "http://127.0.0.1/source.js");
        assertTrue(capabilities.has("wy"));
        JsonObject musicInfo = new JsonObject();
        musicInfo.addProperty("songmid", "456");
        JsonObject info = new JsonObject();
        info.addProperty("type", "320k");
        info.add("musicInfo", musicInfo);
        JsonElement result = client.resolve("wy", "musicUrl", info);
        assertEquals("http://127.0.0.1:19000/wy/456.mp3", result.getAsString());
        client.close();
        file.delete();
    }
}
