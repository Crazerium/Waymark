package com.crazerium.waymark.quest.task;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.EditorField;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.TaskType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/**
 * Requires an amount of a fluid in mB, carried in any fluid container in the inventory: buckets and other registered
 * containers, or items that hold fluid themselves (cells and tanks). With {@code consume} the fluid is drained on
 * submit and empty containers are given back; without it, having the fluid is enough.
 */
public class FluidTask extends Task {

    private String fluid = "water";
    private long amount = FluidContainerRegistry.BUCKET_VOLUME;
    private boolean consume = true;

    public FluidTask(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public TaskType<?> getType() {
        return BuiltinTypes.FLUID_TASK;
    }

    @Override
    public List<EditorField> editorFields() {
        return Arrays.asList(
            EditorField.fluid("fluid", "water"),
            EditorField.integer("amount", 1000),
            EditorField.bool("consume", true));
    }

    public Fluid getFluid() {
        return FluidRegistry.getFluid(fluid);
    }

    @Override
    public long getMaxProgress() {
        return amount;
    }

    /** A filled bucket if the fluid has one, otherwise an empty bucket. */
    @Override
    public ItemStack getIcon() {
        Fluid f = getFluid();
        if (f != null) {
            ItemStack filled = FluidContainerRegistry.fillFluidContainer(
                new FluidStack(f, FluidContainerRegistry.BUCKET_VOLUME),
                new ItemStack(Items.bucket));
            if (filled != null) return filled;
        }
        return new ItemStack(Items.bucket);
    }

    @Override
    public String getTitle() {
        Fluid f = getFluid();
        String name = f != null ? f.getLocalizedName(new FluidStack(f, 1)) : fluid;
        return StatCollector.translateToLocalFormatted("waymark.task.fluid", amount, name);
    }

    @Override
    public boolean hasSubmitButton() {
        return consume;
    }

    @Override
    public boolean isAutoDetected() {
        return !consume;
    }

    /** The fluid held by one item of this stack, or null if it holds none (or another fluid). */
    private static FluidStack contents(ItemStack stack, Fluid fluid) {
        if (stack == null || stack.getItem() == null || fluid == null) return null;
        FluidStack fs = stack.getItem() instanceof IFluidContainerItem
            ? ((IFluidContainerItem) stack.getItem()).getFluid(stack)
            : FluidContainerRegistry.getFluidForFilledItem(stack);
        return fs != null && fs.getFluid() == fluid && fs.amount > 0 ? fs : null;
    }

    @Override
    public long detect(EntityPlayerMP player, long progress) {
        if (consume) return progress;
        Fluid f = getFluid();
        long found = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            FluidStack fs = contents(stack, f);
            if (fs != null) found += (long) fs.amount * stack.stackSize;
        }
        return Math.max(progress, Math.min(found, amount));
    }

    @Override
    public long submit(EntityPlayerMP player, long progress) {
        if (!consume) return detect(player, progress);
        Fluid f = getFluid();
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int i = 0; i < inventory.length && progress < amount; i++) {
            while (progress < amount) {
                ItemStack stack = inventory[i];
                FluidStack fs = contents(stack, f);
                if (fs == null) break;
                // Work on one container at a time so stacks of cells are handled like single ones.
                ItemStack single = stack.splitStack(1);
                if (stack.stackSize <= 0) inventory[i] = null;
                ItemStack leftover;
                if (single.getItem() instanceof IFluidContainerItem) {
                    FluidStack drained = ((IFluidContainerItem) single.getItem())
                        .drain(single, (int) Math.min(Integer.MAX_VALUE, amount - progress), true);
                    leftover = single;
                    if (drained == null || drained.amount <= 0) {
                        // A container that refuses to drain would otherwise be retried forever.
                        giveBack(player, leftover);
                        break;
                    }
                    progress += drained.amount;
                } else {
                    // Registered containers (buckets) are emptied whole; only what the task needs counts.
                    progress += Math.min(fs.amount, amount - progress);
                    leftover = FluidContainerRegistry.drainFluidContainer(single);
                }
                giveBack(player, leftover);
                if (inventory[i] == null) break;
            }
        }
        player.inventoryContainer.detectAndSendChanges();
        return progress;
    }

    private static void giveBack(EntityPlayerMP player, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        if (!player.inventory.addItemStackToInventory(stack) && stack.stackSize > 0)
            player.dropPlayerItemWithRandomChoice(stack, false);
    }

    @Override
    public void writeJson(JsonObject json) {
        json.addProperty("fluid", fluid);
        if (amount != FluidContainerRegistry.BUCKET_VOLUME) json.addProperty("amount", amount);
        if (!consume) json.addProperty("consume", false);
    }

    @Override
    public void readJson(JsonObject json) {
        fluid = JsonUtil.getString(json, "fluid", "water");
        amount = Math.max(1, JsonUtil.getLong(json, "amount", FluidContainerRegistry.BUCKET_VOLUME));
        consume = JsonUtil.getBoolean(json, "consume", true);
    }
}
