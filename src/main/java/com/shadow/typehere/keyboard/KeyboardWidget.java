package com.shadow.typehere.keyboard;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.Predicate;

/**
 * Container widget that renders a full on-screen keyboard.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Lays out a grid of {@link KeyWidget} instances from a {@link KeyboardLayout}.</li>
 *   <li>Manages global state shared across keys: Shift, Caps-Lock, and the active layout.</li>
 *   <li>Maintains focus within its key grid for mouse-driven navigation.</li>
 *   <li>Renders a dark background + border behind the key grid.</li>
 * </ul>
 *
 * <p>All key rendering is done in two passes (backgrounds then foregrounds) so
 * that all sprites are uploaded before any text is drawn — minimising GPU
 * context switches.
 *
 * <p>Adapted from Controlify's {@code KeyboardWidget} with all controller-specific
 * code removed.
 */
public class KeyboardWidget extends AbstractWidget implements ContainerEventHandler {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Resource-pack ID of the currently active layout. */
    private Identifier currentLayout;
    /** Resource-pack ID of the previously active layout (for "go back" keys). */
    @Nullable private Identifier previousLayout;

    /** The target that receives typed characters and key-code events. */
    private InputTarget inputTarget;

    /** Flat list of all key widgets in the current layout. */
    private List<KeyWidget> keys = ImmutableList.of();

    /** Whether the Shift key is currently active (one-shot). */
    private boolean shifted;
    /** Whether Caps Lock is engaged (persistent Shift). */
    private boolean shiftLocked;

    /** The currently focused key (for keyboard / focus navigation). */
    @Nullable private KeyWidget focused;
    private boolean isDragging;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param x           pixel X of the keyboard's top-left corner
     * @param y           pixel Y
     * @param width       total pixel width of the keyboard area
     * @param height      total pixel height of the keyboard area
     * @param layout      the initial keyboard layout to display
     * @param inputTarget the target that will receive the keyboard's input events
     */
    public KeyboardWidget(int x, int y, int width, int height,
                          KeyboardLayoutWithId layout, InputTarget inputTarget) {
        super(x, y, width, height, Component.literal("On-Screen Keyboard"));
        this.inputTarget = inputTarget;
        this.updateLayout(layout, null, null);
    }

    // -------------------------------------------------------------------------
    // Layout management
    // -------------------------------------------------------------------------

    /**
     * Switches to a new layout, attempting to preserve focus on a key with
     * the same identifier as the previously-focused key.
     *
     * @param layout the new layout to switch to
     */
    public void updateLayout(KeyboardLayoutWithId layout) {
        Identifier oldId = getCurrentLayoutId();
        @Nullable String oldIdentifier = Optional.ofNullable(getFocused())
                .map(k -> k.getKey().identifier())
                .orElse(null);
        updateLayout(layout, oldIdentifier, oldId);
    }

    /**
     * Switches to a new layout with explicit focus-restore hints.
     *
     * @param layout              the new layout
     * @param identifierToFocus   key identifier to focus after switch, or {@code null}
     * @param oldLayoutChangerToFocus the layout-change key that was active in the old layout,
     *                            used to focus the "back" button in the new layout
     */
    public void updateLayout(KeyboardLayoutWithId layout,
                             @Nullable String identifierToFocus,
                             @Nullable Identifier oldLayoutChangerToFocus) {
        this.previousLayout = this.currentLayout;
        this.currentLayout  = layout.id();

        arrangeKeys(layout.layout());

        // Try to restore focus on a key with the same string identifier
        findKey(identifierToFocus == null,
                k -> Objects.equals(k.getKey().identifier(), identifierToFocus))
                .or(() ->
                        // Fallback: focus the "change layout" key that points back to the old layout
                        findKey(oldLayoutChangerToFocus == null, k -> {
                            boolean isOldLayout = k.getKeyFunction() instanceof KeyboardLayout.KeyFunction.ChangeLayoutFunc cl
                                    && cl.layout().equals(oldLayoutChangerToFocus);
                            boolean isPrevLayout = k.getKeyFunction() instanceof KeyboardLayout.KeyFunction.SpecialFunc sf
                                    && sf.action() == KeyboardLayout.KeyFunction.SpecialFunc.Action.PREVIOUS_LAYOUT;
                            return isOldLayout || isPrevLayout;
                        })
                )
                .ifPresent(this::setFocused);
    }

    /**
     * Builds the flat {@link #keys} list by iterating over every row and key
     * in the layout and computing pixel bounds.
     */
    private void arrangeKeys(KeyboardLayout layout) {
        int totalKeys = layout.keys().stream().mapToInt(List::size).sum();
        List<KeyWidget> newKeys = new ArrayList<>(totalKeys);

        float unitWidth  = (float) this.getWidth()  / layout.width();
        float keyHeight  = (float) this.getHeight() / layout.keys().size();

        // Compute a visual scale so tiny keyboards are still readable
        int renderScale = Mth.floor(Math.max(0, Math.min(unitWidth, keyHeight) / 60f)) + 1;

        float y = this.getY();
        for (List<KeyboardLayout.Key> row : layout.keys()) {
            float x = this.getX();
            for (KeyboardLayout.Key key : row) {
                float keyWidth = key.width() * unitWidth;
                newKeys.add(new KeyWidget(
                        (int) x, (int) y, (int) keyWidth, (int) keyHeight,
                        renderScale, key, this
                ));
                x += keyWidth;
            }
            y += keyHeight;
        }

        this.keys = Collections.unmodifiableList(newKeys);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Step 1 — call per-key base state update (hover, etc.)
        for (KeyWidget key : keys) {
            key.extractRenderState(graphics, mouseX, mouseY, a);
        }

        // Dark translucent keyboard background
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x80000000);
        // Light border around the whole keyboard
        graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFFAAAAAA);

        // Step 2 — render all key backgrounds in one batch
        for (KeyWidget key : keys) {
            key.extractKeyBackground(graphics, mouseX, mouseY, a);
        }

        // Step 3 — render all key foregrounds (text) in one batch
        for (KeyWidget key : keys) {
            key.extractKeyForeground(graphics, mouseX, mouseY, a);
        }
    }

    // -------------------------------------------------------------------------
    // Shift / Caps-Lock state
    // -------------------------------------------------------------------------

    public boolean isShifted()       { return shifted;     }
    public boolean isShiftLocked()   { return shiftLocked; }
    public void setShifted(boolean v)     { this.shifted     = v; }
    public void setShiftLocked(boolean v) { this.shiftLocked = v; }

    // -------------------------------------------------------------------------
    // Layout / input target accessors
    // -------------------------------------------------------------------------

    public InputTarget getInputTarget()          { return inputTarget; }
    public void setInputTarget(InputTarget t)    { this.inputTarget = t; }

    public Identifier getCurrentLayoutId()        { return currentLayout; }
    public Optional<Identifier> getPreviousLayoutId() { return Optional.ofNullable(previousLayout); }

    // -------------------------------------------------------------------------
    // ContainerEventHandler
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<KeyWidget> children() {
        return (List<KeyWidget>) keys;
    }

    @Override
    public boolean isDragging()             { return isDragging; }
    @Override
    public void setDragging(boolean drag)   { isDragging = drag; }

    @Override
    public @Nullable KeyWidget getFocused() { return focused; }

    @Override
    public void setFocused(@Nullable GuiEventListener el) {
        if (el != null) {
            if (!(el instanceof KeyWidget)) {
                throw new IllegalArgumentException("Only KeyWidget children are allowed in KeyboardWidget");
            }
            // Layout change may have removed the old key; guard against stale reference
            if (!keys.contains(el)) el = null;
        }
        if (focused != null) focused.setFocused(false);
        if (el     != null) el.setFocused(true);
        focused = (KeyWidget) el;
    }

    @Override
    @Nullable
    public ComponentPath nextFocusPath(@NonNull FocusNavigationEvent event) {
        return ContainerEventHandler.super.nextFocusPath(event);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        return ContainerEventHandler.super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        return ContainerEventHandler.super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        return ContainerEventHandler.super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean isFocused() { return ContainerEventHandler.super.isFocused(); }

    @Override
    public void setFocused(boolean focused) { ContainerEventHandler.super.setFocused(focused); }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                Component.translatable("typehere.osk.title"));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Optional<KeyWidget> findKey(boolean skipSearch, Predicate<KeyWidget> predicate) {
        if (skipSearch) return Optional.empty();
        return keys.stream().filter(predicate).findFirst();
    }
}
