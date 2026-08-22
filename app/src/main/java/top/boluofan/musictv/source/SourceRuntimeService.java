package top.boluofan.musictv.source;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public final class SourceRuntimeService extends Service {
    private SourceRuntimeEngine engine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), this::handle));

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private boolean handle(Message message) {
        Bundle data = message.getData();
        String requestId = data.getString(SourceRuntimeProtocol.REQUEST_ID, "");
        if (message.what == SourceRuntimeProtocol.LOAD) {
            load(message.replyTo, requestId, data);
            return true;
        }
        if (message.what == SourceRuntimeProtocol.RESOLVE) {
            resolve(message.replyTo, requestId, data);
            return true;
        }
        return false;
    }

    private void load(Messenger replyTo, String requestId, Bundle data) {
        try {
            File scriptFile = new File(data.getString(SourceRuntimeProtocol.SCRIPT_PATH, ""));
            if (!scriptFile.isFile() || scriptFile.length() > SourceScriptImporter.MAX_SCRIPT_BYTES) {
                throw new IllegalArgumentException("音源脚本文件无效");
            }
            byte[] bytes = new byte[(int) scriptFile.length()];
            try (FileInputStream input = new FileInputStream(scriptFile)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = input.read(bytes, offset, bytes.length - offset);
                    if (read < 0) break;
                    offset += read;
                }
                if (offset != bytes.length) throw new IllegalArgumentException("音源脚本读取不完整");
            }
            String script = new String(bytes, StandardCharsets.UTF_8);
            SourceScriptMetadata metadata = SourceScriptMetadata.parse(script);
            ImportedSource source = new ImportedSource(metadata,
                    data.getString(SourceRuntimeProtocol.IMPORT_URL, ""), script, new JsonObject());
            if (engine != null) engine.close();
            engine = new SourceRuntimeEngine(this);
            engine.load(source, new SourceRuntimeEngine.LoadCallback() {
                @Override public void onLoaded(JsonObject capabilities) {
                    reply(replyTo, requestId, true, capabilities.toString(), null);
                }
                @Override public void onError(String error) {
                    reply(replyTo, requestId, false, null, error);
                    restartAfterTimeout(error);
                }
            });
        } catch (Exception error) {
            reply(replyTo, requestId, false, null, error.getMessage());
        }
    }

    private void resolve(Messenger replyTo, String requestId, Bundle data) {
        if (engine == null) {
            reply(replyTo, requestId, false, null, "音源尚未初始化");
            return;
        }
        try {
            JsonElement parsed = new JsonParser().parse(data.getString(SourceRuntimeProtocol.INFO, "{}"));
            JsonObject info = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            engine.resolve(data.getString(SourceRuntimeProtocol.SOURCE, ""),
                    data.getString(SourceRuntimeProtocol.ACTION, "musicUrl"), info,
                    new SourceRuntimeEngine.ResolveCallback() {
                        @Override public void onSuccess(JsonElement result) {
                            reply(replyTo, requestId, true,
                                    result == null ? "null" : result.toString(), null);
                        }
                        @Override public void onError(String error) {
                            reply(replyTo, requestId, false, null, error);
                            restartAfterTimeout(error);
                        }
                    });
        } catch (Exception error) {
            reply(replyTo, requestId, false, null, error.getMessage());
        }
    }

    private void reply(Messenger target, String requestId, boolean ok, String result, String error) {
        if (target == null) return;
        Message message = Message.obtain(null, SourceRuntimeProtocol.RESULT);
        Bundle data = new Bundle();
        data.putString(SourceRuntimeProtocol.REQUEST_ID, requestId);
        data.putBoolean(SourceRuntimeProtocol.OK, ok);
        data.putString(SourceRuntimeProtocol.DATA, result);
        data.putString(SourceRuntimeProtocol.ERROR, error);
        data.putInt(SourceRuntimeProtocol.PID, Process.myPid());
        message.setData(data);
        try {
            target.send(message);
        } catch (RemoteException ignored) {
        }
    }

    private void restartAfterTimeout(String error) {
        if (error != null && error.contains("超时")) {
            mainHandler.postDelayed(() -> Process.killProcess(Process.myPid()), 150);
        }
    }

    @Override
    public void onDestroy() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
        super.onDestroy();
    }
}
