package com.crazerium.waymark.quest.task;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.EditorField;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.TaskType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/**
 * Be in a dimension, optionally within {@code range} blocks of a point and/or in a biome.
 * A negative range means anywhere in the dimension, a negative biome means any biome.
 */
public class LocationTask extends Task {

    private int dimension;
    private int x, y, z;
    private int range = -1;
    private int biome = -1;

    public LocationTask(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public TaskType<?> getType() {
        return BuiltinTypes.LOCATION_TASK;
    }

    @Override
    public List<EditorField> editorFields() {
        return Arrays.asList(
            EditorField.integer("dimension", 0),
            EditorField.integer("x", 0),
            EditorField.integer("y", 0),
            EditorField.integer("z", 0),
            EditorField.integer("range", -1),
            EditorField.integer("biome", -1));
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public void setPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public void setBiome(int biome) {
        this.biome = biome;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.compass);
    }

    @Override
    public String getTitle() {
        if (range < 0) return StatCollector.translateToLocalFormatted("waymark.task.location.dimension", dimension);
        return StatCollector.translateToLocalFormatted("waymark.task.location.point", x, y, z, dimension);
    }

    @Override
    public boolean isAutoDetected() {
        return true;
    }

    @Override
    public long detect(EntityPlayerMP player, long progress) {
        if (player.dimension != dimension) return progress;
        if (range >= 0) {
            double dx = player.posX - x, dy = player.posY - y, dz = player.posZ - z;
            if (dx * dx + dy * dy + dz * dz > (double) range * range) return progress;
        }
        if (biome >= 0) {
            int bx = MathHelper.floor_double(player.posX), bz = MathHelper.floor_double(player.posZ);
            if (player.worldObj.getBiomeGenForCoords(bx, bz).biomeID != biome) return progress;
        }
        return 1;
    }

    @Override
    public void writeJson(JsonObject json) {
        json.addProperty("dimension", dimension);
        if (range >= 0) {
            json.addProperty("x", x);
            json.addProperty("y", y);
            json.addProperty("z", z);
            json.addProperty("range", range);
        }
        if (biome >= 0) json.addProperty("biome", biome);
    }

    @Override
    public void readJson(JsonObject json) {
        dimension = JsonUtil.getInt(json, "dimension", 0);
        x = JsonUtil.getInt(json, "x", 0);
        y = JsonUtil.getInt(json, "y", 0);
        z = JsonUtil.getInt(json, "z", 0);
        range = JsonUtil.getInt(json, "range", -1);
        biome = JsonUtil.getInt(json, "biome", -1);
    }
}
