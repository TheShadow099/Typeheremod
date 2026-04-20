package com.shadow.typehere.keyboard;

import net.minecraft.resources.Identifier;

import java.util.List;

import static com.shadow.typehere.keyboard.KeyboardLayout.Key;
import static com.shadow.typehere.keyboard.KeyboardLayout.KeyFunction.*;

/**
 * A hardcoded QWERTY fallback layout used when the resource-pack–driven layouts
 * have not loaded yet, or when a requested layout ID is not found.
 *
 * <p>This layout exactly mirrors the structure of the JSON layouts but is defined
 * purely in code, so it is always available even in the absence of any resource pack.
 *
 * <p>Adapted from Controlify's {@code FallbackKeyboardLayout}. Controller-shortcut
 * binding references have been removed.
 */
public final class FallbackKeyboardLayout {

    public static final Identifier ID = Identifier.of("typehere", "fallback");

    /**
     * Standard QWERTY layout (13 units wide).
     *
     * <pre>
     *  Row 0:  §  q  w  e  r  t  y  u  i  o  p  [⌫ ×2]
     *  Row 1:  [Tab]  a  s  d  f  g  h  j  k  l  '  [Enter ×2]
     *  Row 2:  [Shift ×2]  z  x  c  v  b  n  m  ,  /  ←  →
     *  Row 3:  ↑  [Space ×11]  ↓
     * </pre>
     */
    public static final KeyboardLayout QWERTY = KeyboardLayout.of(13.0f,

            // Row 0 — digits / top row
            List.of(
                    k("§", "±"),
                    k("q"), k("w"), k("e"), k("r"), k("t"),
                    k("y"), k("u"), k("i"), k("o"), k("p"),
                    k(SpecialFunc.Action.BACKSPACE, 2.0f)
            ),

            // Row 1 — home row
            List.of(
                    k(SpecialFunc.Action.TAB, 1f),
                    k("a"), k("s"), k("d"), k("f"), k("g"),
                    k("h"), k("j"), k("k"), k("l"),
                    k("'", "\""),
                    k(SpecialFunc.Action.ENTER, 2.0f)
            ),

            // Row 2 — bottom letters row
            List.of(
                    k(SpecialFunc.Action.SHIFT, 2f),
                    k("z"), k("x"), k("c"), k("v"), k("b"),
                    k("n"), k("m"),
                    k(",", "."),
                    k("/", "\\"),
                    k(SpecialFunc.Action.LEFT_ARROW, 1f),
                    k(SpecialFunc.Action.RIGHT_ARROW, 1f)
            ),

            // Row 3 — space bar row
            List.of(
                    k(SpecialFunc.Action.UP_ARROW, 1f),
                    k(" ", 11f),
                    k(SpecialFunc.Action.DOWN_ARROW, 1f)
            )
    );

    // -------------------------------------------------------------------------
    // Private factory helpers
    // -------------------------------------------------------------------------

    private static Key k(SpecialFunc.Action action, float width) {
        return new Key(new SpecialFunc(action), width);
    }

    private static Key k(String s) {
        return new Key(new StringFunc(s));
    }

    private static Key k(String regular, String shifted) {
        return new Key(new StringFunc(regular), new StringFunc(shifted));
    }

    private static Key k(String s, float width) {
        return new Key(new StringFunc(s), width);
    }

    private FallbackKeyboardLayout() {}
}
