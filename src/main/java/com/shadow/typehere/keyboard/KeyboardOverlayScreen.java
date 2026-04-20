package com.shadow.typehere.keyboard;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

/**
 * A transparent overlay screen that hosts a {@link KeyboardWidget} on top of
 * another screen (typically ChatScreen).
 *
 * <p>The overlay renders the underlying {@link #backgroundScreen} first, then
 * draws the keyboard on the next rendering stratum so they are visually separate.
 * When the keyboard is dismissed (Enter, Escape, or a click on the background screen),
 * the background screen is restored directly to {@code Minecraft.screen} to avoid
 * calling {@link Screen#removed()} or {@link Screen#init} unnecessarily.
 *
 * <p>Adapted from Controlify's {@code KeyboardOverlayScreen} — Controlify-specific
 * virtual-mouse and controller callbacks have been removed.
 */
public class KeyboardOverlayScreen extends Screen {

    private final Screen backgroundScreen;
    private final KeyboardLayoutWithId initialLayout;
    private final InputTarget inputTarget;
    private final KeyboardPositioner positioner;

    /** The live keyboard widget — recreated on each {@link #init()}. */
    private KeyboardWidget keyboardWidget;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param backgroundScreen the screen rendered behind the keyboard
     * @param initialLayout    which keyboard layout to show when first opened
     * @param inputTarget      the target that will receive typed characters / key codes
     * @param positioner       determines where on-screen the keyboard is placed
     */
    public KeyboardOverlayScreen(
            Screen backgroundScreen,
            KeyboardLayoutWithId initialLayout,
            InputTarget inputTarget,
            KeyboardPositioner positioner
    ) {
        super(backgroundScreen.getTitle());
        this.backgroundScreen = backgroundScreen;
        this.initialLayout    = initialLayout;
        this.inputTarget      = new CloseInterceptingInputTarget(inputTarget);
        this.positioner       = positioner;
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        // Preserve the layout that was active before an init() call (e.g. on resize)
        KeyboardLayoutWithId layout = (keyboardWidget != null)
                ? com.shadow.typehere.TypeHereClient.LAYOUT_MANAGER
                        .getLayout(keyboardWidget.getCurrentLayoutId())
                : initialLayout;

        ScreenRectangle rect = positioner.positionKeyboard(this.width, this.height);

        this.keyboardWidget = this.addRenderableWidget(new KeyboardWidget(
                rect.left(), rect.top(), rect.width(), rect.height(),
                layout, this.inputTarget
        ));
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        // Re-initialise the background screen so its widgets stay in the right place.
        // MC 26.1: Screen.init(int, int) takes only width and height (no Minecraft arg).
        this.backgroundScreen.init(this.width, this.height);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Render the underlying screen first…
        this.backgroundScreen.extractRenderState(graphics, mouseX, mouseY, a);
        // …then the keyboard on the next stratum (rendered on top, unclipped by the background)
        graphics.nextStratum();
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.backgroundScreen.extractBackground(graphics, mouseX, mouseY, a);
    }

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
                || backgroundScreen.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        // A click on the background screen closes the keyboard and passes the click through
        if (backgroundScreen.mouseClicked(event, doubleClick)) {
            onClose();
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        backgroundScreen.tick();
        super.tick();
    }

    // -------------------------------------------------------------------------
    // Close
    // -------------------------------------------------------------------------

    @Override
    public void onClose() {
        // Restore the background screen without reinitialising it
        this.minecraft.screen = backgroundScreen;
    }

    /** Returns the screen behind the keyboard overlay. */
    public Screen getParentScreen() {
        return backgroundScreen;
    }

    // -------------------------------------------------------------------------
    // CloseInterceptingInputTarget
    // -------------------------------------------------------------------------

    /**
     * Wraps the real {@link InputTarget} and intercepts Enter / Escape key codes
     * to close the overlay automatically when the user submits or cancels input.
     */
    private class CloseInterceptingInputTarget extends InputTarget.Delegated {

        CloseInterceptingInputTarget(InputTarget delegate) {
            super(delegate);
        }

        @Override
        public boolean acceptKeyCode(int keycode, int scancode, int modifiers) {
            if (keycode == InputConstants.KEY_RETURN || keycode == InputConstants.KEY_ESCAPE) {
                KeyboardOverlayScreen.this.onClose();
                return true;
            }
            return super.acceptKeyCode(keycode, scancode, modifiers);
        }
    }

    // -------------------------------------------------------------------------
    // KeyboardPositioner
    // -------------------------------------------------------------------------

    /**
     * Functional interface that computes a {@link ScreenRectangle} for the keyboard
     * area given the current screen dimensions.
     */
    @FunctionalInterface
    public interface KeyboardPositioner {
        ScreenRectangle positionKeyboard(int screenWidth, int screenHeight);
    }

    /**
     * Creates a positioner that places the keyboard above or below the widget
     * returned by {@code widgetRectSupplier}, choosing the side with more space.
     * The keyboard is horizontally centred over the widget.
     *
     * @param desiredWidth   preferred pixel width of the keyboard
     * @param desiredHeight  preferred pixel height of the keyboard
     * @param padding        minimum pixel gap between the widget and the keyboard
     * @param widgetRectSupplier supplies the widget's current rectangle (re-evaluated each call)
     * @return a {@link KeyboardPositioner} implementing the above/below logic
     */
    public static KeyboardPositioner aboveOrBelowWidgetPositioner(
            int desiredWidth, int desiredHeight, int padding,
            Supplier<ScreenRectangle> widgetRectSupplier
    ) {
        return (screenWidth, screenHeight) -> {
            ScreenRectangle widget = widgetRectSupplier.get();

            int kw = Math.min(desiredWidth, screenWidth);
            int kx = Math.clamp(
                    widget.getCenterInAxis(ScreenAxis.HORIZONTAL) - kw / 2,
                    0, screenWidth - kw
            );

            int spaceBelow = screenHeight - widget.bottom() - padding;
            int spaceAbove = widget.top() - padding;

            boolean above;
            int kh;
            if (spaceBelow >= desiredHeight) {
                kh = desiredHeight; above = false;
            } else if (spaceAbove >= desiredHeight) {
                kh = desiredHeight; above = true;
            } else if (spaceBelow >= spaceAbove) {
                kh = spaceBelow; above = false;
            } else {
                kh = spaceAbove; above = true;
            }

            int ky = above ? widget.top() - kh - padding : widget.bottom() + padding;
            return new ScreenRectangle(kx, ky, kw, kh);
        };
    }
}
