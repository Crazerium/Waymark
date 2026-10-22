package com.crazerium.waymark.quest.reward;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.EditorField;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.RewardType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Runs a command as the server console. {@code {player}} is replaced with the player's name. */
public class CommandReward extends Reward {

    private String command = "";

    public CommandReward(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public RewardType<?> getType() {
        return BuiltinTypes.COMMAND_REWARD;
    }

    @Override
    public List<EditorField> editorFields() {
        return Arrays.asList(EditorField.text("command", ""));
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    @Override
    public String getTitle() {
        return StatCollector.translateToLocal("waymark.reward.command");
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Blocks.command_block);
    }

    @Override
    public void claim(EntityPlayerMP player, int choice) {
        if (command.isEmpty()) return;
        MinecraftServer server = MinecraftServer.getServer();
        server.getCommandManager()
            .executeCommand(server, command.replace("{player}", player.getCommandSenderName()));
    }

    @Override
    public void writeJson(JsonObject json) {
        json.addProperty("command", command);
    }

    @Override
    public void readJson(JsonObject json) {
        command = JsonUtil.getString(json, "command", "");
    }
}
