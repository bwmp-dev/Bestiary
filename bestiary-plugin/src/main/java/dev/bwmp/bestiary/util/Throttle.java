package dev.bwmp.bestiary.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a message through once per key per window.
 * <p>
 * Every warning that can fire from inside a skill execution goes through one of
 * these. A boss with a broken placeholder casting on a 20-tick timer would
 * otherwise write a gigabyte of identical log lines before anyone noticed, and
 * the one useful line would be indistinguishable from the noise.
 */
public final class Throttle {

    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private final long windowMillis;

    public Throttle(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public static Throttle perMinute() {
        return new Throttle(60_000L);
    }

    public boolean allow(String key) {
        long now = System.currentTimeMillis();
        Long previous = lastSeen.get(key);
        if (previous != null && now - previous < windowMillis) {
            return false;
        }
        lastSeen.put(key, now);
        return true;
    }

    /** Called on reload; a fresh config deserves a fresh chance to complain. */
    public void reset() {
        lastSeen.clear();
    }

    public void purge() {
        long cutoff = System.currentTimeMillis() - windowMillis;
        lastSeen.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
