package top.boluofan.musictv.player.settings;

import android.os.Bundle;

public final class SleepTimerContract {
    public static final String COMMAND_SET = "settings/sleep/set";
    public static final String COMMAND_GET = "settings/sleep/get";
    public static final String COMMAND_STATE = "settings/sleep/state";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_VALUE = "value";
    public static final String EXTRA_REMAINING = "remaining";
    public static final String MODE_OFF = "off";
    public static final String MODE_MINUTES = "minutes";
    public static final String MODE_SONGS = "songs";

    private SleepTimerContract() {}

    public static Bundle encode(State state) {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_MODE, state.mode);
        bundle.putInt(EXTRA_VALUE, state.value);
        bundle.putInt(EXTRA_REMAINING, state.remaining);
        return bundle;
    }

    public static State decode(Bundle bundle) {
        if (bundle == null) return new State(MODE_OFF, 0, 0);
        return new State(
                bundle.getString(EXTRA_MODE, MODE_OFF),
                bundle.getInt(EXTRA_VALUE, 0),
                bundle.getInt(EXTRA_REMAINING, 0));
    }

    public static final class State {
        public final String mode;
        public final int value;
        public final int remaining;

        public State(String mode, int value, int remaining) {
            this.mode = mode == null ? MODE_OFF : mode;
            this.value = value;
            this.remaining = remaining;
        }
    }
}
