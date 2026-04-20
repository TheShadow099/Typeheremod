package com.shadow.typehere;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server/common-side entry point for the TypeHere OSK mod.
 *
 * <p>This mod is client-only. This class exists only to satisfy the Fabric
 * common entrypoint contract. All real initialisation happens in
 * {@link TypeHereClient}.
 */
public class TypeHere implements ModInitializer {

    /** Mod identifier — must match the "id" field in fabric.mod.json. */
    public static final String MOD_ID = "typehere";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Nothing to do on the common (server) side.
        // All OSK logic is client-only and lives in TypeHereClient.
        LOGGER.info("TypeHere OSK mod loaded.");
    }
}
