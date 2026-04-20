package com.shadow.typehere.util;

/**
 * A simple utility to implement hold-to-repeat behaviour for buttons and keys.
 *
 * <p>Usage: each game tick (or input poll), call {@link #shouldAction(boolean)} with
 * the current "is pressed" state. The method returns {@code true} the first frame the
 * button is pressed, then again after an initial delay, and subsequently at a faster
 * repeat interval — mimicking the behaviour of a physical keyboard's auto-repeat.
 *
 * <p>Call {@link #reset()} whenever the button is released or focus changes so the
 * counters are cleared for the next press.
 */
public class HoldRepeatHelper {

    /**
     * Number of ticks to wait before the first repeat fires after the initial press.
     */
    private final int initialDelay;

    /**
     * Number of ticks between subsequent repeats after the initial delay has elapsed.
     */
    private final int repeatInterval;

    /** Tick counter since the button was first pressed (or since the last {@link #reset()}). */
    private int ticksHeld = 0;

    /**
     * Creates a new HoldRepeatHelper.
     *
     * @param initialDelay   ticks before the first auto-repeat fires (e.g. 10)
     * @param repeatInterval ticks between subsequent auto-repeats (e.g. 2)
     */
    public HoldRepeatHelper(int initialDelay, int repeatInterval) {
        this.initialDelay = initialDelay;
        this.repeatInterval = repeatInterval;
    }

    /**
     * Called every tick while checking whether an action should fire.
     *
     * @param isPressed {@code true} if the button / key is currently held down
     * @return {@code true} if an action should fire this tick
     */
    public boolean shouldAction(boolean isPressed) {
        if (!isPressed) {
            reset();
            return false;
        }

        ticksHeld++;

        // Fire immediately on the first tick
        if (ticksHeld == 1) return true;

        // Fire after the initial delay, then at the repeat interval
        if (ticksHeld > initialDelay && (ticksHeld - initialDelay) % repeatInterval == 0) {
            return true;
        }

        return false;
    }

    /**
     * Signals that an action fired during this tick (call after a successful action).
     * This is a no-op in this implementation but left for API parity with Controlify's helper.
     */
    public void onNavigate() {
        // no-op — tick counter already handles repeat timing
    }

    /**
     * Resets the helper as if the button has been released.
     * Call this when focus moves away from a widget or the button is released.
     */
    public void reset() {
        ticksHeld = 0;
    }
}
