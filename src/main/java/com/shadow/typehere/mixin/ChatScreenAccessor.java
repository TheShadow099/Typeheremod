package com.shadow.typehere.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor that exposes the protected {@code input} field of {@link ChatScreen}.
 *
 * <p>This lets {@link ChatScreenMixin} read the chat EditBox without using
 * reflection or shadowing, keeping the interaction clean and upgrade-friendly.
 */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {

    /**
     * Returns the chat input {@link EditBox}.
     * This is the text field the player types into; the OSK writes characters to it.
     */
    @Accessor("input")
    EditBox typehere$getInput();
}
