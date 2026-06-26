package de.osemine.flutstuff_bridge.client;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.crafting.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RecipeInjector {
    private static IJeiRuntime jeiRuntime = null;

    public static void setJeiRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    public static void injectRecipes(List<RecipeHolder<?>> recipes) {
        if (jeiRuntime == null) return;

        IRecipeManager recipeManager = jeiRuntime.getRecipeManager();

        Map<IRecipeHolderType<?>, List<RecipeHolder<?>>> grouped = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : recipes) {
            IRecipeHolderType<?> recipeType = getJeiRecipeType(holder.value().getType());
            if (recipeType != null) {
                grouped.computeIfAbsent(recipeType, k -> new ArrayList<>()).add(holder);
            }
        }

        for (Map.Entry<IRecipeHolderType<?>, List<RecipeHolder<?>>> entry : grouped.entrySet()) {
            addRecipesUnchecked(recipeManager, entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addRecipesUnchecked(IRecipeManager manager, IRecipeHolderType<?> type, List<RecipeHolder<?>> recipes) {
        IRecipeHolderType rawType = type;
        List rawList = recipes;
        manager.addRecipes(rawType, rawList);
    }

    @Nullable
    private static IRecipeHolderType<?> getJeiRecipeType(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) return RecipeTypes.CRAFTING;
        if (type == RecipeType.SMELTING) return RecipeTypes.SMELTING;
        if (type == RecipeType.BLASTING) return RecipeTypes.BLASTING;
        if (type == RecipeType.SMOKING) return RecipeTypes.SMOKING;
        if (type == RecipeType.CAMPFIRE_COOKING) return RecipeTypes.CAMPFIRE_COOKING;
        if (type == RecipeType.STONECUTTING) return RecipeTypes.STONECUTTING;
        if (type == RecipeType.SMITHING) return RecipeTypes.SMITHING;
        return null;
    }
}
