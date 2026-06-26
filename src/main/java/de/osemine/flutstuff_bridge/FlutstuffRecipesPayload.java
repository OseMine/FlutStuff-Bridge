package de.osemine.flutstuff_bridge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FlutstuffRecipesPayload(byte[] data) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("flutstuff", "recipe_sync");
    public static final CustomPacketPayload.Type<FlutstuffRecipesPayload> TYPE =
            new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, FlutstuffRecipesPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBytes(payload.data),
                    (buf) -> {
                        byte[] bytes = new byte[buf.readableBytes()];
                        buf.readBytes(bytes);
                        return new FlutstuffRecipesPayload(bytes);
                    }
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
