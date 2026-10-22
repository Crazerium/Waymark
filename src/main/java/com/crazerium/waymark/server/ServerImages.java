package com.crazerium.waymark.server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.network.MessageBlob;
import com.crazerium.waymark.network.MessageImageIndex;
import com.crazerium.waymark.network.WaymarkNetwork;

/**
 * Pictures for quest descriptions, kept in config/waymark/images. Clients get the list of names on login and fetch
 * each picture the first time they need it. Operators can upload new ones from the editor.
 */
public final class ServerImages {

    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_SIZE = 1024;

    private static File dir;
    /** Uploads being received, by player. */
    private static final Map<UUID, ByteArrayOutputStream> uploads = new HashMap<>();

    private ServerImages() {}

    public static void init(File configDir) {
        dir = new File(new File(configDir, "waymark"), "images");
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        String[] files = dir == null ? null
            : dir.list(
                (d, n) -> n.toLowerCase(Locale.ROOT)
                    .endsWith(".png"));
        if (files != null) {
            Arrays.sort(files);
            names.addAll(Arrays.asList(files));
        }
        return names;
    }

    /** True for names that cannot leave the images folder or point at anything but a png. */
    private static boolean validName(String name) {
        return name.matches("[a-z0-9_.-]+\\.png") && !name.contains("..");
    }

    public static void sendIndex(EntityPlayerMP player) {
        WaymarkNetwork.CHANNEL.sendTo(new MessageImageIndex(names()), player);
    }

    public static void sendImage(EntityPlayerMP player, String name) {
        if (!validName(name)) return;
        File file = new File(dir, name);
        if (!file.isFile()) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            for (MessageBlob part : MessageBlob.split(MessageBlob.IMAGE, name, bytes))
                WaymarkNetwork.CHANNEL.sendTo(part, player);
        } catch (IOException e) {
            Waymark.LOG.error("Failed to read quest image {}", file, e);
        }
    }

    /** One part of an upload from an operator; the last part checks and saves the picture. */
    public static void receiveUpload(EntityPlayerMP player, String requestedName, int part, int total, byte[] data) {
        if (!ServerEditor.canEdit(player)) return;
        UUID id = player.getUniqueID();
        if (part == 0) uploads.put(id, new ByteArrayOutputStream());
        ByteArrayOutputStream buffer = uploads.get(id);
        if (buffer == null) return;
        buffer.write(data, 0, data.length);
        if (buffer.size() > MAX_BYTES) {
            uploads.remove(id);
            fail(player, "waymark: image is larger than 1 MB");
            return;
        }
        if (part < total - 1) return;
        uploads.remove(id);

        byte[] bytes = buffer.toByteArray();
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            fail(player, "waymark: that file is not a PNG image");
            return;
        }
        if (image.getWidth() > MAX_SIZE || image.getHeight() > MAX_SIZE) {
            fail(player, "waymark: image is larger than " + MAX_SIZE + "x" + MAX_SIZE);
            return;
        }
        try {
            Files.createDirectories(dir.toPath());
            String name = freeName(requestedName);
            Files.write(new File(dir, name).toPath(), bytes);
            Waymark.LOG.info("{} uploaded quest image {}", player.getCommandSenderName(), name);
        } catch (IOException e) {
            Waymark.LOG.error("Failed to save quest image", e);
            fail(player, "waymark: could not save the image");
            return;
        }
        // Everyone's picker and descriptions can use it right away.
        WaymarkNetwork.CHANNEL.sendToAll(new MessageImageIndex(names()));
    }

    /** A safe, unused file name based on what the uploader called the file. */
    private static String freeName(String requested) {
        String base = requested.toLowerCase(Locale.ROOT)
            .replaceAll("\\.png$", "")
            .replaceAll("[^a-z0-9_.-]+", "_")
            .replaceAll("\\.+", ".")
            .replaceAll("^[._]+|[._]+$", "");
        if (base.isEmpty()) base = "image";
        String name = base + ".png";
        for (int i = 2; new File(dir, name).exists(); i++) name = base + "_" + i + ".png";
        return name;
    }

    private static void fail(EntityPlayerMP player, String message) {
        ChatComponentText text = new ChatComponentText(message);
        text.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED));
        player.addChatMessage(text);
    }
}
