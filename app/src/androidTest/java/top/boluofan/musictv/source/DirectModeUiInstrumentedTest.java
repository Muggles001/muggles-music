package top.boluofan.musictv.source;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.recyclerview.widget.RecyclerView;
import fi.iki.elonen.NanoHTTPD;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.ConfigActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.local.LocalLibraryStore;

@RunWith(AndroidJUnit4.class)
public class DirectModeUiInstrumentedTest {
    @Test
    public void firstRunImportsSourceAndOpensTvHome() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new SourceScriptStore(context).clear();
        BackendPreferences.clearMode(context);
        NanoHTTPD fixture = new NanoHTTPD(19001) {
            @Override public Response serve(IHTTPSession session) {
                return newFixedLengthResponse(Response.Status.OK, "text/javascript; charset=utf-8",
                        "/**\n * @name UI 测试源\n * @version 1.0.0\n */\n"
                                + "const {EVENT_NAMES,on,send}=globalThis.lx;"
                                + "on(EVENT_NAMES.request,()=>Promise.resolve("
                                + "'https://storage.googleapis.com/exoplayer-test-media-0/play.mp3'));"
                                + "const s={type:'music',actions:['musicUrl'],qualitys:['128k','320k']};"
                                + "send(EVENT_NAMES.inited,{sources:{mg:{...s,name:'咪咕'},"
                                + "kw:{...s,name:'酷我'},kg:{...s,name:'酷狗'},"
                                + "tx:{...s,name:'QQ音乐'},wy:{...s,name:'网易云'}}});");
            }
        };
        fixture.start();
        Intent intent = new Intent(context, ConfigActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        onView(withId(R.id.etSourceUrl)).perform(replaceText("http://127.0.0.1:19001/source.js"));
        onView(withId(R.id.btnImportSource)).perform(click());
        Thread.sleep(2500);
        onView(withId(R.id.tabSearch)).check(matches(isDisplayed()));
        onView(withId(R.id.rvSourceList)).check((view, error) -> {
            if (error != null) throw error;
            RecyclerView recycler = (RecyclerView) view;
            assertEquals(5, recycler.getAdapter() == null ? 0 : recycler.getAdapter().getItemCount());
        });
        assertEquals(BackendMode.DIRECT_SOURCE, BackendPreferences.getMode(context));
        fixture.stop();
        new SourceScriptStore(context).clear();
        new LocalLibraryStore(context).clear();
        BackendPreferences.clearMode(context);
    }
}
