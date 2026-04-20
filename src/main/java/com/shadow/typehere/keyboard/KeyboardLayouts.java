package com.shadow.typehere.keyboard;

import net.minecraft.resources.Identifier;

/**
 * Registry of well-known keyboard layout identifiers.
 *
 * <p>The actual layouts are loaded from JSON resource files by
 * {@link KeyboardLayoutManager}. These constants are used to look them up.
 *
 * <p>Custom resource packs can supply their own layout IDs and reference them
 * in {@link KeyboardLayout.KeyFunction.ChangeLayoutFunc} keys.
 */
public final class KeyboardLayouts {

    /** Mod namespace used for built-in layout identifiers. */
    private static final String NS = "typehere";

    /**
     * Full QWERTY layout — letters, Shift, Backspace, Enter, arrow keys,
     * and a "?123" key to switch to {@link #SYMBOLS}.
     */
    public static final Identifier FULL = Identifier.of(NS, "full");

    /**
     * Symbols / numbers layout — digits 0–9, punctuation, and an "ABC" key
     * that switches back to {@link #FULL}.
     */
    public static final Identifier SYMBOLS = Identifier.of(NS, "symbols");

    // -------------------------------------------------------------------------
    // Convenience accessors (delegate to the layout manager)
    // -------------------------------------------------------------------------

    /**
     * Returns the loaded {@code full} layout, or the {@link FallbackKeyboardLayout}
     * if the resource has not yet been loaded.
     */
    public static KeyboardLayoutWithId full() {
        return com.shadow.typehere.TypeHereClient.LAYOUT_MANAGER.getLayout(FULL);
    }

    /**
     * Returns the loaded {@code symbols} layout, or the fallback if unavailable.
     */
    public static KeyboardLayoutWithId symbols() {
        return com.shadow.typehere.TypeHereClient.LAYOUT_MANAGER.getLayout(SYMBOLS);
    }

    /** Returns the hardcoded fallback layout (always available). */
    public static KeyboardLayoutWithId fallback() {
        return new KeyboardLayoutWithId(FallbackKeyboardLayout.QWERTY, FallbackKeyboardLayout.ID);
    }

    private KeyboardLayouts() {}
}
