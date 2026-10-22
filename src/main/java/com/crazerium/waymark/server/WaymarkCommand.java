package com.crazerium.waymark.server;

import java.io.IOException;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.quest.Chapter;
import com.crazerium.waymark.quest.QuestBook;

public class WaymarkCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "waymark";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/waymark <reload|save|info>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1) throw new WrongUsageException(getCommandUsage(sender));
        switch (args[0]) {
            case "reload":
                ServerQuests.load();
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
                reply(sender, describe(ServerQuests.get()));
                for (Chapter chapter : ServerQuests.get()
                    .getChapters()) {
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
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "reload", "save", "info") : null;
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
