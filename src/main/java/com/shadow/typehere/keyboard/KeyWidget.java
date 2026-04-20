package com.shadow.typehere.keyboard;

import com.mojang.blaze3d.platform.InputConstants;
import com.shadow.typehere.util.HoldRepeatHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

/**
 * A single key widget within a {@link KeyboardWidget}.
 *
 * <p>Handles its own rendering (background sprite + foreground text) and
 * click events, then delegates the resulting action to the parent
 * {@link KeyboardWidget}'s active {@link InputTarget}.
 *
 * <p>Rendering uses two nine-slice sprites loaded from the mod's resource pack:
 * <ul>
 *   <li>{@link #SPRITE}         — normal key state</li>
 *   <li>{@link #SPRITE_PRESSED} — visually-depressed key state</li>
 * </ul>
 *
 * <p>Adapted from Controlify's {@code KeyWidget} with all controller-input
 * code removed. Only mouse clicks are handled.
 */
public class KeyWidget extends AbstractWidget {

    /** Sprite used for the normal (unpressed) key background. */
    public static final Identifier SPRITE         = Identifier.of("typehere", "keyboard/key");
    /** Sprite used for the visually-pressed key background. */
    public static final Identifier SPRITE_PRESSED = Identifier.of("typehere", "keyboard/key_pressed");

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final KeyboardWidget keyboard;
    private final KeyboardLayout.Key key;

    /** Pre-computed label component for the normal (unshifted) state. */
    private final Component regularLabel;
    /** Pre-computed label component for the shifted state. */
    private final Component shiftedLabel;

    /** {@code true} if the normal-state action is supported by the active InputTarget. */
    private final boolean supportsRegular;
    /** {@code true} if the shifted-state action is supported by the active InputTarget. */
    private final boolean supportsShifted;

    /** Mouse-button is currently held over this key. */
    private boolean mousePressed;

    /**
     * Hold-to-repeat helper so that holding down a key (e.g. Backspace) repeats the action.
     * Not used for mouse currently but reserved for future keyboard navigation support.
     */
    private final HoldRepeatHelper holdRepeatHelper = new HoldRepeatHelper(10, 2);

    /**
     * Scale factor applied when the key area is small.
     * A scale of 2 means the entire key content is rendered at 2× size.
     */
    private final int renderScale;
    /** Key pixel width divided by renderScale. */
    private final int renderWidth;
    /** Key pixel height divided by renderScale. */
    private final int renderHeight;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param x           pixel X of the key's top-left corner
     * @param y           pixel Y of the key's top-left corner
     * @param width       pixel width of the key
     * @param height      pixel height of the key
     * @param renderScale visual scale applied to the key's content rendering
     * @param key         the key definition (label, function, width weight)
     * @param keyboard    the parent {@link KeyboardWidget} that owns this key
     */
    public KeyWidget(int x, int y, int width, int height, int renderScale,
                     KeyboardLayout.Key key, KeyboardWidget keyboard) {
        super(x, y, width, height, Component.literal("Key"));
        this.keyboard = keyboard;
        this.key = key;
        this.renderScale = renderScale;
        this.renderWidth  = width  / renderScale;
        this.renderHeight = height / renderScale;

        this.regularLabel = createLabel(key, false);
        this.shiftedLabel = createLabel(key, true);
        this.supportsRegular  = supportsAction(false);
        this.supportsShifted  = supportsAction(true);
    }

    // -------------------------------------------------------------------------
    // Rendering (MC 26.1 GuiGraphicsExtractor API)
    // -------------------------------------------------------------------------

    /**
     * Renders the key's background sprite (key body).
     * Called by {@link KeyboardWidget} in a batched loop so all key backgrounds
     * are uploaded in a single draw call before foregrounds are rendered.
     *
     * @param graphics graphics extractor
     * @param mouseX   current mouse X
     * @param mouseY   current mouse Y
     * @param a        partial tick
     */
    public void extractKeyBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.active = supportsCurrentAction();

        doScaledExtraction(graphics, () -> {
            Identifier sprite = isVisuallyPressed() ? SPRITE_PRESSED : SPRITE;

            // Draw the nine-slice key background (1px inset from the key bounds)
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                    getX() + 1, getY() + 1,
                    renderWidth - 2, renderHeight - 2);

            // Hover / focus outline
            if (isHoveredOrFocused()) {
                graphics.outline(getX() - 1, getY() - 1, renderWidth + 2, renderHeight + 2, 0x80FFFFFF);
            }

            // Inactive overlay — gray out when the target doesn't support this key's action
            if (!this.active) {
                graphics.fill(getX() + 1, getY() + 1,
                        getX() + renderWidth - 1, getY() + renderHeight - 1,
                        0x30000000);
            }
        });
    }

    /**
     * Renders the key label (text).
     * Called by {@link KeyboardWidget} in a second batched loop so text is
     * always rendered on top of all key backgrounds.
     *
     * @param graphics  graphics extractor
     * @param mouseX    current mouse X
     * @param mouseY    current mouse Y
     * @param deltaTick partial tick
     */
    public void extractKeyForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTick) {
        doScaledExtraction(graphics, () -> {
            Component label = keyboard.isShifted() ? shiftedLabel : regularLabel;
            // Shift the label down by 2px when the key is visually pressed (tactile feel)
            int textY = getY() + renderHeight / 2 - 4 + (isVisuallyPressed() ? 2 : 0);
            graphics.centeredText(Minecraft.getInstance().font, label,
                    getX() + renderWidth / 2, textY, 0xFFFFFFFF);
        });
    }

    /** Overrides the base render — all drawing is done through the two custom extract methods above. */
    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Intentionally empty: KeyboardWidget calls extractKeyBackground / extractKeyForeground directly.
    }

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (isMouseOver(event.x(), event.y())) {
            this.mousePressed = true;
            onPress();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        this.mousePressed = false;
        return super.mouseReleased(event);
    }

    // -------------------------------------------------------------------------
    // Key activation logic
    // -------------------------------------------------------------------------

    /**
     * Fires the key's action: inserts text, sends a key code, or changes state.
     * Called whenever the key is pressed (mouse click).
     */
    private void onPress() {
        KeyboardLayout.KeyFunction fn = getKeyFunction();
        InputTarget target = keyboard.getInputTarget();

        // Play the click sound (reuses Minecraft's UI click sound)
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f
                )
        );

        boolean wasShiftAction = false;

        switch (fn) {
            case KeyboardLayout.KeyFunction.StringFunc stringFn -> insertText(stringFn.string(), target);

            case KeyboardLayout.KeyFunction.CodeFunc codeFn ->
                codeFn.codes().forEach(code ->
                        target.acceptKeyCode(code.keycode(), code.scancode(), code.modifier()));

            case KeyboardLayout.KeyFunction.SpecialFunc specialFn -> {
                switch (specialFn.action()) {
                    case SHIFT -> {
                        toggleShift();
                        wasShiftAction = true;
                    }
                    case SHIFT_LOCK -> {
                        toggleShiftLock();
                        wasShiftAction = true;
                    }
                    case ENTER     -> target.acceptKeyCode(InputConstants.KEY_RETURN,    0, 0);
                    case BACKSPACE -> target.acceptKeyCode(InputConstants.KEY_BACKSPACE,  0, 0);
                    case TAB       -> target.acceptKeyCode(InputConstants.KEY_TAB,        0, 0);
                    case LEFT_ARROW  -> target.acceptKeyCode(InputConstants.KEY_LEFT,   0, 0);
                    case RIGHT_ARROW -> target.acceptKeyCode(InputConstants.KEY_RIGHT,  0, 0);
                    case UP_ARROW    -> target.acceptKeyCode(InputConstants.KEY_UP,     0, 0);
                    case DOWN_ARROW  -> target.acceptKeyCode(InputConstants.KEY_DOWN,   0, 0);
                    case PASTE     -> {
                        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                        insertText(clip, target);
                    }
                    case COPY_ALL  -> target.copy();
                    case PREVIOUS_LAYOUT -> keyboard.getPreviousLayoutId().ifPresent(this::changeLayout);
                }
            }

            case KeyboardLayout.KeyFunction.ChangeLayoutFunc layoutFn -> changeLayout(layoutFn.layout());
        }

        // Auto-release Shift after a character key is pressed (one-shot Shift)
        if (!wasShiftAction && keyboard.isShifted() && !keyboard.isShiftLocked()) {
            if (key.regular() != key.shifted()) {
                keyboard.setShifted(false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shift helpers
    // -------------------------------------------------------------------------

    private void toggleShift() {
        if (!keyboard.isShiftLocked()) {
            keyboard.setShifted(!keyboard.isShifted());
        } else {
            keyboard.setShifted(false);
            keyboard.setShiftLocked(false);
        }
    }

    private void toggleShiftLock() {
        boolean locked = !keyboard.isShiftLocked();
        keyboard.setShiftLocked(locked);
        keyboard.setShifted(locked);
    }

    private void changeLayout(net.minecraft.resources.Identifier layoutId) {
        KeyboardLayoutWithId layoutWithId = com.shadow.typehere.TypeHereClient.LAYOUT_MANAGER.getLayout(layoutId);
        keyboard.updateLayout(layoutWithId);
    }

    // -------------------------------------------------------------------------
    // Visual state
    // -------------------------------------------------------------------------

    /** Returns {@code true} when the key should render in its pressed/depressed state. */
    public boolean isVisuallyPressed() {
        return mousePressed
                || isShiftKeyAndShifting()
                || isShiftLockKeyAndShiftLocked();
    }

    private boolean isShiftKeyAndShifting() {
        return keyboard.isShifted() && !keyboard.isShiftLocked()
                && getKeyFunction() instanceof KeyboardLayout.KeyFunction.SpecialFunc(
                        KeyboardLayout.KeyFunction.SpecialFunc.Action action)
                && action == KeyboardLayout.KeyFunction.SpecialFunc.Action.SHIFT;
    }

    private boolean isShiftLockKeyAndShiftLocked() {
        return keyboard.isShiftLocked()
                && getKeyFunction() instanceof KeyboardLayout.KeyFunction.SpecialFunc(
                        KeyboardLayout.KeyFunction.SpecialFunc.Action action)
                && action == KeyboardLayout.KeyFunction.SpecialFunc.Action.SHIFT_LOCK;
    }

    // -------------------------------------------------------------------------
    // Capability checks
    // -------------------------------------------------------------------------

    private boolean supportsAction(boolean shifted) {
        InputTarget t = keyboard.getInputTarget();
        return switch (key.getFunction(shifted)) {
            case KeyboardLayout.KeyFunction.StringFunc ignored        -> t.supportsCharInput();
            case KeyboardLayout.KeyFunction.CodeFunc  ignored        -> t.supportsKeyCodeInput();
            case KeyboardLayout.KeyFunction.SpecialFunc specialFunc  -> switch (specialFunc.action()) {
                case ENTER, BACKSPACE, LEFT_ARROW, RIGHT_ARROW,
                     UP_ARROW, DOWN_ARROW, TAB              -> t.supportsKeyCodeInput();
                case PASTE                                   -> t.supportsCharInput();
                case COPY_ALL                                -> t.supportsCopying();
                case SHIFT, SHIFT_LOCK, PREVIOUS_LAYOUT      -> true;
            };
            case KeyboardLayout.KeyFunction.ChangeLayoutFunc ignored -> true;
        };
    }

    private boolean supportsCurrentAction() {
        return keyboard.isShifted() ? supportsShifted : supportsRegular;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public KeyboardLayout.Key getKey() { return key; }

    public KeyboardLayout.KeyFunction getKeyFunction() {
        return key.getFunction(keyboard.isShifted());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Inserts each Unicode code-point in {@code text} as a character event,
     * correctly handling surrogate pairs (e.g. emoji).
     */
    private static void insertText(String text, InputTarget target) {
        text.codePoints().forEach(cp -> {
            int mod = Character.isUpperCase(cp) ? GLFW.GLFW_MOD_SHIFT : 0;
            if (Character.isBmpCodePoint(cp)) {
                target.acceptChar((char) cp, mod);
            } else if (Character.isValidCodePoint(cp)) {
                target.acceptChar(Character.highSurrogate(cp), mod);
                target.acceptChar(Character.lowSurrogate(cp), mod);
            }
        });
    }

    /** Builds the display label for a key in the given shift state. */
    private static Component createLabel(KeyboardLayout.Key key, boolean shift) {
        return key.getFunction(shift).displayName();
    }

    /**
     * Applies a render scale transform around the key's position so the content
     * is rendered at {@link #renderScale} × size.
     */
    private void doScaledExtraction(GuiGraphicsExtractor graphics, Runnable runnable) {
        var pose = graphics.pose().pushMatrix();
        pose.translate(getX(), getY());
        pose.scale(renderScale, renderScale);
        pose.translate(-getX(), -getY());
        runnable.run();
        pose.popMatrix();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        // Narration: describe the key label for accessibility
        out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                keyboard.isShifted() ? shiftedLabel : regularLabel);
    }
}
