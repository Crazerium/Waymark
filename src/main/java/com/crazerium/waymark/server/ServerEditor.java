package com.crazerium.waymark.server;

import java.util.HashSet;
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
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.util.Ids;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Checks and applies editor changes from clients. Only operators (permission level 2) may edit. */
public final class ServerEditor {

    private ServerEditor() {}

    public static boolean canEdit(EntityPlayerMP player) {
        return player.canCommandSenderUseCommand(2, "waymark");
    }

    /**
     * Tasks and rewards added in the editor arrive without an id. They, and any id already used elsewhere in the book
     * or twice in this quest, get a fresh one; ids that belong to this quest are kept so progress survives.
     */
    private static void assignIds(QuestBook book, JsonObject op) {
        Quest quest = book.getQuest(Ids.parse(JsonUtil.getString(op, "quest", "")));
        JsonObject data = JsonUtil.getObject(op, "data");
        if (quest == null || data == null) return;
        Set<Long> own = new HashSet<>();
        for (Task task : quest.getTasks()) own.add(task.getId());
        for (Reward reward : quest.getRewards()) own.add(reward.getId());
        Set<Long> used = new HashSet<>();
        for (String list : new String[] { "tasks", "rewards" }) {
            for (JsonElement e : JsonUtil.getArray(data, list)) {
                if (!e.isJsonObject()) continue;
                JsonObject obj = e.getAsJsonObject();
                long id = Ids.parse(JsonUtil.getString(obj, "id", ""));
                boolean usable = id != Ids.NONE && (own.contains(id) || book.get(id) == null) && used.add(id);
                if (!usable) {
                    long fresh = book.newId();
                    used.add(fresh);
                    obj.addProperty("id", Ids.format(fresh));
                }
            }
        }
    }

    public static void handle(EntityPlayerMP player, JsonObject op) {
        QuestBook book = ServerQuests.get();
        if (book == null || !canEdit(player)) return;
        // New objects get their ids here, so every copy of the book uses the same ones.
        String name = JsonUtil.getString(op, "op", "");
        if (BookEdits.CREATE_QUEST.equals(name)) op.addProperty("id", Ids.format(book.newId()));
        if (BookEdits.UPDATE_QUEST.equals(name)) assignIds(book, op);

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
