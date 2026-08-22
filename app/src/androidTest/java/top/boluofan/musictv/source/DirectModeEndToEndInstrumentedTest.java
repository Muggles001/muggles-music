package top.boluofan.musictv.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ComponentName;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import fi.iki.elonen.NanoHTTPD;
import org.junit.Test;
import org.junit.runner.RunWith;
import retrofit2.Response;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.model.ListData;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.backend.MusicApiProvider;
import top.boluofan.musictv.local.LocalLibraryStore;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.PlaybackQueue;

@RunWith(AndroidJUnit4.class)
public class DirectModeEndToEndInstrumentedTest {
    @Test
    public void importsSearchesResolvesAndPersistsWithoutLxserver() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        NanoHTTPD fixture = new NanoHTTPD(19000) {
            @Override public Response serve(IHTTPSession session) {
                if ("/resolve.json".equals(session.getUri())) {
                    return newFixedLengthResponse(Response.Status.OK, "application/json",
                            "{\"url\":\"http://127.0.0.1:19000/audio.mp3\"}");
                }
                if ("/audio.mp3".equals(session.getUri())) {
                    Response redirect = newFixedLengthResponse(Response.Status.REDIRECT,
                            "text/plain", "redirect");
                    redirect.addHeader("Location",
                            "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3");
                    return redirect;
                }
                return newFixedLengthResponse(Response.Status.OK, "text/javascript; charset=utf-8",
                        fixtureScript());
            }
        };
        fixture.start();
        ImportedSource downloaded = new SourceScriptImporter().download("http://127.0.0.1:19000/source.js");
        CountDownLatch activation = new CountDownLatch(1);
        AtomicReference<String> activationError = new AtomicReference<>();
        SourceRuntimeManager.get(context).activate(downloaded, new SourceRuntimeEngine.LoadCallback() {
            @Override public void onLoaded(JsonObject capabilities) {
                activation.countDown();
            }
            @Override public void onError(String error) {
                activationError.set(error);
                activation.countDown();
            }
        });
        assertTrue(activation.await(10, TimeUnit.SECONDS));
        assertEquals(null, activationError.get());
        BackendPreferences.setMode(context, BackendMode.DIRECT_SOURCE);

        LxApiService api = MusicApiProvider.get(context);
        Response<List<MusicInfo>> search = api.searchMusic("周杰伦", "wy", 1, 3).execute();
        assertTrue(search.isSuccessful());
        assertNotNull(search.body());
        assertFalse(search.body().isEmpty());
        MusicInfo song = search.body().get(0);

        JsonObject musicInfo = new Gson().toJsonTree(song).getAsJsonObject();
        String url = SourceRuntimeManager.get(context).resolveMusicUrlBlocking(
                "wy", musicInfo, "320k");
        assertEquals("http://127.0.0.1:19000/audio.mp3", url);
        assertNotNull(PlaybackQueue.createMediaItem(song));

        Response<List<MusicInfo>> kugouSearch = api.searchMusic("周杰伦", "kg", 1, 3).execute();
        assertTrue(kugouSearch.isSuccessful());
        assertNotNull(kugouSearch.body());
        assertFalse(kugouSearch.body().isEmpty());
        MusicInfo kugouSong = kugouSearch.body().get(0);
        JsonObject kugouInfo = new Gson().toJsonTree(kugouSong).getAsJsonObject();
        assertFalse(kugouInfo.get("hash").getAsString().isEmpty());
        assertEquals("http://127.0.0.1:19000/audio.mp3",
                SourceRuntimeManager.get(context).resolveMusicUrlBlocking(
                        "kg", kugouInfo, "320k"));
        assertPlaybackReady(context, kugouSong);

        ListData library = api.getUserList("", "", "").execute().body();
        assertNotNull(library);
        library.getLoveList().add(kugouSong);
        assertTrue(api.updateUserList("", "", "", library).execute().isSuccessful());
        ListData reloaded = api.getUserList("", "", "").execute().body();
        assertNotNull(reloaded);
        assertEquals(1, reloaded.getLoveList().size());

        new LocalLibraryStore(context).clear();
        new SourceScriptStore(context).clear();
        BackendPreferences.clearMode(context);
        fixture.stop();
    }

    private static String fixtureScript() {
        return "/**\n * @name 麻瓜音乐端到端测试源\n * @version 1.0.0\n */\n"
                + "const {EVENT_NAMES,request,on,send}=globalThis.lx;"
                + "const get=()=>new Promise((resolve,reject)=>request("
                + "'http://127.0.0.1:19000/resolve.json',{method:'GET'},"
                + "(error,response)=>error?reject(error):resolve(response.body)));"
                + "on(EVENT_NAMES.request,({source,action,info})=>{"
                + "if(action!=='musicUrl')return Promise.reject(new Error('unsupported action'));"
                + "if(source==='kg'&&!info.musicInfo.hash)return Promise.reject(new Error('missing hash'));"
                + "return get().then(body=>body.url)});"
                + "const s={type:'music',actions:['musicUrl'],qualitys:['128k','320k','flac']};"
                + "send(EVENT_NAMES.inited,{sources:{mg:{...s,name:'咪咕'},kw:{...s,name:'酷我'},"
                + "kg:{...s,name:'酷狗'},tx:{...s,name:'QQ音乐'},wy:{...s,name:'网易云'}}});";
    }

    private static void assertPlaybackReady(Context context, MusicInfo song) throws Exception {
        SessionToken token = new SessionToken(context, new ComponentName(context, MusicService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(context, token).buildAsync();
        MediaController controller = future.get(10, TimeUnit.SECONDS);
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<String> playbackError = new AtomicReference<>();
        Player.Listener listener = new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) ready.countDown();
            }
            @Override public void onPlayerError(PlaybackException error) {
                playbackError.set(error.getMessage());
                ready.countDown();
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            controller.addListener(listener);
            controller.setMediaItem(PlaybackQueue.createMediaItem(song));
            controller.prepare();
            controller.play();
        });
        assertTrue(ready.await(20, TimeUnit.SECONDS));
        assertEquals(null, playbackError.get());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            controller.stop();
            controller.clearMediaItems();
            controller.removeListener(listener);
        });
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> MediaController.releaseFuture(future));
    }
}
