package com.agrovision.kiosk.threading;

import com.agrovision.kiosk.util.LogUtils;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MatchingExecutor
 * 
 * Purpose: Handle heavy string matching (Levenshtein) and database resolution
 * without blocking the OCR engine or the UI.
 */
public final class MatchingExecutor {

    private static final int CORE_THREADS = 2;
    private static final int MAX_THREADS = 4;
    
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            CORE_THREADS,
            MAX_THREADS,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new MatchingThreadFactory()
    );

    private MatchingExecutor() {}

    public static Executor get() {
        return EXECUTOR;
    }

    public static void submit(Runnable task) {
        EXECUTOR.execute(task);
    }

    private static class MatchingThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("Matcher-" + count.getAndIncrement());
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler((thread, throwable) -> 
                LogUtils.e("Matcher thread crashed", throwable)
            );
            return t;
        }
    }
}
