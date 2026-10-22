package com.crazerium.waymark.server;

import java.io.IOException;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.progress.TeamProgress;
import com.crazerium.waymark.quest.Chapter;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.util.Ids;

public class WaymarkCommand extends CommandBase {

    private static final String[] SUBCOMMANDS = { "reload", "save", "info", "progress", "complete", "reset", "claim" };

    @Override
    public String getCommandName() {
        return "waymark";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/waymark <reload|save|info|progress [player]|complete <quest id> [player]|reset [player]|claim [player]>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        QuestBook book = ServerQuests.get();
        switch (args[0]) {
            case "reload":
                ServerQuests.load();
                ServerProgress.syncAll();
                reply(sender, "Quests reloaded: " + describe(ServerQuests.get()));
                break;
            case "save":
                try {
                    ServerQuests.save();
                } catch (IOException e) {
                    Waymark.LOG.error("Failed to save quests", e);
                    throw new CommandException("Failed to save quests: " + e.getMessage());
                }
                reply(sender, "Quests saved");
                break;
            case "info":
                reply(sender, describe(book));
                for (Chapter chapter : book.getChapters()) {
                    reply(
                        sender,
                        " - " + chapter.getTitle()
                            + " ("
                            + chapter.getFilename()
                            + ".json, "
                            + chapter.getQuests()
                                .size()
                            + " quests)");
                }
                break;
            case "progress": {
                EntityPlayerMP player = playerArg(sender, args, 1);
                TeamProgress team = ServerProgress.get(player);
                List<Quest> quests = book.getAllQuests();
                int done = 0;
                for (Quest quest : quests) if (team.isCompleted(quest)) done++;
                reply(sender, player.getCommandSenderName() + ": " + done + "/" + quests.size() + " quests completed");
                for (Quest quest : quests) {
                    if (team.canProgress(quest)) reply(
                        sender,
                        " - available: " + quest.getDisplayTitle() + " [" + Ids.format(quest.getId()) + "]");
                }
                break;
            }
            case "complete": {
                if (args.length < 2) throw new WrongUsageException(getCommandUsage(sender));
                Quest quest = book.getQuest(Ids.parse(args[1]));
                if (quest == null) throw new CommandException("No quest with id " + args[1]);
                EntityPlayerMP player = playerArg(sender, args, 2);
                ServerProgress.forceComplete(ServerProgress.get(player), quest);
                reply(sender, "Completed " + quest.getDisplayTitle() + " for " + player.getCommandSenderName());
                break;
            }
            case "reset": {
                EntityPlayerMP player = playerArg(sender, args, 1);
                ServerProgress.reset(ServerProgress.get(player));
                reply(sender, "Progress of " + player.getCommandSenderName() + " reset");
                break;
            }
            case "claim": {
                // Debug helper until the quest screen has claim buttons.
                EntityPlayerMP player = playerArg(sender, args, 1);
                int claimed = 0;
                for (Quest quest : book.getAllQuests()) {
                    for (Reward reward : quest.getRewards()) {
                        TeamProgress team = ServerProgress.get(player);
                        if (team.isCompleted(quest) && !team.isClaimed(player.getUniqueID(), reward)) {
                            ServerProgress.claim(player, reward.getId());
                            claimed++;
                        }
                    }
                }
                reply(sender, "Claimed " + claimed + " rewards for " + player.getCommandSenderName());
                break;
            }
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static EntityPlayerMP playerArg(ICommandSender sender, String[] args, int index) {
        return args.length > index ? getPlayer(sender, args[index]) : getCommandSenderAsPlayer(sender);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        boolean playerSlot = args.length == 2 && !args[0].equals("complete")
            || args.length == 3 && args[0].equals("complete");
        return playerSlot ? getListOfStringsMatchingLastWord(
            args,
            MinecraftServer.getServer()
                .getAllUsernames())
            : null;
    }

    private static String describe(QuestBook book) {
        int quests = 0;
        for (Chapter chapter : book.getChapters()) quests += chapter.getQuests()
            .size();
        return book.getChapters()
            .size() + " chapters, "
            + quests
            + " quests";
    }

    private static void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }
}
