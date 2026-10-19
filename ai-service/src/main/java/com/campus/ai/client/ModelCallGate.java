package com.campus.ai.client;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** One AI instance: chat, embedding and admin probes share the same bounded slots. */
@Component
public class ModelCallGate {
    private final Semaphore slots;
    public ModelCallGate(@Value("${ai.max-concurrent-model-requests:2}") int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Model concurrency must be positive");
        slots = new Semaphore(maximum);
    }
    public <T> T call(Supplier<T> operation) {
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "模型服务繁忙，请稍后重试；普通校园业务不受影响");
        try { return operation.get(); }
        finally { slots.release(); }
    }
}
