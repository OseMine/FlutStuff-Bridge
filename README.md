# FlutStuff Bridge

A Fabric client mod that receives server-side recipes from the **FlutStuff Paper plugin** via custom payload and injects them into **JEI** in real time.

## How it works

```
Paper Server (FlutStuff)                 Fabric Client (this mod)
       │                                          │
       │  PlayerJoinEvent fires                   │
       │                                          │
       │  ── CustomPayload("flutstuff:recipe_sync") ──>│
       │     [binary: VarInt count + recipes]      │  Deserialize recipes
       │                                          │  Inject into JEI
       │                                          ▼
       │                                    JEI shows all recipes
```

1. The Paper server sends all server recipes (vanilla + datapack) on player join
2. The mod receives them on channel `flutstuff:recipe_sync`
3. Recipes are deserialized using the serializer's `StreamCodec`
4. Injected into JEI via `IRecipeManager.addRecipes()`

## Build

```bash
./gradlew build
```

Requires **Java 25** and a Fabric development environment.

## Usage

- Install the mod on your Fabric client alongside **Fabric API** and **JEI**
- Install the **FlutStuff** plugin on a Paper server
- Join the server — all recipes (vanilla + datapack) appear in JEI automatically

## Dependencies

| Dependency   | Version      |
|-------------|-------------|
| Fabric Loader | >=0.19.3    |
| Fabric API    | >=0.153.0   |
| JEI           | >=29.6      |
| Minecraft     | 26.1.2      |

## Binary wire format

| Field        | Type                        | Description                     |
|-------------|-----------------------------|---------------------------------|
| recipeCount  | VarInt                      | Number of recipes               |
| recipeId     | Identifier (VarInt + UTF-8) | e.g. `minecraft:stick`         |
| serializerId | Identifier                  | e.g. `minecraft:crafting_shaped`|
| typeId       | Identifier                  | Recipe type (consumed, unused)  |
| recipeData   | bytes                       | Serialized via streamCodec      |
