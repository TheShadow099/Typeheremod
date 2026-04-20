package com.shadow.typehere;

import com.shadow.typehere.keyboard.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side entry point for the TypeHere on-screen keyboard mod.
 *
 * <p>This class:
 * <ol>
 *   <li>Creates the {@link KeyboardLayoutManager} and registers it as a Fabric
 *       client-resource reload listener (called once on startup and on every
 *       /reload or resource-pack change).</li>
 *   <li>Registers the keyboard toggle keybinding (default: {@code K}).</li>
 *   <li>Polls the keybinding each game tick and opens / closes the OSK overlay.</li>
 * </ol>
 *
 * <p>Declared as the {@code "client"} entrypoint in {@code fabric.mod.json}.
 */
public class TypeHereClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(TypeHere.MOD_ID);

    // -------------------------------------------------------------------------
    // Shared singletons
    // -------------------------------------------------------------------------

    /**
     * The singleton keyboard layout manager.
     *
     * <p>Populated on startup and refreshed on every resource-pack reload.
     * All keyboard classes access it via this static field rather than
     * through a service locator, keeping the API simple and dependency-free.
     */
    public static final KeyboardLayoutManager LAYOUT_MANAGER = new KeyboardLayoutManager();

    // -------------------------------------------------------------------------
    // Keybinding
    // -------------------------------------------------------------------------

    /**
     * Toggle the floating on-screen keyboard overlay.
     * Default key: {@code K}. Rebindable in <em>Options → Controls → TypeHere</em>.
     */
    public static KeyMapping TOGGLE_OSK_KEY;

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    @Override
    public void onInitializeClient() {
        // 1 — Register layout manager (reloads every time resource packs change)
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(LAYOUT_MANAGER);

        // 2 — Register the toggle keybinding
        TOGGLE_OSK_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "typehere.keybind.toggle_osk",   // translation key
                InputUtil.Type.KEYSYM,            // key type
                GLFW.GLFW_KEY_K,                  // default: K
                "typehere.keybind.category"        // controls category header
        ));

        // 3 — Poll the keybinding each game tick
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        LOGGER.info("TypeHere OSK client initialised.");
    }

    // -------------------------------------------------------------------------
    // Tick handler
    // -------------------------------------------------------------------------

    /**
     * Called at the end of every client game tick.
     *
     * <p>While the toggle key has registered presses (can be > 1 if ticks were skipped):
     * <ul>
     *   <li>If the OSK overlay is currently open → close it (restore parent screen).</li>
     *   <li>If any other screen is open → open the OSK overlay on top of it.</li>
     *   <li>If no screen is open (in-game HUD visible) → do nothing.</li>
     * </ul>
     *
     * @param client the active Minecraft client
     */
    private void onClientTick(Minecraft client) {
        while (TOGGLE_OSK_KEY.consumeClick()) {
            if (client.screen instanceof KeyboardOverlayScreen osk) {
                // Already open — close it
                client.setScreen(osk.getParentScreen());
            } else if (client.screen != null) {
                // Open the overlay on the current screen with an empty input target.
                // Screens that want the OSK to write into a specific field (like ChatScreen)
                // handle that via their own mixin; the toggle key is for all other screens.
                int sw = client.getWindow().getGuiScaledWidth();
                int sh = client.getWindow().getGuiScaledHeight();
                int kh = (int) (sh * 0.45f);

                // The keyboard is anchored to the bottom of the screen.
                ScreenRectangle anchor = new ScreenRectangle(0, sh, sw, 0);

                client.setScreen(new KeyboardOverlayScreen(
                        client.screen,
                        KeyboardLayouts.full(),
                        InputTarget.EMPTY,
                        KeyboardOverlayScreen.aboveOrBelowWidgetPositioner(sw, kh, 2, () -> anchor)
                ));
            }
        }
    }
}
