package top.boluofan.musictv.source;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import fi.iki.elonen.NanoHTTPD;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.boluofan.musictv.ConfigActivity;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;

@RunWith(AndroidJUnit4.class)
public class ServerModeUiInstrumentedTest {
    @Test
    public void remoteServerIsVerifiedBeforeOpeningTvHome() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BackendPreferences.clearMode(context);
        LxRetrofitClient.clearConfig(context);
        NanoHTTPD fixture = new NanoHTTPD(19002) {
            @Override public Response serve(IHTTPSession session) {
                if ("/api/user/login".equals(session.getUri())) {
                    return newFixedLengthResponse(Response.Status.OK, "application/json",
                            "{\"success\":true,\"token\":\"test-token\",\"username\":\"muggles\"}");
                }
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{}");
            }
        };
        fixture.start();
        try {
            Intent intent = new Intent(context, ConfigActivity.class);
            try (ActivityScenario<ConfigActivity> scenario = ActivityScenario.launch(intent)) {
                scenario.onActivity(activity -> {
                    activity.findViewById(R.id.btnModeServer).performClick();
                    ((EditText) activity.findViewById(R.id.etUrl))
                            .setText("http://127.0.0.1:19002");
                    ((EditText) activity.findViewById(R.id.etUsername)).setText("muggles");
                    ((EditText) activity.findViewById(R.id.etPassword)).setText("password");
                    activity.findViewById(R.id.btnConnect).performClick();
                });
                Thread.sleep(1500);
                onView(withId(R.id.tabSongSquare)).check(matches(isDisplayed()));
                assertEquals(BackendMode.LXSERVER, BackendPreferences.getMode(context));
            }
        } finally {
            fixture.stop();
            LxRetrofitClient.clearConfig(context);
            BackendPreferences.clearMode(context);
        }
    }

    @Test
    public void failedServerProbeStaysOnConfigAndDoesNotPersistAddress() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BackendPreferences.clearMode(context);
        LxRetrofitClient.clearConfig(context);
        NanoHTTPD fixture = new NanoHTTPD(19004) {
            @Override public Response serve(IHTTPSession session) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{}");
            }
        };
        fixture.start();
        try {
            Intent intent = new Intent(context, ConfigActivity.class);
            try (ActivityScenario<ConfigActivity> scenario = ActivityScenario.launch(intent)) {
                scenario.onActivity(activity -> {
                    activity.findViewById(R.id.btnModeServer).performClick();
                    ((EditText) activity.findViewById(R.id.etUrl))
                            .setText("http://127.0.0.1:19004");
                    activity.findViewById(R.id.btnConnect).performClick();
                });
                Thread.sleep(700);
                scenario.onActivity(activity -> assertEquals("重新连接",
                        ((android.widget.Button) activity.findViewById(R.id.btnConnect))
                                .getText().toString()));
                assertEquals("", LxRetrofitClient.getServerUrl(context));
                assertEquals(BackendMode.NONE, BackendPreferences.getMode(context));
            }
        } finally {
            fixture.stop();
            LxRetrofitClient.clearConfig(context);
            BackendPreferences.clearMode(context);
        }
    }
}
