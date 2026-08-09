package top.boluofan.musictv;

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
        holder.tvLyric.setText(line.text);

        if (position == currentIndex) {
            // 当前行：最亮，粗体大字
            holder.tvLyric.setTextColor(0xFFFFFFFF);
            holder.tvLyric.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f);
            holder.tvLyric.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.tvLyric.setShadowLayer(12, 0, 4, 0xE0000000);
            holder.itemView.setAlpha(1.0f);
            holder.itemView.setScaleX(1.04f);
            holder.itemView.setScaleY(1.04f);
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
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
