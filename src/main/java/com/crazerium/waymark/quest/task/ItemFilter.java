package com.crazerium.waymark.quest.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Which items count for a task. Matches the main item or any alternative (damage 32767 matches any damage), or,
 * when {@code ore_dict} is set, any item with that ore dictionary name. With {@code match_nbt} the item must
 * contain all NBT of the required item; extra NBT on the player's item is fine.
 */
public class ItemFilter {

    private final List<ItemStack> items = new ArrayList<>();
    private String oreDict = "";
    private boolean matchNbt;

    /** The first item is the main one and is used as the icon. */
    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void setItems(List<ItemStack> list) {
        items.clear();
        for (ItemStack stack : list) {
            if (stack == null) continue;
            ItemStack copy = stack.copy();
            copy.stackSize = 1;
            items.add(copy);
        }
    }

    public String getOreDict() {
        return oreDict;
    }

    public void setOreDict(String oreDict) {
        this.oreDict = oreDict;
    }

    public boolean isMatchNbt() {
        return matchNbt;
    }

    public void setMatchNbt(boolean matchNbt) {
        this.matchNbt = matchNbt;
    }

    public ItemStack getIcon() {
        if (!items.isEmpty()) return items.get(0);
        if (!oreDict.isEmpty()) {
            for (ItemStack ore : OreDictionary.getOres(oreDict)) return ore;
        }
        return null;
    }

    public String getName() {
        if (!oreDict.isEmpty()) return StatCollector.translateToLocalFormatted("waymark.task.item.ore", oreDict);
        if (items.isEmpty()) return "?";
        String name = items.get(0)
            .getDisplayName();
        return items.size() > 1 ? StatCollector.translateToLocalFormatted("waymark.task.item.or_more", name) : name;
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (!oreDict.isEmpty()) {
            int wanted = OreDictionary.getOreID(oreDict);
            for (int id : OreDictionary.getOreIDs(stack)) {
                if (id == wanted) return true;
            }
            return false;
        }
        for (ItemStack item : items) {
            if (item.getItem() != stack.getItem()) continue;
            if (item.getItemDamage() != OreDictionary.WILDCARD_VALUE && item.getItemDamage() != stack.getItemDamage())
                continue;
            if (!matchNbt || containsNbt(stack.getTagCompound(), item.getTagCompound())) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean containsNbt(NBTTagCompound actual, NBTTagCompound required) {
        if (required == null) return true;
        if (actual == null) return required.hasNoTags();
        for (String key : (Set<String>) required.func_150296_c()) {
            NBTBase want = required.getTag(key);
            NBTBase have = actual.getTag(key);
            if (have == null) return false;
            if (want instanceof NBTTagCompound && have instanceof NBTTagCompound) {
                if (!containsNbt((NBTTagCompound) have, (NBTTagCompound) want)) return false;
            } else if (!want.equals(have)) {
                return false;
            }
        }
        return true;
    }

    public void writeJson(JsonObject json) {
        if (!items.isEmpty()) json.add("item", JsonUtil.writeStack(items.get(0)));
        if (items.size() > 1) {
            JsonArray alternatives = new JsonArray();
            for (int i = 1; i < items.size(); i++) alternatives.add(JsonUtil.writeStack(items.get(i)));
            json.add("alternatives", alternatives);
        }
        if (!oreDict.isEmpty()) json.addProperty("ore_dict", oreDict);
        if (matchNbt) json.addProperty("match_nbt", true);
    }

    public void readJson(JsonObject json) {
        List<ItemStack> list = new ArrayList<>();
        list.add(JsonUtil.readStack(JsonUtil.getObject(json, "item")));
        for (JsonElement e : JsonUtil.getArray(json, "alternatives")) {
            if (e.isJsonObject()) list.add(JsonUtil.readStack(e.getAsJsonObject()));
        }
        setItems(list);
        oreDict = JsonUtil.getString(json, "ore_dict", "");
        matchNbt = JsonUtil.getBoolean(json, "match_nbt", false);
    }
}
