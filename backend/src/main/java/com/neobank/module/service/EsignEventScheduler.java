package com.neobank.module.service;

/**
 * Runs a task after a delay, off the caller's thread. The seam that lets a unit test replace
 * "wait {@code delaySeconds}, for real" with "run it now" — the same idea as swapping
 * {@code applicationTaskExecutor} for {@code Runnable::run} in {@code ApplicationServiceTest}.
 */
public interface EsignEventScheduler {

    void schedule(Runnable task, int delaySeconds);
}
