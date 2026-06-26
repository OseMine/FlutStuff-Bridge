package de.osemine.flutstuff_bridge.client;

import de.osemine.flutstuff_bridge.FlutstuffRecipesPayload;
import de.osemine.flutstuff_bridge.Flutstuff_bridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

public class Flutstuff_bridgeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerReceiver(
                FlutstuffRecipesPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        processRecipes(payload.data());
                    });
                }
        );
    }

    @SuppressWarnings("unchecked")
    private void processRecipes(byte[] rawData) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RegistryAccess registryAccess = mc.level.registryAccess();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(rawData), registryAccess
        );

        int count = buf.readVarInt();
        List<RecipeHolder<?>> recipes = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            try {
                Identifier recipeId = Identifier.STREAM_CODEC.decode(buf);
                Identifier serializerId = Identifier.STREAM_CODEC.decode(buf);
                Identifier typeId = Identifier.STREAM_CODEC.decode(buf); // unused, but consumed

                var encoderRef = BuiltInRegistries.RECIPE_SERIALIZER.get(serializerId);
                if (encoderRef.isEmpty()) {
                    continue;
                }
                RecipeSerializer<?> serializer = encoderRef.get().value();

                StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> codec =
                        (StreamCodec<RegistryFriendlyByteBuf, Recipe<?>>) serializer.streamCodec();
                Recipe<?> recipe = codec.decode(buf);

                ResourceKey<Recipe<?>> key = ResourceKey.create(
                        Registries.RECIPE, recipeId
                );

                recipes.add(new RecipeHolder<>(key, recipe));
            } catch (Exception e) {
                Flutstuff_bridge.LOGGER.error("Failed to deserialize recipe", e);
            }
        }

        RecipeInjector.injectRecipes(recipes);
    }
}
