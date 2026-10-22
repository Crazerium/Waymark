package com.crazerium.waymark.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.crazerium.waymark.Waymark;

/** Right-click opens the quest book, the same as its key. */
public class ItemQuestBook extends Item {

    public static ItemQuestBook INSTANCE;

    public ItemQuestBook() {
        setUnlocalizedName("waymark.quest_book");
        setTextureName("minecraft:book_written");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
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
