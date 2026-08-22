package top.boluofan.musictv.util;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import java.util.Hashtable;
import top.boluofan.musictv.R;

public class DialogHelper {

    public interface IDialogCallback {
        void onConfirm();
        void onCancel();
    }

    public static AlertDialog showConfirmDialog(Context context, String title, String message, String confirmText, String cancelText, IDialogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
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
                styleDialogButton(context, positiveButton, true, false);
            }

            if (negativeButton != null) {
                styleDialogButton(context, negativeButton, false, true);
            }

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            }
        });

        dialog.show();
        return dialog;
    }

    public static AlertDialog showPlaylistPickerDialog(Context context, String title, String[] playlistNames, DialogInterface.OnClickListener onItemClick) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
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
                styleDialogButton(context, negativeButton, false, true);
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

    public static androidx.appcompat.app.AlertDialog showQrCodeDialog(Context context, String title, String hint, String qrCodeUrl, String ipAddress) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_scan_search, null);
        
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvHint = dialogView.findViewById(R.id.tvDialogHint);
        ImageView ivQrCode = dialogView.findViewById(R.id.ivQrCode);
        TextView tvIpAddress = dialogView.findViewById(R.id.tvIpAddress);
        
        tvTitle.setText(title);
        tvHint.setText(hint);
        tvIpAddress.setText(ipAddress);
        
        android.graphics.Bitmap qrBitmap = generateQrCodeBitmap(qrCodeUrl, 512);
        if (qrBitmap != null) {
            ivQrCode.setImageBitmap(qrBitmap);
        }
        
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(context, R.style.CustomAlertDialog);
        builder.setView(dialogView);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            }
        });
        
        return dialog;
    }

    private static android.graphics.Bitmap generateQrCodeBitmap(String content, int size) {
        try {
            com.google.zxing.BarcodeFormat format = com.google.zxing.BarcodeFormat.QR_CODE;
            Hashtable<com.google.zxing.EncodeHintType, String> hints2 = new Hashtable<>();
            hints2.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            hints2.put(com.google.zxing.EncodeHintType.MARGIN, "1");
            
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(content, format, size, size, hints2);
            
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void styleDialogButton(Context context, Button button,
                                          boolean primary, boolean requestFocus) {
        button.setTextSize(16);
        button.setTextColor(ContextCompat.getColorStateList(context, primary
                ? R.color.selector_primary_action_content
                : R.color.selector_action_content));
        button.setBackgroundResource(primary
                ? R.drawable.bg_btn_primary : R.drawable.bg_btn_secondary);
        int horizontal = context.getResources().getDimensionPixelSize(R.dimen.lx_space_md);
        int vertical = context.getResources().getDimensionPixelSize(R.dimen.lx_space_sm);
        button.setPadding(horizontal, vertical, horizontal, vertical);
        button.setMinWidth(context.getResources().getDimensionPixelSize(R.dimen.lx_button_short_width));
        button.setMinHeight(context.getResources().getDimensionPixelSize(R.dimen.lx_control_standard));
        if (button.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams) {
            android.widget.LinearLayout.LayoutParams params =
                    (android.widget.LinearLayout.LayoutParams) button.getLayoutParams();
            params.setMarginStart(context.getResources().getDimensionPixelSize(R.dimen.lx_space_sm));
            button.setLayoutParams(params);
        }
        button.setFocusable(true);
        if (requestFocus) button.requestFocus();
    }
}
