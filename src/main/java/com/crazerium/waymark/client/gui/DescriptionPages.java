package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.crazerium.waymark.client.ClientImages;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Quest descriptions are lines of text with two special kinds of line:
 *
 * <pre>
 * {&#64;pagebreak}                                   starts a new page
 * {image:waymark:textures/quests/boiler.png width=128 height=64 align=center}   a texture from resources
 * {image:server:boiler.png width=128}            a picture uploaded to the server
 * </pre>
 *
 * Image width, height and align are optional; without a height the picture keeps its own proportions.
 */
@SideOnly(Side.CLIENT)
final class DescriptionPages {

    static final String PAGE_BREAK = "{@pagebreak}";
    static final String IMAGE_TEMPLATE = "{image:minecraft:textures/items/diamond.png width=64}";

    private static final Pattern IMAGE = Pattern.compile("^\\{image:([^\\s}]+)((?:\\s+\\w+=\\w+)*)\\}$");
    private static final Pattern PARAM = Pattern.compile("(\\w+)=(\\w+)");
    /** Texture sizes in pixels, read once per texture. */
    private static final Map<String, int[]> sizes = new HashMap<>();

    private DescriptionPages() {}

    static List<List<String>> pages(List<String> lines) {
        List<List<String>> pages = new ArrayList<>();
        List<String> page = new ArrayList<>();
        for (String line : lines) {
            if (line.trim()
                .equalsIgnoreCase(PAGE_BREAK)) {
                pages.add(page);
                page = new ArrayList<>();
            } else {
                page.add(line);
            }
        }
        pages.add(page);
        return pages;
    }

    /** An image line, or null if the line is ordinary text. */
    static Image image(String line) {
        Matcher m = IMAGE.matcher(line.trim());
        if (!m.matches()) return null;
        Image image = new Image(m.group(1));
        Matcher p = PARAM.matcher(m.group(2));
        while (p.find()) {
            String key = p.group(1), value = p.group(2);
            try {
                if (key.equals("width")) image.width = Integer.parseInt(value);
                else if (key.equals("height")) image.height = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignore a bad number and use the default.
            }
            if (key.equals("align")) image.align = value;
        }
        return image;
    }

    static final class Image {

        /** A texture from resources, or null for a picture uploaded to the server. */
        final ResourceLocation texture;
        /** The server image name for "server:..." paths. */
        final String serverImage;
        int width = -1, height = -1;
        String align = "center";

        Image(String path) {
            serverImage = path.startsWith("server:") ? path.substring("server:".length()) : null;
            texture = serverImage == null ? new ResourceLocation(path) : null;
        }

        /** Draws the image fitted into {@code maxWidth} at the top of {@code y}; returns its drawn height. */
        int draw(int left, int y, int maxWidth) {
            int[] size;
            if (serverImage != null) {
                ClientImages.Image image = ClientImages.get(serverImage);
                if (image == null) {
                    // Still downloading: keep the space it will need so the text does not jump much.
                    int w = width > 0 ? Math.min(width, maxWidth) : Math.min(64, maxWidth);
                    int h = height > 0 ? height : w / 2;
                    GuiUtil.frame(left + (maxWidth - w) / 2, y, w, h, 0x40FFFFFF, 0xFF404860);
                    return h;
                }
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(image.texture);
                size = new int[] { image.width, image.height };
            } else {
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(texture);
                size = sizes.computeIfAbsent(
                    texture.toString(),
                    k -> new int[] { GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH),
                        GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) });
            }
            int w = width > 0 ? width : Math.min(size[0], 128);
            w = Math.max(1, Math.min(w, maxWidth));
            int h = height > 0 ? height : (size[0] > 0 ? w * size[1] / size[0] : w);
            int x = align.equals("left") ? left
                : align.equals("right") ? left + maxWidth - w : left + (maxWidth - w) / 2;

            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.addVertexWithUV(x, y + h, 0, 0, 1);
            t.addVertexWithUV(x + w, y + h, 0, 1, 1);
            t.addVertexWithUV(x + w, y, 0, 1, 0);
            t.addVertexWithUV(x, y, 0, 0, 0);
            t.draw();
            GL11.glDisable(GL11.GL_BLEND);
            return h;
        }
    }
}
