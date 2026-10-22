package com.crazerium.waymark.client.compat;

import net.minecraft.item.ItemStack;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;

/** Calls into NotEnoughItems. Only loaded when NEI is installed, so nothing else may reference NEI classes. */
final class NeiRecipes {

    private NeiRecipes() {}

    static boolean show(ItemStack stack, boolean usage) {
        return usage ? GuiUsageRecipe.openRecipeGui("item", stack.copy())
            : GuiCraftingRecipe.openRecipeGui("item", stack.copy());
    }
}
