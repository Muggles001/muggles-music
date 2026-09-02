package top.boluofan.musictv.library;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class LibraryMutationCoordinatorTest {
    @Test
    public void startsNextMutationOnlyAfterCurrentCompletion() {
        LibraryMutationCoordinator coordinator = new LibraryMutationCoordinator();
        List<String> events = new ArrayList<>();
        AtomicReference<Runnable> firstCompletion = new AtomicReference<>();

        coordinator.enqueue(completion -> {
            events.add("first-start");
            firstCompletion.set(completion);
        });
        coordinator.enqueue(completion -> {
            events.add("second-start");
            completion.run();
        });

        assertEquals(java.util.Collections.singletonList("first-start"), events);
        firstCompletion.get().run();
        assertEquals(Arrays.asList("first-start", "second-start"), events);
    }
}
