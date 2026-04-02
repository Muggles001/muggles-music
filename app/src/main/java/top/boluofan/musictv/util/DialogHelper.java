package top.boluofan.musictv.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import top.boluofan.musictv.R;

public class DialogHelper {

    public interface IDialogCallback {
        void onConfirm();
        void onCancel();
    }

    public static AlertDialog showConfirmDialog(Context context, String title, String message, String confirmText, String cancelText, IDialogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);

        builder.setPositiveButton(confirmText, (dialog, which) -> {
            if (callback != null) callback.onConfirm();
        });

        builder.setNegativeButton(cancelText, (dialog, which) -> {
            if (callback != null) callback.onCancel();
        });

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (positiveButton != null) {
                positiveButton.setTextSize(16);
                positiveButton.setTextColor(Color.WHITE);
                positiveButton.setBackgroundResource(R.drawable.bg_btn_primary);
                positiveButton.setPadding(40, 20, 40, 20);
                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                params.leftMargin = 20;
                positiveButton.setLayoutParams(params);
            }

            if (negativeButton != null) {
                negativeButton.setTextSize(16);
                negativeButton.setTextColor(Color.WHITE);
                negativeButton.setBackgroundResource(R.drawable.bg_btn_secondary);
                negativeButton.setPadding(40, 20, 40, 20);
                negativeButton.setFocusable(true);
                negativeButton.requestFocus();
                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                params.leftMargin = 30;
                negativeButton.setLayoutParams(params);
            }

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            }
        });

        dialog.show();
        return dialog;
    }

    public static AlertDialog showPlaylistPickerDialog(Context context, String title, String[] playlistNames, DialogInterface.OnClickListener onItemClick) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setItems(playlistNames, onItemClick);
        builder.setNegativeButton("取消", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            }
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextSize(16);
                negativeButton.setTextColor(Color.WHITE);
                negativeButton.setBackgroundResource(R.drawable.bg_btn_secondary);
                negativeButton.setPadding(40, 20, 40, 20);
                negativeButton.setFocusable(true);
                negativeButton.requestFocus();
                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                params.leftMargin = 30;
                negativeButton.setLayoutParams(params);
            }
        });

        dialog.show();
        return dialog;
    }

    public static AlertDialog showDeleteConfirmDialog(Context context, String songName, IDialogCallback callback) {
        return showConfirmDialog(context, "删除歌曲", "确定要从歌单中删除《" + songName + "》吗？", "删除", "取消", callback);
    }

    public static AlertDialog showOverwriteConfirmDialog(Context context, String playlistName, IDialogCallback callback) {
        return showConfirmDialog(context, "歌单已存在", "已存在名为「" + playlistName + "」的歌单，是否覆盖？", "覆盖", "取消", callback);
    }
}