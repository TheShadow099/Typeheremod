package com.shadow.typehere.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.shadow.typehere.keyboard.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * Injects an on-screen keyboard directly into {@link ChatScreen}.
 *
 * <p>When the chat screen opens, this mixin adds a full-width {@link KeyboardWidget}
 * to the lower half of the screen and shifts the chat input box upward to make room.
 * The keyboard widget is wired to a {@link InputTarget} that types into the chat
 * EditBox.
 *
 * <p>The inline-keyboard approach (embedding the keyboard as a widget inside
 * ChatScreen, rather than opening a separate overlay screen) means the player
 * can see incoming chat messages while typing, and the keyboard is always
 * visible as long as chat is open.
 *
 * <p>To use the overlay-based approach instead (keyboard on top of any screen),
 * bind the toggle key configured in {@link com.shadow.typehere.TypeHereClient}
 * — default: K.
 *
 * <p>Adapted from Controlify's {@code ChatScreenMixin}; all Controlify-specific
 * dependencies replaced with standalone equivalents.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    /** The chat EditBox (protected in ChatScreen — accessed via shadow). */
    @Shadow protected EditBox input;

    /** The embedded keyboard widget; {@code null} when keyboard is hidden. */
    @Unique private KeyboardWidget typehere$keyboard;

    /** Fraction of the screen height occupied by the keyboard (0.0 = hidden, 0.45 = shown). */
    @Unique private static final float KEYBOARD_HEIGHT_FRACTION = 0.45f;

    /** Whether the inline keyboard is currently shown. */
    @Unique private boolean typehere$keyboardVisible = true;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * After ChatScreen.init() builds its widgets, inject the keyboard widget
     * at the bottom of the screen and move the input field upward.
     */
    @Inject(method = "init", at = @At("RETURN"))
    private void typehere$addKeyboard(CallbackInfo ci) {
        if (!typehere$keyboardVisible) return;

        int keyboardHeight = (int) (this.height * KEYBOARD_HEIGHT_FRACTION);
        int keyboardTop    = this.height - keyboardHeight;

        // Move the chat input field above the keyboard
        this.input.setY(keyboardTop - 14);

        // Build the InputTarget that pipes keyboard events into the chat EditBox
        InputTarget chatTarget = new ChatInputTarget(this.input, (ChatScreen) (Object) this);

        // Use the full QWERTY layout (falls back to FallbackKeyboardLayout if resource not loaded)
        KeyboardLayoutWithId layout = KeyboardLayouts.full();

        this.typehere$keyboard = this.addRenderableWidget(new KeyboardWidget(
                0, keyboardTop, this.width, keyboardHeight,
                layout, chatTarget
        ));
    }

    // -------------------------------------------------------------------------
    // ChatInputTarget — connects the OSK to the chat EditBox
    // -------------------------------------------------------------------------

    /**
     * An {@link InputTarget} implementation that routes typed characters and
     * key-code events to the chat screen's {@link EditBox}.
     */
    @Unique
    private record ChatInputTarget(EditBox editBox, ChatScreen screen) implements InputTarget {

        @Override
        public boolean supportsCharInput() { return true; }

        @Override
        public boolean acceptChar(char ch, int modifiers) {
            editBox.charTyped(new CharacterEvent(ch));
            return true;
        }

        @Override
        public boolean supportsKeyCodeInput() { return true; }

        @Override
        public boolean acceptKeyCode(int keycode, int scancode, int modifiers) {
            KeyEvent event = new KeyEvent(keycode, scancode, modifiers);

            // Enter and Escape should be forwarded to the full screen (to submit / close chat)
            boolean bypass = List.of(InputConstants.KEY_RETURN, InputConstants.KEY_ESCAPE)
                    .contains(keycode);

            if (bypass) {
                return ((GuiEventListener) screen).keyPressed(event);
            }
            return editBox.keyPressed(event);
        }

        @Override
        public boolean supportsCursorMovement() { return true; }

        @Override
        public boolean moveCursor(int amount) {
            editBox.moveCursor(amount, false);
            return true;
        }

        @Override
        public boolean supportsCopying() { return true; }

        @Override
        public boolean copy() {
            Minecraft.getInstance().keyboardHandler.setClipboard(editBox.getValue());
            return true;
        }
    }
}
