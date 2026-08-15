package top.boluofan.musictv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import android.content.ComponentName;
import top.boluofan.musictv.ConfigActivity;
import top.boluofan.musictv.MusicService;
import top.boluofan.musictv.R;
import top.boluofan.musictv.FloatingPlayerWindow;
import top.boluofan.musictv.api.LxRetrofitClient;

public class SettingsFragment extends Fragment implements MainActivity.PrimaryPageKeyHandler {
    private View rootView;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundResource(0);
        rootView = view;
        initViews();
    }

    private static final String EXTRA_SERVER_URL = "server_url";
    private static final String EXTRA_USERNAME = "username";

    private void initViews() {
        ImageButton btnBack = rootView.findViewById(R.id.btnBack);
        btnBack.setVisibility(View.GONE);

        String serverUrl = LxRetrofitClient.getServerUrl(requireContext());
        String username = LxRetrofitClient.getUsername(requireContext());

        LinearLayout layoutServerConfig = rootView.findViewById(R.id.layoutServerConfig);
        layoutServerConfig.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ConfigActivity.class);
            intent.putExtra(EXTRA_SERVER_URL, serverUrl);
            intent.putExtra(EXTRA_USERNAME, username);
            startActivity(intent);
        });

        LinearLayout layoutUserInfo = rootView.findViewById(R.id.layoutUserInfo);
        layoutUserInfo.setOnClickListener(v -> {
            if (LxRetrofitClient.isLoggedIn(requireContext())) {
                ((MainActivity) requireActivity()).selectPrimaryPage(MainActivity.PAGE_LIBRARY, true);
            } else {
                Intent intent = new Intent(requireContext(), ConfigActivity.class);
                intent.putExtra(EXTRA_SERVER_URL, serverUrl);
                startActivity(intent);
            }
        });

        LinearLayout layoutLogout = rootView.findViewById(R.id.layoutLogout);
        layoutLogout.setOnClickListener(v -> clearConfigAndLogout());

        TextView tvServerUrl = rootView.findViewById(R.id.tvServerUrl);
        tvServerUrl.setText(serverUrl);

        TextView tvUsername = rootView.findViewById(R.id.tvUsername);
        tvUsername.setText(username.isEmpty() ? "未登录" : username);

        ImageButton btnBackgroundPlay = rootView.findViewById(R.id.btnBackgroundPlay);
        LinearLayout layoutBackgroundPlay = rootView.findViewById(R.id.layoutBackgroundPlay);
        updateBackgroundPlayButton(btnBackgroundPlay);

        layoutBackgroundPlay.setOnClickListener(v -> {
            boolean newState = !LxRetrofitClient.getBackgroundPlay(requireContext());
            LxRetrofitClient.setBackgroundPlay(requireContext(), newState);
            Toast.makeText(requireContext(), "后台播放: " + (newState ? "开启" : "关闭"), Toast.LENGTH_SHORT).show();
            updateBackgroundPlayButton(btnBackgroundPlay);
        });
    }

    private void updateBackgroundPlayButton(ImageButton btn) {
        boolean isEnabled = LxRetrofitClient.getBackgroundPlay(requireContext());
        btn.setBackgroundResource(isEnabled ? R.drawable.toggle_on_new : R.drawable.toggle_off_new);
    }

    private void clearConfigAndLogout() {
        boolean backgroundPlay = LxRetrofitClient.getBackgroundPlay(requireContext());

        if (!backgroundPlay && player != null) {
            player.stop();
            player.clearMediaItems();
        }
        LxRetrofitClient.clearConfig(requireContext());
        Toast.makeText(requireContext(), "配置已清除", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(requireContext(), ConfigActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(requireContext(), new ComponentName(requireContext(), MusicService.class));
        final ListenableFuture<MediaController> pendingController =
                new MediaController.Builder(requireContext(), sessionToken).buildAsync();
        controllerFuture = pendingController;
        controllerFuture.addListener(() -> {
            try {
                MediaController resolved = pendingController.get();
                if (!isAdded() || controllerFuture != pendingController) {
                    MediaController.releaseFuture(pendingController);
                    return;
                }
                player = resolved;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(requireContext()));
    }

    @Override
    public void onStop() {
        super.onStop();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        player = null;
    }

    @Override
    public void onDestroyView() {
        rootView = null;
        super.onDestroyView();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }
}
