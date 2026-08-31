package top.boluofan.musictv.player.artwork;

/**
 * Tracks artwork request generations so asynchronous results cannot update a later item.
 */
public final class ArtworkRequestState {
    private long generation;
    private Binding current;
    private boolean closed;

    public Binding begin() {
        return begin(false);
    }

    public Binding begin(boolean metadataArtwork) {
        if (closed) {
            return new Binding(-1L, metadataArtwork);
        }
        Binding binding = new Binding(++generation, metadataArtwork);
        current = binding;
        return binding;
    }

    public boolean isCurrent(Binding binding) {
        return !closed && binding != null && binding == current;
    }

    public boolean hasMetadataArtwork(Binding binding) {
        return isCurrent(binding) && binding.metadataArtwork;
    }

    public boolean canUseScrapedFallback(Binding binding) {
        return isCurrent(binding) && !binding.metadataArtwork;
    }

    public void clear() {
        current = null;
        generation++;
    }

    public void close() {
        closed = true;
        current = null;
        generation++;
    }

    public static final class Binding {
        private final long generation;
        private final boolean metadataArtwork;

        private Binding(long generation, boolean metadataArtwork) {
            this.generation = generation;
            this.metadataArtwork = metadataArtwork;
        }

        public long getGeneration() {
            return generation;
        }
    }
}
