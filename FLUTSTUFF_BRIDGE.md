# FlutStuff Recipe Sync Bridge — Fabric Mod Implementation

## Overview

The FlutStuff Paper plugin sends all server recipes (vanilla + datapack) to connected clients using Minecraft's custom payload system. Your Fabric mod receives these recipes and injects them into JEI/REI.

### How it works

```
Paper Server (FlutStuff)                    Fabric Client (Your Mod)
       │                                           │
       │  PlayerJoinEvent fires                    │
       │                                           │
       │  ── ClientboundCustomPayloadPacket ──────>│
       │     channel: "flutstuff:recipe_sync"      │  Register receiver
       │     data: [binary recipe payload]         │  Deserialize recipes
       │                                           │  Feed to JEI/REI
       │                                           ▼
       │                                     JEI shows all recipes
```

### Payload format (binary)

All multi-byte values use **big-endian** byte order (Minecraft's default via `RegistryFriendlyByteBuf` / `FriendlyByteBuf`).

```
VarInt     recipeCount                    // number of recipes
for each recipe:
  Identifier    recipeId                  // e.g. "minecraft:stick"
  Identifier    serializerId              // e.g. "minecraft:crafting_shaped"
  Identifier    typeId                    // e.g. "minecraft:crafting"
  [serialized recipe data]                // encoded with the serializer's StreamCodec
```

- `Identifier` is written as: `VarInt length` + `UTF-8 bytes` of the string (e.g. `"minecraft:stick"`).
- `VarInt` uses Minecraft's variable-length integer encoding (7 bits per byte, continuation bit).
- The recipe data is serialized using `RegistryFriendlyByteBuf` with the server's `RegistryAccess`.

---

## Fabric Mod Implementation

### 1. Project Setup

Create a new Fabric mod project (Fabric Loader 0.16+, Fabric API 0.100+). Your `build.gradle` needs:

```groovy
repositories {
    maven {
        name = "ParchmentMC"
        url = "https://maven.parchmentmc.org"
    }
}

dependencies {
    modImplementation "net.fabricmc:fabric-loader:${loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_api_version}"
}
```

Use **Mojang mappings** (via Parchment or Mojang official). The plugin uses Mojang names for all NMS classes.

### 2. Define the Payload

```java
package com.example.flutstuffbridge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

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
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

### 3. Register the Receiver and Process Recipes

Create a client initializer that registers the payload and processes incoming recipes:

```java
package com.example.flutstuffbridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.core.RegistryAccess;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.recipe.v1.RecipeSyncHandler;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class FlutstuffBridgeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerReceiver(
                FlutstuffRecipesPayload.TYPE,
                FlutstuffRecipesPayload.CODEC,
                (payload, context) -> {
                    context.client().execute(() -> {
                        processRecipes(payload.data());
                    });
                }
        );
    }

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
                Identifier recipeId = buf.readIdentifier();
                Identifier serializerId = buf.readIdentifier();
                Identifier typeId = buf.readIdentifier();

                // Look up serializer
                RecipeSerializer<?> serializer = BuiltInRegistries.RECIPE_SERIALIZER.get(serializerId);
                if (serializer == null) {
                    // Unknown serializer — skip
                    continue;
                }

                // Deserialize recipe using its StreamCodec
                @SuppressWarnings("unchecked")
                StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> codec =
                        (StreamCodec<RegistryFriendlyByteBuf, Recipe<?>>) serializer.streamCodec();
                Recipe<?> recipe = codec.decode(buf);

                // Create a ResourceKey for the recipe
                ResourceKey<Recipe<?>> key = ResourceKey.create(
                        net.minecraft.core.registries.Registries.RECIPE, recipeId
                );

                recipes.add(new RecipeHolder<>(key, recipe));
            } catch (Exception e) {
                // Log and continue with next recipe
                FlutstuffBridge.LOGGER.error("Failed to deserialize recipe", e);
            }
        }

        // Inject recipes into JEI/REI
        RecipeInjector.injectRecipes(recipes);
    }
}
```

### 4. Inject Recipes into JEI

#### Option A: Via JEI API (JEI 19.x)

```java
package com.example.flutstuffbridge;

import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.List;

public class RecipeInjector {
    private static IJeiRuntime jeiRuntime = null;

    public static void setJeiRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    public static void injectRecipes(List<RecipeHolder<?>> recipes) {
        if (jeiRuntime == null) return;

        // JEI 19.x API — adjust method names as needed
        var recipeManager = jeiRuntime.getRecipeManager();
        for (RecipeHolder<?> holder : recipes) {
            recipeManager.addRecipe(holder, true); // true = notify listeners
        }
    }
}
```

JEI plugin entry point to capture `IJeiRuntime`:

```java
@JeiPlugin
public class FlutstuffJeiPlugin implements JeiPlugin {
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RecipeInjector.setJeiRuntime(runtime);
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("flutstuff_bridge", "jei_plugin");
    }
}
```

#### Option B: Via Fabric Recipe Manager (alternative if JEI API is unavailable)

Add recipes directly to Minecraft's client-side recipe manager. JEI reads from this and updates automatically:

```java
// Inside processRecipes(), after deserialization:
RecipeManager clientRecipeManager = mc.getConnection().getRecipeManager();
// Use OBF/Reflection to access the internal recipe map.
// The field is likely "recipes" (Map<ResourceKey, RecipeHolder<?>>).
// This requires access wideners or reflection.
```

### 5. Register in `fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "flutstuff_bridge",
  "version": "1.0.0",
  "name": "FlutStuff Bridge",
  "description": "Receives server recipes from FlutStuff Paper plugin and injects them into JEI/REI",
  "authors": ["your-name"],
  "contact": {},
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": ["com.example.flutstuffbridge.FlutstuffBridgeClient"],
    "jei": ["com.example.flutstuffbridge.FlutstuffJeiPlugin"]
  },
  "depends": {
    "fabricloader": ">=0.16.0",
    "fabric": "*",
    "minecraft": ">=26.1",
    "jei": ">=19.0"
  }
}
```

---

## Testing

1. **Start Paper server** with FlutStuff plugin
2. **Connect with Fabric client** that has your bridge mod + JEI
3. Check logs for `FlutStuffBridge` messages
4. Open JEI — all server recipes (vanilla + datapack) should be visible

If recipes don't appear:
- Check server logs for errors in `RecipeSyncSender`
- Check client logs for errors in the bridge mod
- Verify the payload channel name matches on both sides
- Ensure JEI is loaded on the client

## Binary wire format reference

| Field | Type | Description |
|-------|------|-------------|
| `recipeCount` | VarInt | Number of recipes |
| `recipeId` | Identifier (VarInt length + UTF-8) | e.g. `minecraft:stick` |
| `serializerId` | Identifier | e.g. `minecraft:crafting_shaped` |
| `typeId` | Identifier | e.g. `minecraft:crafting` |
| `recipeData` | bytes | Serialized via `serializer.streamCodec()` |

VarInt encoding: 7 bits per byte, MSB = continuation flag.
Identifier encoding: VarInt byte length, then UTF-8 string bytes.

---

## Troubleshooting

### "Class not found: net.minecraft.resources.Identifier"
Paper 26.1 renamed `ResourceLocation` to `Identifier`. Use **Mojang mappings** (not Yarn).

### "StreamCodec mismatch"
Both sides must use identical registry lookups for codec resolution. The codec is looked up by `serializerId` from `BuiltInRegistries.RECIPE_SERIALIZER`.

### PlayerJoinEvent fires before client is ready
In rare cases, `player.connection.send()` might fail during login. The plugin sends on `PlayerJoinEvent` which fires after the player is fully connected.
