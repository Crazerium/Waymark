package com.crazerium.waymark.server;

import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.network.MessageEditApply;
import com.crazerium.waymark.network.WaymarkNetwork;
import com.crazerium.waymark.quest.BookEdits;
import com.crazerium.waymark.quest.Chapter;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.util.Ids;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Checks and applies editor changes from clients. Only operators (permission level 2) may edit. */
public final class ServerEditor {

    private ServerEditor() {}

    public static boolean canEdit(EntityPlayerMP player) {
        return player.canCommandSenderUseCommand(2, "waymark");
    }

    public static void handle(EntityPlayerMP player, JsonObject op) {
        QuestBook book = ServerQuests.get();
        if (book == null || !canEdit(player)) return;
        // New objects get their ids here, so every copy of the book uses the same ones.
        if (BookEdits.CREATE_QUEST.equals(JsonUtil.getString(op, "op", "")))
            op.addProperty("id", Ids.format(book.newId()));

        Set<Chapter> changed;
        try {
            changed = BookEdits.apply(book, op);
        } catch (IllegalArgumentException e) {
            ChatComponentText message = new ChatComponentText(e.getMessage());
            message.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED));
            player.addChatMessage(message);
            return;
        }
        Waymark.LOG.info("{} edited quests: {}", player.getCommandSenderName(), op);
        ServerQuests.onEdited(changed);
        WaymarkNetwork.CHANNEL.sendToAll(new MessageEditApply(op));
    }
}
