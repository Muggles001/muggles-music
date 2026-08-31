package top.boluofan.musictv.player.artwork;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArtworkRequestStateTest {
    @Test
    public void newBindingInvalidatesOldBinding() {
        ArtworkRequestState state = new ArtworkRequestState();
        ArtworkRequestState.Binding first = state.begin();
        ArtworkRequestState.Binding second = state.begin();

        assertFalse(state.isCurrent(first));
        assertTrue(state.isCurrent(second));
        assertNotEquals(first.getGeneration(), second.getGeneration());
    }

    @Test
    public void metadataArtworkBlocksScrapedFallback() {
        ArtworkRequestState state = new ArtworkRequestState();
        ArtworkRequestState.Binding binding = state.begin(true);

        assertTrue(state.hasMetadataArtwork(binding));
        assertFalse(state.canUseScrapedFallback(binding));
    }

    @Test
    public void missingMetadataRetainsCurrentBindingForFallback() {
        ArtworkRequestState state = new ArtworkRequestState();
        ArtworkRequestState.Binding binding = state.begin(false);

        assertFalse(state.hasMetadataArtwork(binding));
        assertTrue(state.canUseScrapedFallback(binding));
    }

    @Test
    public void clearInvalidatesLateResults() {
        ArtworkRequestState state = new ArtworkRequestState();
        ArtworkRequestState.Binding binding = state.begin();
        state.clear();

        assertFalse(state.isCurrent(binding));
        assertFalse(state.hasMetadataArtwork(binding));
        assertFalse(state.canUseScrapedFallback(binding));
    }

    @Test
    public void closeInvalidatesLateResultsAndRejectsNewBinding() {
        ArtworkRequestState state = new ArtworkRequestState();
        ArtworkRequestState.Binding binding = state.begin();
        state.close();
        ArtworkRequestState.Binding afterClose = state.begin(true);

        assertFalse(state.isCurrent(binding));
        assertFalse(state.isCurrent(afterClose));
        assertFalse(state.hasMetadataArtwork(afterClose));
        assertFalse(state.canUseScrapedFallback(afterClose));
    }

    @Test
    public void outOfOrderBindingOnlyCurrentOneCanFallback() {
        ArtworkRequestState state = new ArtworkRequestState();
        ArtworkRequestState.Binding first = state.begin(false);
        ArtworkRequestState.Binding second = state.begin(false);

        assertFalse(state.canUseScrapedFallback(first));
        assertTrue(state.canUseScrapedFallback(second));
    }
}
