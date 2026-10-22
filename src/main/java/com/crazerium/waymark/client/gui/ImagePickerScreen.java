package com.crazerium.waymark.client.gui;

import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.client.ClientImages;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Picks a picture for a quest description: one already on the server, a new one uploaded from this computer (with
 * the system file dialog, or from the waymark_uploads folder when that dialog cannot open), or a texture path.
 */
@SideOnly(Side.CLIENT)
public class ImagePickerScreen extends GuiScreen {

    private static final int CELL = 52;

    private final GuiScreen parent;
    private final Consumer<String> onPick;
    private int px, py, pw, ph, cols, gridTop, gridRows;
    private int scroll;
    private String status;
    private int statusColor;
    /** Set by the file dialog thread, picked up on the render thread. */
    private volatile File chosen;
    private volatile boolean dialogOpen;
    private int indexSizeAtUpload = -1;

    /** {@code onPick} gets the description line to insert. */
    public ImagePickerScreen(GuiScreen parent, Consumer<String> onPick) {
        this.parent = parent;
        this.onPick = onPick;
    }

    private File uploadsDir() {
        return new File(mc.mcDataDir, "waymark_uploads");
    }

    private List<File> localFiles() {
        File[] files = uploadsDir().listFiles(
            (d, n) -> n.toLowerCase()
                .endsWith(".png"));
        List<File> list = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files);
            list.addAll(Arrays.asList(files));
        }
        return list;
    }

    @Override
    public void initGui() {
        pw = Math.min(420, width - 20);
        ph = height - 20;
        px = (width - pw) / 2;
        py = 10;
        cols = Math.max(1, (pw - 16) / CELL);
        gridTop = py + 24;
        gridRows = Math.max(1, (ph - 24 - 90) / CELL);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        File file = chosen;
        if (file != null) {
            chosen = null;
            upload(file);
        }
        // The new image's name arrives with the next index; then the upload is done.
        if (indexSizeAtUpload >= 0 && ClientImages.names()
            .size() > indexSizeAtUpload) {
            indexSizeAtUpload = -1;
            setStatus(I18n.format("waymark.images.uploaded"), 0x55FF55);
        }
    }

    private void upload(File file) {
        String error = ClientImages.upload(file);
        if (error != null) {
            setStatus(I18n.format(error), 0xFF5555);
            return;
        }
        indexSizeAtUpload = ClientImages.names()
            .size();
        setStatus(I18n.format("waymark.images.uploading", file.getName()), 0xFFCC66);
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        drawDefaultBackground();
        GuiUtil.frame(px, py, pw, ph, 0xF0101018, 0xFF8090B0);
        fontRendererObj.drawStringWithShadow(I18n.format("waymark.images.heading"), px + 8, py + 8, 0xFFFFFF);

        // Server images.
        List<String> names = ClientImages.names();
        int maxScroll = Math.max(0, (names.size() + cols - 1) / cols - gridRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        String hoveredName = null;
        for (int i = 0; i < cols * gridRows; i++) {
            int index = scroll * cols + i;
            if (index >= names.size()) break;
            int cx = px + 8 + (i % cols) * CELL, cy = gridTop + (i / cols) * CELL;
            boolean hover = GuiUtil.inside(mx, my, cx, cy, CELL - 4, CELL - 4);
            GuiUtil.frame(cx, cy, CELL - 4, CELL - 4, hover ? 0xFF303048 : 0xFF181820, hover ? 0xFFFFCC66 : 0xFF404860);
            drawThumbnail(names.get(index), cx + 2, cy + 2, CELL - 8);
            if (hover) hoveredName = names.get(index);
        }
        if (names.isEmpty())
            fontRendererObj.drawString(I18n.format("waymark.images.none"), px + 10, gridTop + 4, 0x808080);

        // Local uploads folder.
        int localTop = gridTop + gridRows * CELL + 4;
        fontRendererObj.drawString(I18n.format("waymark.images.folder"), px + 8, localTop, 0xC0C0C0);
        int lx = px + 8, ly = localTop + 12;
        for (File file : localFiles()) {
            String label = GuiUtil.trim(fontRendererObj, file.getName(), 100);
            int bw = fontRendererObj.getStringWidth(label) + 10;
            if (lx + bw > px + pw - 8) {
                lx = px + 8;
                ly += 16;
            }
            if (ly > py + ph - 44) break;
            GuiUtil.button(fontRendererObj, lx, ly, bw, 14, label, mx, my);
            lx += bw + 4;
        }

        // Buttons.
        int by = py + ph - 22;
        String[] labels = buttonLabels();
        int bx = px + 8;
        for (String label : labels) {
            int bw = fontRendererObj.getStringWidth(label) + 12;
            GuiUtil.button(fontRendererObj, bx, by, bw, 16, label, mx, my);
            bx += bw + 4;
        }
        if (status != null) fontRendererObj.drawString(status, px + 8, by - 12, statusColor);

        super.drawScreen(mx, my, partialTicks);
        if (hoveredName != null)
            drawHoveringText(java.util.Collections.singletonList(hoveredName), mx, my, fontRendererObj);
    }

    private String[] buttonLabels() {
        return new String[] { I18n.format("waymark.images.upload"), I18n.format("waymark.images.open_folder"),
            I18n.format("waymark.images.texture_path"), I18n.format("gui.cancel") };
    }

    private void drawThumbnail(String name, int x, int y, int size) {
        ClientImages.Image image = ClientImages.get(name);
        if (image == null) {
            drawCenteredString(fontRendererObj, "...", x + size / 2, y + size / 2 - 4, 0x808080);
            return;
        }
        int w = size, h = size;
        if (image.width > image.height) h = Math.max(1, size * image.height / image.width);
        else w = Math.max(1, size * image.width / Math.max(1, image.height));
        int dx = x + (size - w) / 2, dy = y + (size - h) / 2;
        mc.getTextureManager()
            .bindTexture(image.texture);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(dx, dy + h, 0, 0, 1);
        t.addVertexWithUV(dx + w, dy + h, 0, 1, 1);
        t.addVertexWithUV(dx + w, dy, 0, 1, 0);
        t.addVertexWithUV(dx, dy, 0, 0, 0);
        t.draw();
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        List<String> names = ClientImages.names();
        for (int i = 0; i < cols * gridRows; i++) {
            int index = scroll * cols + i;
            if (index >= names.size()) break;
            int cx = px + 8 + (i % cols) * CELL, cy = gridTop + (i / cols) * CELL;
            if (!GuiUtil.inside(mx, my, cx, cy, CELL - 4, CELL - 4)) continue;
            String name = names.get(index);
            ClientImages.Image image = ClientImages.get(name);
            int width = image != null ? Math.min(image.width, 128) : 128;
            pick("{image:server:" + name + " width=" + width + "}");
            return;
        }

        int localTop = gridTop + gridRows * CELL + 4;
        int lx = px + 8, ly = localTop + 12;
        for (File file : localFiles()) {
            String label = GuiUtil.trim(fontRendererObj, file.getName(), 100);
            int bw = fontRendererObj.getStringWidth(label) + 10;
            if (lx + bw > px + pw - 8) {
                lx = px + 8;
                ly += 16;
            }
            if (ly > py + ph - 44) break;
            if (GuiUtil.inside(mx, my, lx, ly, bw, 14)) {
                upload(file);
                return;
            }
            lx += bw + 4;
        }

        int by = py + ph - 22;
        String[] labels = buttonLabels();
        int bx = px + 8;
        for (int i = 0; i < labels.length; i++) {
            int bw = fontRendererObj.getStringWidth(labels[i]) + 12;
            if (GuiUtil.inside(mx, my, bx, by, bw, 16)) {
                if (i == 0) openFileDialog();
                else if (i == 1) openFolder();
                else if (i == 2) pick(DescriptionPages.IMAGE_TEMPLATE);
                else mc.displayGuiScreen(parent);
                return;
            }
            bx += bw + 4;
        }
    }

    private void pick(String line) {
        onPick.accept(line);
        mc.displayGuiScreen(parent);
    }

    /** The system file dialog, on its own thread so the game keeps drawing while it is open. */
    private void openFileDialog() {
        if (dialogOpen) return;
        if (GraphicsEnvironment.isHeadless()) {
            setStatus(I18n.format("waymark.images.no_dialog"), 0xFF5555);
            return;
        }
        dialogOpen = true;
        String title = I18n.format("waymark.images.upload");
        Thread thread = new Thread(() -> {
            try {
                FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
                dialog.setFile("*.png");
                dialog.setVisible(true);
                if (dialog.getFile() != null) chosen = new File(dialog.getDirectory(), dialog.getFile());
            } catch (Throwable e) {
                Waymark.LOG.warn("File dialog failed", e);
            } finally {
                dialogOpen = false;
            }
        }, "Waymark file dialog");
        thread.setDaemon(true);
        thread.start();
    }

    private void openFolder() {
        File dir = uploadsDir();
        dir.mkdirs();
        try {
            Desktop.getDesktop()
                .open(dir);
        } catch (Throwable e) {
            Sys.openURL("file://" + dir.getAbsolutePath());
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scroll += wheel > 0 ? -1 : 1;
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    }
}
