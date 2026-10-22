package com.crazerium.waymark.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Stands in for an item whose mod is not installed. It carries the item's original JSON, so the quest file is
 * written back unchanged and nothing is lost when quests are edited without every mod present.
 */
public class ItemMissing extends Item {

    private static final String KEY = "waymark_original";

    public static ItemMissing INSTANCE;

    public ItemMissing() {
        setUnlocalizedName("waymark.missing");
        setTextureName("waymark:missing");
        setMaxStackSize(64);
    }

    public static ItemStack create(JsonObject original) {
        ItemStack stack = new ItemStack(INSTANCE);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KEY, original.toString());
        stack.setTagCompound(tag);
        return stack;
    }

    /** The JSON the placeholder was created from, or null if this is not a placeholder. */
    public static JsonObject original(ItemStack stack) {
        if (INSTANCE == null || stack.getItem() != INSTANCE || !stack.hasTagCompound()) return null;
        String json = stack.getTagCompound()
            .getString(KEY);
        if (json.isEmpty()) return null;
        return new JsonParser().parse(json)
            .getAsJsonObject();
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        JsonObject original = original(stack);
        String id = original != null && original.has("id") ? original.get("id")
            .getAsString() : "?";
        return "§cMissing: " + id;
    }
}
