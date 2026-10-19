package com.campus.ai.client;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ModelCallGateTest {
    @Test void boundsConcurrencyAndReleasesAfterSuccessAndFailure() throws Exception {
        var gate = new ModelCallGate(1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var running = executor.submit(() -> gate.call(() -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return "done"; }));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> gate.call(() -> "rejected")).hasMessageContaining("429");
            release.countDown(); assertThat(running.get(3, TimeUnit.SECONDS)).isEqualTo("done");
            assertThatThrownBy(() -> gate.call(() -> { throw new IllegalStateException("fixture failure"); })).hasMessageContaining("fixture failure");
            assertThat(gate.call(() -> "available")).isEqualTo("available");
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
