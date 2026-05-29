package com.jvmd.llmbrainservice.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.function.Supplier;

@Slf4j
public final class RateLimitRetry {

    private RateLimitRetry() {}

    public static <T> T call(Supplier<T> fn, int waitSeconds, String callerName) {
        try {
            return fn.get();
        } catch (Exception e) {
            if (isRateLimitError(e)) {
                log.warn("{}: rate limit hit — waiting {}s before retry", callerName, waitSeconds);
                try {
                    Thread.sleep(waitSeconds * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                try {
                    return fn.get();
                } catch (Exception e2) {
                    throw new RuntimeException(e2.getMessage(), e2);
                }
            }
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static boolean isRateLimitError(Throwable e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg != null && (msg.contains("429")
                || msg.toLowerCase(Locale.ROOT).contains("rate limit")
                || msg.toLowerCase(Locale.ROOT).contains("too many requests"))) return true;
        return isRateLimitError(e.getCause());
    }
}
