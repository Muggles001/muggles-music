package top.boluofan.musictv.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PageWindowTest {
    @Test
    public void emptySourceStillHasOneEmptyPage() {
        PageWindow<String> page = PageWindow.of(Collections.emptyList(), 4, 8);

        assertEquals(0, page.getPageIndex());
        assertEquals(1, page.getPageCount());
        assertEquals(0, page.getStartIndex());
        assertTrue(page.getItems().isEmpty());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    public void pageContainsAnIndependentSlice() {
        java.util.List<Integer> source = Arrays.asList(0, 1, 2, 3, 4);
        PageWindow<Integer> page = PageWindow.of(source, 1, 3);

        assertEquals(Arrays.asList(3, 4), page.getItems());
        assertEquals(3, page.getStartIndex());
        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
        source.set(3, 30);
        assertEquals(Arrays.asList(3, 4), page.getItems());
    }

    @Test
    public void requestedPageAndMovementAreClamped() {
        PageWindow<Integer> first = PageWindow.of(Arrays.asList(0, 1, 2, 3, 4), -3, 2);
        PageWindow<Integer> last = PageWindow.of(Arrays.asList(0, 1, 2, 3, 4), 8, 2);

        assertEquals(0, first.getPageIndex());
        assertEquals(0, first.pageAfter(-1));
        assertEquals(1, first.pageAfter(1));
        assertEquals(2, last.getPageIndex());
        assertEquals(2, last.pageAfter(1));
    }

    @Test
    public void pageSizeMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> PageWindow.of(Collections.emptyList(), 0, 0));
    }
}
