package top.boluofan.musictv.remote;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import fi.iki.elonen.NanoHTTPD;

public final class RemoteWebResponse {
    private RemoteWebResponse() {
    }

    public static NanoHTTPD.Response afterSent(
            NanoHTTPD.Response.IStatus status, String mimeType, String body,
            Runnable completion) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return NanoHTTPD.newFixedLengthResponse(status, mimeType,
                new CompletionInputStream(bytes, completion), bytes.length);
    }

    private static final class CompletionInputStream extends ByteArrayInputStream {
        private final Runnable completion;
        private final AtomicBoolean completed = new AtomicBoolean();

        private CompletionInputStream(byte[] bytes, Runnable completion) {
            super(bytes);
            this.completion = completion;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (completed.compareAndSet(false, true)) completion.run();
            }
        }
    }
}
