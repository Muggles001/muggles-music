package top.boluofan.musictv.ui.adapter;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import top.boluofan.musictv.R;
import top.boluofan.musictv.util.FocusAnimationHelper;
import top.boluofan.musictv.util.RecyclerFocus;

public final class MusicSourceStripAdapter
        extends RecyclerView.Adapter<MusicSourceStripAdapter.ViewHolder> {
    public interface OnSourceClickListener {
        void onSourceClick(int position);
    }

    public interface OnBoundaryKeyListener {
        boolean onBoundaryKey(int position, int keyCode);
    }

    public interface OnItemBindListener {
        void onItemBind(View itemView, int position);
    }

    private final String[] sourceNames;
    private final OnSourceClickListener clickListener;
    private final OnBoundaryKeyListener boundaryKeyListener;
    private RecyclerView recyclerView;
    private OnItemBindListener itemBindListener;
    private int selectedPosition;

    public MusicSourceStripAdapter(String[] sourceNames, int selectedPosition,
                                   OnSourceClickListener clickListener,
                                   OnBoundaryKeyListener boundaryKeyListener) {
        this.sourceNames = sourceNames;
        this.selectedPosition = selectedPosition;
        this.clickListener = clickListener;
        this.boundaryKeyListener = boundaryKeyListener;
    }

    public void setOnItemBindListener(OnItemBindListener listener) {
        itemBindListener = listener;
    }

    public void setSelectedPosition(int position) {
        if (position == selectedPosition) return;
        int previous = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(previous);
        notifyItemChanged(position);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        if (this.recyclerView == recyclerView) this.recyclerView = null;
        super.onDetachedFromRecyclerView(recyclerView);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.sourceName.setText(sourceNames[position]);
        boolean selected = position == selectedPosition;
        holder.radio.setImageResource(selected ? R.drawable.radio_checked : R.drawable.radio_unchecked);
        holder.itemView.setSelected(selected);
        if (itemBindListener != null) itemBindListener.onItemBind(holder.itemView, position);

        holder.itemView.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION) clickListener.onSourceClick(currentPosition);
        });
        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentPosition > 0) {
                return requestFocusAt(currentPosition - 1);
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    && currentPosition + 1 < getItemCount()) {
                return requestFocusAt(currentPosition + 1);
            }
            return boundaryKeyListener != null
                    && boundaryKeyListener.onBoundaryKey(currentPosition, keyCode);
        });
    }

    @Override
    public int getItemCount() {
        return sourceNames.length;
    }

    public boolean requestFocusAt(int position) {
        if (recyclerView == null || position < 0 || position >= getItemCount()) return false;
        return RecyclerFocus.requestPosition(recyclerView, position);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView sourceName;
        final ImageView radio;

        ViewHolder(View itemView) {
            super(itemView);
            FocusAnimationHelper.applyFocusAnimation(itemView, FocusAnimationHelper.Profile.RAIL);
            sourceName = itemView.findViewById(R.id.tvSourceName);
            radio = itemView.findViewById(R.id.ivRadio);
        }
    }
}
