package com.shadow.typehere.keyboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Loads all keyboard layouts from resource packs as a Fabric resource-reload listener.
 *
 * <p>Layout files must be placed at:
 * <pre>
 *   assets/&lt;namespace&gt;/keyboard_layout/&lt;layout-name&gt;/&lt;language-code&gt;.json
 * </pre>
 *
 * <p>For example:
 * <pre>
 *   assets/typehere/keyboard_layout/full/en_us.json
 *   assets/typehere/keyboard_layout/symbols/fr_fr.json
 * </pre>
 *
 * <p>The layout JSON format is defined by {@link KeyboardLayout#CODEC}.
 *
 * <p>Language selection falls back to {@code en_us} if no file exists for the player's
 * current game language.
 *
 * <p>Adapted from Controlify's {@code KeyboardLayoutManager}, using Fabric's
 * {@link IdentifiableResourceReloadListener} instead of Controlify's internal interface.
 */
public class KeyboardLayoutManager implements IdentifiableResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("TypeHere/LayoutManager");

    /** Resource directory prefix scanned for layout JSON files. */
    private static final String PREFIX = "keyboard_layout";

    /** Reload ID — must be unique across all resource reload listeners. */
    private static final Identifier RELOAD_ID = Identifier.of("typehere", "keyboard_layout");

    /** Default fallback language code. */
    private static final String DEFAULT_LANG = "en_us";

    /**
     * Loaded layouts, keyed by {@code (languageCode, layoutId)}.
     * Populated after each resource-pack reload.
     */
    private Map<LayoutKey, KeyboardLayout> layouts = Map.of();

    // -------------------------------------------------------------------------
    // IdentifiableResourceReloadListener
    // -------------------------------------------------------------------------

    @Override
    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    @Override
    public CompletableFuture<Void> reload(
            PreparationBarrier barrier,
            ResourceManager manager,
            Executor backgroundExecutor,
            Executor gameExecutor
    ) {
        return CompletableFuture
                // Phase 1 — background: scan and parse all layout files
                .supplyAsync(() -> loadAll(manager), backgroundExecutor)
                // Wait for the barrier (ensures other listeners finish prep too)
                .thenCompose(barrier::wait)
                // Phase 2 — main thread: apply loaded layouts
                .thenAcceptAsync(loaded -> {
                    this.layouts = loaded;
                    LOGGER.info("TypeHere OSK: loaded {} keyboard layout(s)", layouts.size());
                }, gameExecutor);
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Scans the resource manager for all matching JSON files and parses them.
     * Any files that fail to parse are logged and skipped.
     *
     * @param manager the resource manager to scan
     * @return map of {@link LayoutKey} → {@link KeyboardLayout}
     */
    private static Map<LayoutKey, KeyboardLayout> loadAll(ResourceManager manager) {
        Map<Identifier, Resource> files = manager.listResources(
                PREFIX, path -> path.getPath().endsWith(".json")
        );

        Map<LayoutKey, KeyboardLayout> result = new HashMap<>();

        for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
            Identifier file = entry.getKey();
            Resource resource = entry.getValue();

            try {
                LayoutKey key = fileToKey(file);
                KeyboardLayout layout = parseLayout(file, resource);
                result.put(key, layout);
                LOGGER.debug("TypeHere OSK: loaded layout {} (lang={})", key.layoutId(), key.languageCode());
            } catch (Exception e) {
                LOGGER.error("TypeHere OSK: failed to load layout from {}: {}", file, e.getMessage());
            }
        }

        return Map.copyOf(result);
    }

    /**
     * Parses a single layout JSON file.
     *
     * @param file     the resource identifier (used only for error messages)
     * @param resource the raw resource to read
     * @return the parsed {@link KeyboardLayout}
     * @throws RuntimeException if parsing fails
     */
    private static KeyboardLayout parseLayout(Identifier file, Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            return KeyboardLayout.CODEC
                    .parse(JsonOps.INSTANCE, json)
                    .getOrThrow(reason -> new RuntimeException("Codec error: " + reason));
        } catch (Exception e) {
            throw new RuntimeException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Layout lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the layout for the given ID and the current game language,
     * falling back to {@code en_us} and then to the hardcoded fallback layout
     * if no matching file is found.
     *
     * @param layoutId  the resource-pack identifier of the layout
     * @param langCode  the player's current language code (e.g. {@code "en_us"})
     * @return the best-matching layout with its ID
     */
    public KeyboardLayoutWithId getLayout(Identifier layoutId, String langCode) {
        LayoutKey key = new LayoutKey(langCode, layoutId);

        return Optional.ofNullable(layouts.get(key))
                // try the requested language
                .or(() -> Optional.ofNullable(layouts.get(key.withLanguage(DEFAULT_LANG))))
                // fall back to the hardcoded layout
                .map(layout -> new KeyboardLayoutWithId(layout, layoutId))
                .orElseGet(() -> {
                    LOGGER.warn("TypeHere OSK: layout '{}' not found, using fallback", layoutId);
                    return KeyboardLayouts.fallback();
                });
    }

    /**
     * Returns the layout for the given ID using the current game language.
     *
     * @param layoutId the resource-pack identifier of the layout
     * @return the best-matching layout with its ID
     */
    public KeyboardLayoutWithId getLayout(Identifier layoutId) {
        String currentLang = Minecraft.getInstance().getLanguageManager().getSelected();
        return getLayout(layoutId, currentLang);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Derives a {@link LayoutKey} from a resource file path.
     *
     * <p>Expected path format:
     * {@code keyboard_layout/<layout-name>/<language-code>.json}
     *
     * @param file the full resource identifier
     * @return the parsed key
     * @throws IllegalArgumentException if the path does not match the expected format
     */
    private static LayoutKey fileToKey(Identifier file) {
        // path = "keyboard_layout/<name>/<lang>.json"
        String[] parts = file.getPath().split("/");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Unexpected keyboard layout path: " + file.getPath()
                            + " — expected keyboard_layout/<name>/<lang>.json"
            );
        }
        String layoutPath = parts[1];
        String langWithExt = parts[2];
        String lang = langWithExt.substring(0, langWithExt.lastIndexOf('.'));
        Identifier layoutId = Identifier.of(file.getNamespace(), layoutPath);
        return new LayoutKey(lang, layoutId);
    }

    /** Composite key: (languageCode, layoutId). */
    private record LayoutKey(String languageCode, Identifier layoutId) {
        LayoutKey withLanguage(String lang) {
            return new LayoutKey(lang, layoutId);
        }
    }
}
