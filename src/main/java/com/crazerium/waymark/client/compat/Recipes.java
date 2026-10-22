package com.crazerium.waymark.client.compat;

import net.minecraft.item.ItemStack;

import com.crazerium.waymark.Waymark;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shows an item's recipes or uses in a recipe viewer, if one is installed (NotEnoughItems). */
@SideOnly(Side.CLIENT)
public final class Recipes {

    private static Boolean nei;

    private Recipes() {}

    public static boolean available() {
        if (nei == null) nei = Loader.isModLoaded("NotEnoughItems");
        return nei;
    }

    /** Opens the recipes (or with {@code usage}, the uses) of the stack; returns false if nothing was shown. */
    public static boolean show(ItemStack stack, boolean usage) {
        if (stack == null || !available()) return false;
        try {
            return NeiRecipes.show(stack, usage);
        } catch (LinkageError | RuntimeException e) {
            Waymark.LOG.error("Could not open NEI recipes", e);
            nei = false;
            return false;
        }
    }
}
