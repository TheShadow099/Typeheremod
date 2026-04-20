package com.shadow.typehere.keyboard;

/**
 * Represents a consumer of on-screen keyboard input.
 *
 * <p>A target is anything that can receive typed characters and key-code events
 * from the on-screen keyboard — typically an EditBox or the ChatScreen itself.
 *
 * <p>All capabilities are opt-in: each {@code supports*()} method returns
 * {@code false} by default so that partial implementations are safe to use.
 * The keyboard UI disables keys whose capability is not supported by the active target.
 *
 * <p>Adapted from Controlify's {@code InputTarget} with identical interface contract.
 */
public interface InputTarget {

    /** A no-op target that silently discards all input. */
    InputTarget EMPTY = new InputTarget() {};

    // -------------------------------------------------------------------------
    // Character input
    // -------------------------------------------------------------------------

    /** @return {@code true} if this target accepts typed characters. */
    default boolean supportsCharInput() {
        return false;
    }

    /**
     * Types a single character into the target.
     *
     * @param ch        the character to type
     * @param modifiers GLFW modifier bitset (e.g. {@code GLFW_MOD_SHIFT})
     * @return {@code true} if the input was consumed
     */
    default boolean acceptChar(char ch, int modifiers) {
        return false;
    }

    // -------------------------------------------------------------------------
    // Key-code input
    // -------------------------------------------------------------------------

    /** @return {@code true} if this target accepts raw key-code events. */
    default boolean supportsKeyCodeInput() {
        return false;
    }

    /**
     * Sends a key-code event to the target (e.g. Backspace, Enter, arrow keys).
     *
     * @param keycode   GLFW key code (see {@code InputConstants.KEY_*})
     * @param scancode  platform-specific scancode (usually 0 is fine)
     * @param modifiers GLFW modifier bitset
     * @return {@code true} if the input was consumed
     */
    default boolean acceptKeyCode(int keycode, int scancode, int modifiers) {
        return false;
    }

    // -------------------------------------------------------------------------
    // Copying
    // -------------------------------------------------------------------------

    /** @return {@code true} if this target supports copying its text to the clipboard. */
    default boolean supportsCopying() {
        return false;
    }

    /**
     * Copies the current text content to the system clipboard.
     *
     * @return {@code true} if the copy succeeded
     */
    default boolean copy() {
        return false;
    }

    // -------------------------------------------------------------------------
    // Cursor movement
    // -------------------------------------------------------------------------

    /** @return {@code true} if this target supports moving the text cursor. */
    default boolean supportsCursorMovement() {
        return false;
    }

    /**
     * Moves the text cursor by {@code amount} characters.
     *
     * @param amount positive = forward, negative = backward
     * @return {@code true} if the cursor was moved
     */
    default boolean moveCursor(int amount) {
        return false;
    }

    // -------------------------------------------------------------------------
    // Delegated wrapper
    // -------------------------------------------------------------------------

    /**
     * A delegating wrapper that forwards all calls to an inner {@link InputTarget}.
     * Subclass this to override only specific methods (e.g. to intercept Enter).
     */
    class Delegated implements InputTarget {
        private final InputTarget target;

        public Delegated(InputTarget target) {
            this.target = target;
        }

        @Override public boolean supportsCharInput()                          { return target.supportsCharInput(); }
        @Override public boolean acceptChar(char ch, int modifiers)           { return target.acceptChar(ch, modifiers); }
        @Override public boolean supportsKeyCodeInput()                        { return target.supportsKeyCodeInput(); }
        @Override public boolean acceptKeyCode(int k, int s, int m)           { return target.acceptKeyCode(k, s, m); }
        @Override public boolean supportsCopying()                             { return target.supportsCopying(); }
        @Override public boolean copy()                                        { return target.copy(); }
        @Override public boolean supportsCursorMovement()                      { return target.supportsCursorMovement(); }
        @Override public boolean moveCursor(int amount)                        { return target.moveCursor(amount); }
    }
}
