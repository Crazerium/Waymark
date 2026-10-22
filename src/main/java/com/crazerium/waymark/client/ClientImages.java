package com.crazerium.waymark.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.network.MessageImageRequest;
import com.crazerium.waymark.network.MessageImageUpload;
import com.crazerium.waymark.network.WaymarkNetwork;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The server's quest images on this client: the list of names, and the pictures downloaded so far as textures. A
 * picture is requested from the server the first time something wants to draw it.
 */
@SideOnly(Side.CLIENT)
public final class ClientImages {

    public static final class Image {

        public final ResourceLocation texture;
        public final int width, height;

        Image(ResourceLocation texture, int width, int height) {
            this.texture = texture;
            this.width = width;
            this.height = height;
        }
    }

    private static List<String> index = new ArrayList<>();
    private static final Map<String, Image> loaded = new HashMap<>();
    private static final Set<String> requested = new HashSet<>();

    private ClientImages() {}

    public static List<String> names() {
        return Collections.unmodifiableList(index);
    }

    public static void setIndex(List<String> names) {
        index = new ArrayList<>(names);
    }

    /** The picture, or null while it is still downloading (the first call starts the download). */
    public static Image get(String name) {
        Image image = loaded.get(name);
        if (image == null && index.contains(name) && requested.add(name))
            WaymarkNetwork.CHANNEL.sendToServer(new MessageImageRequest(name));
        return image;
    }

    public static void receive(String name, byte[] bytes) {
        requested.remove(name);
        try {
            BufferedImage picture = ImageIO.read(new ByteArrayInputStream(bytes));
            if (picture == null) return;
            ResourceLocation texture = Minecraft.getMinecraft()
                .getTextureManager()
                .getDynamicTextureLocation("waymark_image", new DynamicTexture(picture));
            loaded.put(name, new Image(texture, picture.getWidth(), picture.getHeight()));
        } catch (IOException e) {
            Waymark.LOG.warn("Could not read quest image {}", name, e);
        }
    }

    /**
     * Sends a PNG from this computer to the server. Returns an error message key, or null if it was sent; the
     * server checks it again and the new name arrives with the next index.
     */
    public static String upload(File file) {
        if (!file.getName()
            .toLowerCase()
            .endsWith(".png")) return "waymark.images.not_png";
        if (file.length() > 1024 * 1024) return "waymark.images.too_big";
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            for (MessageImageUpload part : MessageImageUpload.split(file.getName(), bytes))
                WaymarkNetwork.CHANNEL.sendToServer(part);
            return null;
        } catch (IOException e) {
            Waymark.LOG.warn("Could not read {}", file, e);
            return "waymark.images.read_failed";
        }
    }

    /** Forgets everything from the last server, freeing the textures. */
    public static void clear() {
        // Disconnects are handled off the render thread; textures may only be freed on it.
        List<ResourceLocation> textures = new ArrayList<>();
        for (Image image : loaded.values()) textures.add(image.texture);
        Minecraft.getMinecraft()
            .func_152344_a(
                () -> {
                    for (ResourceLocation texture : textures) Minecraft.getMinecraft()
                        .getTextureManager()
                        .deleteTexture(texture);
                });
        loaded.clear();
        requested.clear();
        index = new ArrayList<>();
    }
}
