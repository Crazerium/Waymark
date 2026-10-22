package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.crazerium.waymark.client.ClientEvents;
import com.crazerium.waymark.client.ClientQuests;
import com.crazerium.waymark.client.QuestState;
import com.crazerium.waymark.quest.Chapter;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.quest.QuestLink;
import com.crazerium.waymark.quest.QuestObject;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The quest book: chapter list on the left, a pannable and zoomable map of the chapter's quests on the right. */
@SideOnly(Side.CLIENT)
public class QuestBookScreen extends GuiScreen {

    private static final int MARGIN = 8;
    private static final int HEADER = 18;
    private static final int LIST_WIDTH = 110;
    private static final int ROW = 20;
    /** Screen pixels per quest position unit at zoom 1. */
    private static final int GRID = 28;
    /** Node size in pixels for a size 1 quest at zoom 1. */
    private static final int NODE = 22;
    private static final double MIN_ZOOM = 0.25, MAX_ZOOM = 3;

    // Kept between openings so the book reopens where the player left it.
    private static long lastChapterId;
    private static final Map<Long, double[]> savedViews = new HashMap<>();

    private Chapter chapter;
    private double panX, panY, zoom = 1;
    private int listScroll;
    private QuestView questView;

    private boolean dragging, dragMoved;
    private int dragStartX, dragStartY, lastMouseX, lastMouseY;

    private List<String> tooltip;
    private ItemStack tooltipStack;
    private int tooltipX, tooltipY;

    private int listX, listY, listH, canvasX, canvasY, canvasW, canvasH;

    @Override
    public void initGui() {
        listX = MARGIN;
        listY = MARGIN + HEADER;
        listH = height - listY - MARGIN;
        canvasX = listX + LIST_WIDTH + 4;
        canvasY = listY;
        canvasW = width - canvasX - MARGIN;
        canvasH = listH;
        if (questView != null) questView.layout();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    FontRenderer font() {
        return fontRendererObj;
    }

    void showTooltip(List<String> lines, int x, int y) {
        tooltip = lines;
        tooltipX = x;
        tooltipY = y;
    }

    void showItemTooltip(ItemStack stack, int x, int y) {
        tooltipStack = stack;
        tooltipX = x;
        tooltipY = y;
    }

    void openQuest(Quest quest) {
        questView = new QuestView(this, quest);
    }

    void closeQuest() {
        questView = null;
    }

    void playClick() {
        mc.getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    /** Re-resolves the open chapter and quest after the server sent a new book (e.g. after /waymark reload). */
    private void refresh(QuestBook book) {
        if (chapter != null && chapter.getBook() == book) return;
        QuestObject found = book.get(chapter != null ? chapter.getId() : lastChapterId);
        chapter = null;
        selectChapter(
            found instanceof Chapter ? (Chapter) found
                : book.getChapters()
                    .isEmpty() ? null
                        : book.getChapters()
                            .get(0));
        if (questView != null) {
            Quest quest = book.getQuest(questView.quest.getId());
            questView = quest != null ? new QuestView(this, quest) : null;
        }
    }

    private void selectChapter(Chapter next) {
        saveView();
        chapter = next;
        if (next == null) return;
        lastChapterId = next.getId();
        double[] view = savedViews.get(next.getId());
        if (view != null) {
            panX = view[0];
            panY = view[1];
            zoom = view[2];
        } else {
            centerView();
        }
    }

    private void saveView() {
        if (chapter != null) savedViews.put(chapter.getId(), new double[] { panX, panY, zoom });
    }

    /** Something drawn on the map: one of the chapter's quests, or a link to a quest from another chapter. */
    private static final class Node {

        final Quest quest;
        final double x, y, size;
        final boolean link;
        final QuestState state;

        Node(Quest quest, double x, double y, double size, boolean link) {
            this.quest = quest;
            this.x = x;
            this.y = y;
            this.size = size;
            this.link = link;
            this.state = QuestState.of(quest);
        }
    }

    /** Visible nodes of the open chapter, quests first and links after. */
    private List<Node> nodes() {
        List<Node> nodes = new ArrayList<>();
        if (chapter == null) return nodes;
        for (Quest q : chapter.getQuests()) {
            Node node = new Node(q, q.getX(), q.getY(), q.getSize(), false);
            if (node.state != QuestState.HIDDEN) nodes.add(node);
        }
        for (QuestLink link : chapter.getLinks()) {
            Quest q = link.getQuest();
            if (q == null) continue;
            Node node = new Node(q, link.getX(), link.getY(), link.getSize(), true);
            if (node.state != QuestState.HIDDEN) nodes.add(node);
        }
        return nodes;
    }

    private void centerView() {
        zoom = 1;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Node n : nodes()) {
            minX = Math.min(minX, n.x);
            maxX = Math.max(maxX, n.x);
            minY = Math.min(minY, n.y);
            maxY = Math.max(maxY, n.y);
        }
        if (minX > maxX) {
            panX = panY = 0;
            return;
        }
        panX = -(minX + maxX) / 2 * GRID;
        panY = -(minY + maxY) / 2 * GRID;
    }

    private double screenX(Node n) {
        return canvasX + canvasW / 2.0 + panX + n.x * GRID * zoom;
    }

    private double screenY(Node n) {
        return canvasY + canvasH / 2.0 + panY + n.y * GRID * zoom;
    }

    private int halfSize(Node n) {
        return Math.max(4, (int) Math.round(NODE * n.size * zoom / 2));
    }

    private Node nodeAt(List<Node> nodes, int mx, int my) {
        if (!GuiUtil.inside(mx, my, canvasX, canvasY, canvasW, canvasH)) return null;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            int half = halfSize(n);
            if (Math.abs(mx - screenX(n)) <= half && Math.abs(my - screenY(n)) <= half) return n;
        }
        return null;
    }

    private Quest questAt(int mx, int my) {
        Node n = nodeAt(nodes(), mx, my);
        return n != null ? n.quest : null;
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        tooltip = null;
        tooltipStack = null;
        drawDefaultBackground();
        fontRendererObj.drawStringWithShadow(I18n.format("waymark.gui.title"), MARGIN, MARGIN + 4, 0xFFFFFF);

        QuestBook book = ClientQuests.getBook();
        if (book == null || ClientQuests.getProgress() == null) {
            drawCenteredString(fontRendererObj, I18n.format("waymark.gui.loading"), width / 2, height / 2, 0xAAAAAA);
            return;
        }
        refresh(book);

        // While the quest window is open the map behind it must not react to the mouse.
        int hx = questView == null ? mx : -1;
        int hy = questView == null ? my : -1;
        drawChapterList(book, hx, hy);
        drawCanvas(hx, hy);

        if (questView != null) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            questView.draw(mx, my);
        }

        super.drawScreen(mx, my, partialTicks);

        if (tooltipStack != null) renderToolTip(tooltipStack, tooltipX, tooltipY);
        else if (tooltip != null) drawHoveringText(tooltip, tooltipX, tooltipY, fontRendererObj);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderHelper.disableStandardItemLighting();
    }

    private void drawChapterList(QuestBook book, int mx, int my) {
        GuiUtil.frame(listX, listY, LIST_WIDTH, listH, 0xC0101018, 0xFF404860);
        List<Chapter> chapters = book.getChapters();
        listScroll = Math.max(0, Math.min(listScroll, chapters.size() * ROW - (listH - 4)));

        GuiUtil.scissor(listX + 1, listY + 1, LIST_WIDTH - 2, listH - 2);
        boolean mouseInList = GuiUtil.inside(mx, my, listX, listY, LIST_WIDTH, listH);
        Chapter hoveredChapter = null;
        for (int i = 0; i < chapters.size(); i++) {
            int y = listY + 2 + i * ROW - listScroll;
            if (y + ROW < listY || y > listY + listH) continue;
            Chapter c = chapters.get(i);
            boolean selected = c == chapter;
            if (selected) GuiUtil.rect(listX + 1, y, LIST_WIDTH - 2, ROW, 0xFF2A3550);
            else if (mouseInList && GuiUtil.inside(mx, my, listX, y, LIST_WIDTH, ROW))
                GuiUtil.rect(listX + 1, y, LIST_WIDTH - 2, ROW, 0x30FFFFFF);

            GuiUtil.item(chapterIcon(c), listX + 3, y + 2);
            String title = c.getTitle()
                .isEmpty() ? c.getFilename() : GuiUtil.colorCodes(c.getTitle());
            fontRendererObj.drawString(
                GuiUtil.trim(fontRendererObj, title, LIST_WIDTH - 32),
                listX + 22,
                y + 6,
                selected ? 0xFFFFFF : 0xC0C0C0);
            if (hasClaimable(c)) GuiUtil.rect(listX + LIST_WIDTH - 8, y + 8, 4, 4, QuestState.CLAIMABLE.color);
            if (mouseInList && GuiUtil.inside(mx, my, listX, y, LIST_WIDTH, ROW)) hoveredChapter = c;
        }
        GuiUtil.endScissor();

        if (hoveredChapter != null) {
            List<String> lines = new ArrayList<>();
            lines.add(GuiUtil.colorCodes(hoveredChapter.getTitle()));
            for (String line : hoveredChapter.getDescription()) {
                for (Object wrapped : fontRendererObj.listFormattedStringToWidth("§7" + GuiUtil.colorCodes(line), 220))
                    lines.add((String) wrapped);
            }
            showTooltip(lines, mx, my);
        }
    }

    private static ItemStack chapterIcon(Chapter c) {
        if (c.getIcon() != null) return c.getIcon();
        for (Quest q : c.getQuests()) {
            ItemStack icon = q.getIcon();
            if (icon != null) return icon;
        }
        return null;
    }

    private static boolean hasClaimable(Chapter c) {
        for (Quest q : c.getQuests()) {
            if (QuestState.of(q) == QuestState.CLAIMABLE) return true;
        }
        return false;
    }

    private void drawCanvas(int mx, int my) {
        GuiUtil.frame(canvasX, canvasY, canvasW, canvasH, 0xC0080810, 0xFF404860);
        if (chapter == null) {
            drawCenteredString(
                fontRendererObj,
                I18n.format("waymark.gui.no_chapters"),
                canvasX + canvasW / 2,
                canvasY + canvasH / 2,
                0xAAAAAA);
            return;
        }

        GuiUtil.scissor(canvasX + 1, canvasY + 1, canvasW - 2, canvasH - 2);

        List<Node> nodes = nodes();
        Map<Long, Node> byQuest = new HashMap<>();
        for (Node n : nodes) byQuest.putIfAbsent(n.quest.getId(), n);

        // Lines join whatever is on this map, so links pull in connections to other chapters' quests.
        float lineWidth = (float) Math.max(1, 2 * zoom * GuiUtil.guiScale());
        for (Node n : nodes) {
            for (Quest dep : n.quest.getDependencies()) {
                if (n.quest.isLineHidden(dep)) continue;
                Node from = byQuest.get(dep.getId());
                if (from == null) continue;
                boolean done = from.state == QuestState.COMPLETED || from.state == QuestState.CLAIMABLE;
                GuiUtil.line(
                    screenX(from),
                    screenY(from),
                    screenX(n),
                    screenY(n),
                    lineWidth,
                    done ? 0xC055DD55 : 0xC0905050);
            }
        }

        Node hoveredNode = nodeAt(nodes, mx, my);
        Quest hovered = hoveredNode != null ? hoveredNode.quest : null;
        for (Node n : nodes) {
            Quest q = n.quest;
            QuestState state = n.state;
            int cx = (int) Math.round(screenX(n));
            int cy = (int) Math.round(screenY(n));
            int half = halfSize(n);
            if (cx + half < canvasX || cx - half > canvasX + canvasW
                || cy + half < canvasY
                || cy - half > canvasY + canvasH) continue;

            int border = state.color;
            if (state == QuestState.CLAIMABLE) {
                // Pulse so finished quests with unclaimed rewards stand out.
                float pulse = (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 200.0));
                border = 0xFF000000 | ((int) (0xFF * pulse) << 16) | ((int) (0xCC * pulse) << 8);
            }
            GuiUtil.frame(cx - half, cy - half, half * 2, half * 2, n == hoveredNode ? 0xFF303040 : 0xFF1A1A24, border);

            ItemStack icon = q.getIcon();
            if (icon != null) {
                double scale = (half * 2 - 4) / 16.0;
                GL11.glPushMatrix();
                GL11.glTranslated(cx, cy, 0);
                GL11.glScaled(scale, scale, 1);
                GuiUtil.item(icon, -8, -8);
                GL11.glPopMatrix();
            } else {
                drawCenteredString(fontRendererObj, "?", cx, cy - 4, 0xFFFFFF);
            }

            if (state == QuestState.LOCKED)
                GuiUtil.rect(cx - half + 1, cy - half + 1, half * 2 - 2, half * 2 - 2, 0xA0000000);
            if (state == QuestState.IN_PROGRESS) {
                int barW = (int) ((half * 2 - 2) * QuestState.fraction(q));
                GuiUtil.rect(cx - half + 1, cy + half - 3, barW, 2, QuestState.IN_PROGRESS.color);
            }
            // A link is marked with a small corner so players know the quest lives in another chapter.
            if (n.link) GuiUtil.rect(cx + half - 5, cy - half + 1, 4, 4, 0xFF55AAFF);
        }
        GuiUtil.endScissor();

        if (hovered != null) {
            List<String> lines = questTooltip(hovered);
            if (hoveredNode.link) lines.add(
                "§b" + I18n.format(
                    "waymark.gui.linked_from",
                    GuiUtil.colorCodes(
                        hovered.getChapter()
                            .getTitle())));
            showTooltip(lines, mx, my);
        }
    }

    private List<String> questTooltip(Quest q) {
        List<String> lines = new ArrayList<>();
        lines.add(GuiUtil.colorCodes(q.getDisplayTitle()));
        if (!q.getSubtitle()
            .isEmpty()) lines.add("§7" + GuiUtil.colorCodes(q.getSubtitle()));
        switch (QuestState.of(q)) {
            case LOCKED:
                lines.add("§8" + I18n.format("waymark.gui.locked"));
                break;
            case COMPLETED:
                lines.add("§a" + I18n.format("waymark.gui.completed"));
                break;
            case CLAIMABLE:
                lines.add("§e" + I18n.format("waymark.gui.claimable"));
                break;
            default:
                if (!q.getTasks()
                    .isEmpty()) lines.add("§9" + (int) (QuestState.fraction(q) * 100) + "%");
        }
        return lines;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        super.mouseClicked(mx, my, button);
        if (ClientQuests.getBook() == null) return;
        if (questView != null) {
            questView.mouseClicked(mx, my, button);
            return;
        }
        if (GuiUtil.inside(mx, my, listX, listY, LIST_WIDTH, listH)) {
            int index = (my - listY - 2 + listScroll) / ROW;
            List<Chapter> chapters = ClientQuests.getBook()
                .getChapters();
            if (index >= 0 && index < chapters.size() && chapters.get(index) != chapter) {
                selectChapter(chapters.get(index));
                playClick();
            }
            return;
        }
        if (GuiUtil.inside(mx, my, canvasX, canvasY, canvasW, canvasH)) {
            dragging = true;
            dragMoved = false;
            dragStartX = lastMouseX = mx;
            dragStartY = lastMouseY = my;
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long timeSinceClick) {
        if (!dragging) return;
        panX += mx - lastMouseX;
        panY += my - lastMouseY;
        lastMouseX = mx;
        lastMouseY = my;
        if (Math.abs(mx - dragStartX) + Math.abs(my - dragStartY) > 3) dragMoved = true;
    }

    @Override
    protected void mouseMovedOrUp(int mx, int my, int which) {
        super.mouseMovedOrUp(mx, my, which);
        if (which == -1 || !dragging) return;
        dragging = false;
        if (!dragMoved && which == 0) {
            Quest quest = questAt(mx, my);
            if (quest != null) {
                openQuest(quest);
                playClick();
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (questView != null) {
            questView.scroll(wheel > 0 ? -1 : 1);
        } else if (GuiUtil.inside(mx, my, listX, listY, LIST_WIDTH, listH)) {
            listScroll += wheel > 0 ? -ROW : ROW;
        } else if (chapter != null && GuiUtil.inside(mx, my, canvasX, canvasY, canvasW, canvasH)) {
            zoomAt(mx, my, wheel > 0 ? 1.2 : 1 / 1.2);
        }
    }

    /** Zooms while keeping the point under the cursor in place. */
    private void zoomAt(int mx, int my, double factor) {
        double next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        double cx = canvasX + canvasW / 2.0;
        double cy = canvasY + canvasH / 2.0;
        double wx = (mx - cx - panX) / zoom;
        double wy = (my - cy - panY) / zoom;
        zoom = next;
        panX = mx - cx - wx * zoom;
        panY = my - cy - wy * zoom;
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE && questView != null) {
            closeQuest();
            return;
        }
        if (key == ClientEvents.OPEN_BOOK.getKeyCode() && questView == null) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    public void onGuiClosed() {
        saveView();
    }
}
