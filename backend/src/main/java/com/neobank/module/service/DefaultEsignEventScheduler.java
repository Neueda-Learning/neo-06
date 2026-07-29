package com.neobank.module.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Real implementation of {@link EsignEventScheduler} — used everywhere except
 * {@code EsignMockServiceTest}, which fakes it to run inline.
 *
 * <p>Deliberately NOT exposed as a {@code @Bean} of type {@code Executor}/{@code
 * ScheduledExecutorService}: Spring Boot's own {@code applicationTaskExecutor}
 * auto-configuration backs off the moment ANY bean assignable to {@code Executor} exists in the
 * context ({@code @ConditionalOnMissingBean(Executor.class)}) — which is exactly what happened
 * the first time this was written as an {@code AppConfig} bean, and it broke
 * {@code ApplicationService}'s {@code @Qualifier("applicationTaskExecutor")} dependency in the
 * real (non-test) Spring context. Owning the executor as a private field here, behind this
 * module's OWN interface, keeps it out of that type-based matching entirely.</p>
 */
@Component
public class DefaultEsignEventScheduler implements EsignEventScheduler {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "esign-mock-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void schedule(Runnable task, int delaySeconds) {
        executor.schedule(task, delaySeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
