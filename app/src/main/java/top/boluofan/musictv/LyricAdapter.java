package top.boluofan.musictv;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;


public class LyricAdapter extends RecyclerView.Adapter<LyricAdapter.LyricViewHolder> {

    public static class LyricLine {
        public long timeMs;
        public String text;
        public String rawLine; // 保存原始行内容用于写回

        public LyricLine(long timeMs, String text) {
            this(timeMs, text, "");
        }

        public LyricLine(long timeMs, String text, String rawLine) {
            this.timeMs = timeMs;
            this.text = text;
            this.rawLine = rawLine;
        }
    }

    private List<LyricLine> lyrics = new ArrayList<>();
    private int currentIndex = -1;

    public void setLyrics(List<LyricLine> lyrics) {
        List<LyricLine> padded = new ArrayList<>();
        if (lyrics != null && !lyrics.isEmpty()) {
            padded.add(new LyricLine(-1, ""));
            padded.addAll(lyrics);
            padded.add(new LyricLine(Long.MAX_VALUE, ""));
        }
        this.lyrics = padded;
        this.currentIndex = -1;
        notifyDataSetChanged();
    }

    public void setCurrentIndex(int index) {
        if (this.currentIndex != index) {
            this.currentIndex = index;
            // A TV can show many lyric rows at once. Rebind all visible rows so recycled
            // views cannot retain alpha/scale values from a previous active line.
            notifyDataSetChanged();
        }
    }

    public List<LyricLine> getLyrics() {
        return lyrics;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    @NonNull
    @Override
    public LyricViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lyric, parent, false);
        return new LyricViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LyricViewHolder holder, int position) {
        LyricLine line = lyrics.get(position);
        boolean isCurrent = position == currentIndex;
        holder.tvLyric.setText(formatLyricText(line.text, isCurrent));

        if (isCurrent) {
            // 当前行：最亮，粗体大字
            holder.tvLyric.setTextColor(0xFFFFFFFF);
            holder.tvLyric.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f);
            holder.tvLyric.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.tvLyric.setShadowLayer(12, 0, 4, 0xE0000000);
            holder.itemView.setAlpha(1.0f);
            holder.itemView.setScaleX(1.04f);
            holder.itemView.setScaleY(1.04f);
            holder.itemView.setBackground(new SoftRoundedBackgroundDrawable(0x0CFFFFFF, 80f));
        } else {
            // 距离越远透明度越低，自然渐出
            int distance = (currentIndex >= 0) ? Math.abs(position - currentIndex) : position + 1;
            float alpha = Math.max(0.10f, 0.55f - Math.max(0, distance - 1) * 0.14f);
            holder.tvLyric.setTextColor(0xFFFFFFFF);
            holder.tvLyric.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f);
            holder.tvLyric.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.tvLyric.setShadowLayer(0, 0, 0, 0);
            holder.itemView.setAlpha(alpha);
            holder.itemView.setScaleX(0.96f);
            holder.itemView.setScaleY(0.96f);
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
    }

    private CharSequence formatLyricText(String text, boolean isCurrent) {
        if (text == null) return "";
        SpannableString styled = new SpannableString(text);
        int translationStart = text.indexOf('\n');
        int originalEnd = translationStart >= 0 ? translationStart : text.length();
        if (isCurrent && originalEnd > 0) {
            styled.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    originalEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        if (translationStart < 0 || translationStart >= text.length() - 1) return styled;

        int spanStart = translationStart + 1;
        styled.setSpan(new RelativeSizeSpan(0.70f), spanStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(
                new ForegroundColorSpan(isCurrent ? 0xCCFFFFFF : 0xAFFFFFFF),
                spanStart,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return styled;
    }

    @Override
    public int getItemCount() {
        return lyrics.size();
    }

    static class LyricViewHolder extends RecyclerView.ViewHolder {
        TextView tvLyric;

        public LyricViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLyric = itemView.findViewById(R.id.tvLyric);
        }
    }
}
