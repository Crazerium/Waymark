package com.crazerium.waymark.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.compat.AppEng;
import com.crazerium.waymark.server.ServerProgress;

/** Right-click opens the quest book, the same as its key. */
public class ItemQuestBook extends Item {

    public static ItemQuestBook INSTANCE;

    public ItemQuestBook() {
        setUnlocalizedName("waymark.quest_book");
        setTextureName("minecraft:book_written");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    /**
     * Right-clicking a block of an ME network binds that network to the player's team (the server checks that the
     * team has claimed the chunk). Any other block opens the book as usual.
     */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!AppEng.isNetworkBlock(world, x, y, z)) return false;
        if (!world.isRemote && player instanceof EntityPlayerMP)
            ServerProgress.bindMeNetwork((EntityPlayerMP) player, world.provider.dimensionId, x, y, z);
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) Waymark.proxy.openQuestBook();
        return stack;
    }

    /** Glints like an enchanted book, so it stands out from vanilla books. */
    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return true;
    }
}
