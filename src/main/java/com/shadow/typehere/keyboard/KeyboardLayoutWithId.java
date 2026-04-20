package com.shadow.typehere.keyboard;

import net.minecraft.resources.Identifier;

/**
 * Couples a {@link KeyboardLayout} with its resource-pack identifier.
 *
 * <p>Layout objects themselves carry no ID; this record binds an ID to a layout
 * so the keyboard widget can track which layout is active and switch back to a
 * previous one when needed.
 *
 * @param layout the keyboard layout definition
 * @param id     the resource-pack identifier that was used to load this layout
 *               (e.g. {@code typehere:full})
 */
public record KeyboardLayoutWithId(KeyboardLayout layout, Identifier id) {
}
