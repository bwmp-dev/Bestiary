package dev.bwmp.bestiary.api.ai;

/**
 * Implemented by an {@link AiController} that can use the NMS tier when one is
 * available.
 * <p>
 * The support object is passed as {@code Object} deliberately: its type lives
 * in bestiary-ai-nms, which neither this module nor bestiary-plugin may name —
 * the whole point of that module is that it is absent below 1.20.5 and must
 * never be class-loaded there.
 */
public interface NmsAware {

    void attachNms(Object support);
}
