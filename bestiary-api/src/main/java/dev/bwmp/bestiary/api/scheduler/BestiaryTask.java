package dev.bwmp.bestiary.api.scheduler;

/** A handle on scheduled work. */
public interface BestiaryTask {

    void cancel();

    boolean isCancelled();
}
