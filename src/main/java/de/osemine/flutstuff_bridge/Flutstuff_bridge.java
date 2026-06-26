package de.osemine.flutstuff_bridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Flutstuff_bridge implements ModInitializer {
    public static final String MOD_ID = "flutstuff_bridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(
                FlutstuffRecipesPayload.TYPE,
                FlutstuffRecipesPayload.CODEC
        );
        LOGGER.info("FlutStuff Bridge initialized");
    }
}
