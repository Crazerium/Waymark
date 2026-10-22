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
import com.crazerium.waymark.quest.BookEdits;
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

    /** What a held left mouse button on the map is doing. */
    private enum Drag {
        NONE,
        PAN,
        /** Edit mode: moving a quest or link. */
        MOVE,
        /** Edit mode: Ctrl+drag from a quest to another to toggle a dependency. */
        CONNECT,
        /** Edit mode: dragging a chapter in the list to a new position. */
        CHAPTER
    }

    private static boolean editMode;
    private Drag drag = Drag.NONE;
    private boolean dragMoved;
    private Node dragNode;
    private int dragStartX, dragStartY, lastMouseX, lastMouseY;
    private ContextMenu menu;
    /** Edit mode: index of the chapter being dragged in the list. */
    private int dragChapter;
    /** Edit mode: the quest copied with "Copy as link", pasted as a link into another chapter. */
    private static long linkClipboard;
    private int editButtonX, editButtonW;

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

    static boolean isEditing() {
        return editMode && ClientQuests.canEdit();
    }

    void editQuest(Quest quest) {
        questView = null;
        mc.displayGuiScreen(new QuestEditScreen(this, quest));
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
        // The chapter may also have been deleted from this same book by an edit.
        if (chapter != null && chapter.getBook() == book && book.get(chapter.getId()) == chapter) return;
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
        /** Index in the chapter's link list, or -1 for a quest. */
        final int linkIndex;
        final QuestState state;

        Node(Quest quest, double x, double y, double size, int linkIndex) {
            this.quest = quest;
            this.x = x;
            this.y = y;
            this.size = size;
            this.link = linkIndex >= 0;
            this.linkIndex = linkIndex;
            this.state = QuestState.of(quest);
        }

        boolean sameAs(Node other) {
            return other != null && other.quest == quest && other.linkIndex == linkIndex;
        }
    }

    /**
     * Visible nodes of the open chapter, quests first and links after. In edit mode hidden quests are included too,
     * and the node being dragged is placed under the mouse.
     */
    private List<Node> nodes() {
        List<Node> nodes = new ArrayList<>();
        if (chapter == null) return nodes;
        for (Quest q : chapter.getQuests()) addNode(nodes, new Node(q, q.getX(), q.getY(), q.getSize(), -1));
        List<QuestLink> links = chapter.getLinks();
        for (int i = 0; i < links.size(); i++) {
            QuestLink link = links.get(i);
            Quest q = link.getQuest();
            if (q != null) addNode(nodes, new Node(q, link.getX(), link.getY(), link.getSize(), i));
        }
        return nodes;
    }

    private void addNode(List<Node> nodes, Node node) {
        if (node.state == QuestState.HIDDEN && !editMode) return;
        if (drag == Drag.MOVE && dragMoved && node.sameAs(dragNode))
            node = new Node(node.quest, snap(worldX(lastMouseX)), snap(worldY(lastMouseY)), node.size, node.linkIndex);
        nodes.add(node);
    }

    private double worldX(int mx) {
        return (mx - canvasX - canvasW / 2.0 - panX) / (GRID * zoom);
    }

    private double worldY(int my) {
        return (my - canvasY - canvasH / 2.0 - panY) / (GRID * zoom);
    }

    /** Positions snap to half a grid cell; holding Shift places freely. */
    private static double snap(double v) {
        if (isShiftKeyDown()) return Math.round(v * 100) / 100.0;
        return Math.round(v * 2) / 2.0;
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
        // An edit from the server (maybe another operator's) may have deleted the open quest.
        if (questView != null && book.getQuest(questView.quest.getId()) != questView.quest) questView = null;
        if (!ClientQuests.canEdit()) editMode = false;

        // While the quest window or a menu is open the map behind it must not react to the mouse.
        boolean blocked = questView != null || menu != null;
        int hx = blocked ? -1 : mx;
        int hy = blocked ? -1 : my;
        drawEditHeader(hx, hy);
        drawChapterList(book, hx, hy);
        drawCanvas(hx, hy);

        if (questView != null) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            questView.draw(mx, my);
        }
        if (menu != null) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            menu.draw(fontRendererObj, mx, my);
        }

        super.drawScreen(mx, my, partialTicks);

        if (tooltipStack != null) renderToolTip(tooltipStack, tooltipX, tooltipY);
        else if (tooltip != null) drawHoveringText(tooltip, tooltipX, tooltipY, fontRendererObj);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderHelper.disableStandardItemLighting();
    }

    /** The edit mode toggle (operators only) and, while editing, a line of help. */
    private void drawEditHeader(int mx, int my) {
        if (!ClientQuests.canEdit()) return;
        String label = I18n.format(editMode ? "waymark.edit.on" : "waymark.edit.off");
        editButtonW = fontRendererObj.getStringWidth(label) + 12;
        editButtonX = width - MARGIN - editButtonW;
        GuiUtil.button(fontRendererObj, editButtonX, MARGIN, editButtonW, 14, label, mx, my);
        if (editMode) {
            String text = I18n.format("waymark.edit.help");
            String help = GuiUtil.trim(fontRendererObj, text, editButtonX - canvasX - 8);
            fontRendererObj.drawString(help, canvasX, MARGIN + 4, 0xFFCC66);
        }
    }

    private void drawChapterList(QuestBook book, int mx, int my) {
        GuiUtil.frame(listX, listY, LIST_WIDTH, listH, 0xC0101018, 0xFF404860);
        List<Chapter> chapters = book.getChapters();
        boolean draggingToList = dragMoved && (drag == Drag.CHAPTER || drag == Drag.MOVE);
        // Holding a dragged chapter or quest near the list's top or bottom edge scrolls it.
        if (draggingToList && mx >= listX && mx < listX + LIST_WIDTH) {
            if (my < listY + 16) listScroll -= 4;
            else if (my > listY + listH - 16) listScroll += 4;
        }
        listScroll = Math.max(0, Math.min(listScroll, chapters.size() * ROW - (listH - 4)));

        GuiUtil.scissor(listX + 1, listY + 1, LIST_WIDTH - 2, listH - 2);
        boolean mouseInList = GuiUtil.inside(mx, my, listX, listY, LIST_WIDTH, listH);
        boolean draggingChapter = drag == Drag.CHAPTER && dragMoved && dragChapter < chapters.size();
        Chapter dragged = draggingChapter ? chapters.get(dragChapter) : null;
        // While a chapter is dragged the list is drawn in the order it would have if dropped now.
        List<Chapter> shown = chapters;
        if (draggingChapter) {
            shown = new ArrayList<>(chapters);
            shown.remove(dragged);
            shown.add(dropTarget(my, chapters.size()), dragged);
        }
        Chapter hoveredChapter = null;
        for (int i = 0; i < shown.size(); i++) {
            int y = listY + 2 + i * ROW - listScroll;
            if (y + ROW < listY || y > listY + listH) continue;
            Chapter c = shown.get(i);
            boolean selected = c == chapter;
            if (c == dragged) GuiUtil.frame(listX + 1, y, LIST_WIDTH - 2, ROW, 0x50FFCC00, 0xFFFFCC00);
            else if (selected) GuiUtil.rect(listX + 1, y, LIST_WIDTH - 2, ROW, 0xFF2A3550);
            else if (!draggingChapter && mouseInList && GuiUtil.inside(mx, my, listX, y, LIST_WIDTH, ROW))
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
            boolean rowHovered = mouseInList && GuiUtil.inside(mx, my, listX, y, LIST_WIDTH, ROW);
            // A quest being dragged over a chapter shows where it will go.
            if (drag == Drag.MOVE && dragMoved && rowHovered && c != chapter)
                GuiUtil.frame(listX + 1, y, LIST_WIDTH - 2, ROW, 0x30FFCC00, 0xFFFFCC00);
            if (rowHovered && drag == Drag.NONE) hoveredChapter = c;
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

            // Hidden quests only show in edit mode; purple marks them as invisible to players.
            int border = state == QuestState.HIDDEN ? 0xFF9050C0 : state.color;
            if (n.sameAs(dragNode) && drag != Drag.NONE) border = 0xFFFFFFFF;
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

            if (state == QuestState.LOCKED || state == QuestState.HIDDEN)
                GuiUtil.rect(cx - half + 1, cy - half + 1, half * 2 - 2, half * 2 - 2, 0xA0000000);
            if (state == QuestState.IN_PROGRESS) {
                int barW = (int) ((half * 2 - 2) * QuestState.fraction(q));
                GuiUtil.rect(cx - half + 1, cy + half - 3, barW, 2, QuestState.IN_PROGRESS.color);
            }
            // A link is marked with a small corner so players know the quest lives in another chapter.
            if (n.link) GuiUtil.rect(cx + half - 5, cy - half + 1, 4, 4, 0xFF55AAFF);
        }
        if (drag == Drag.CONNECT && dragNode != null) {
            GuiUtil.line(screenX(dragNode), screenY(dragNode), lastMouseX, lastMouseY, lineWidth, 0xFFFFCC00);
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
        if (menu != null) {
            if (!menu.mouseClicked(mx, my)) menu = null;
            else playClick();
            return;
        }
        if (questView != null) {
            questView.mouseClicked(mx, my, button);
            return;
        }
        if (ClientQuests.canEdit() && GuiUtil.inside(mx, my, editButtonX, MARGIN, editButtonW, 14)) {
            editMode = !editMode;
            playClick();
            return;
        }
        if (GuiUtil.inside(mx, my, listX, listY, LIST_WIDTH, listH)) {
            int index = (my - listY - 2 + listScroll) / ROW;
            List<Chapter> chapters = ClientQuests.getBook()
                .getChapters();
            Chapter clicked = index >= 0 && index < chapters.size() ? chapters.get(index) : null;
            if (button == 1 && editMode) {
                openChapterMenu(clicked, index, chapters.size(), mx, my);
                return;
            }
            if (button == 0 && editMode && clicked != null) {
                // Selecting happens on release, so the same press can also start dragging the chapter.
                drag = Drag.CHAPTER;
                dragChapter = index;
                dragMoved = false;
                dragStartX = lastMouseX = mx;
                dragStartY = lastMouseY = my;
                return;
            }
            if (clicked != null && clicked != chapter) {
                selectChapter(chapters.get(index));
                playClick();
            }
            return;
        }
        if (chapter == null || !GuiUtil.inside(mx, my, canvasX, canvasY, canvasW, canvasH)) return;
        Node node = nodeAt(nodes(), mx, my);
        if (button == 1 && editMode) {
            openEditMenu(node, mx, my);
            return;
        }
        if (button != 0) return;
        if (editMode && node != null) drag = isCtrlKeyDown() ? Drag.CONNECT : Drag.MOVE;
        else drag = Drag.PAN;
        dragNode = node;
        dragMoved = false;
        dragStartX = lastMouseX = mx;
        dragStartY = lastMouseY = my;
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long timeSinceClick) {
        if (drag == Drag.NONE) return;
        if (drag == Drag.PAN) {
            panX += mx - lastMouseX;
            panY += my - lastMouseY;
        }
        lastMouseX = mx;
        lastMouseY = my;
        if (Math.abs(mx - dragStartX) + Math.abs(my - dragStartY) > 3) dragMoved = true;
    }

    @Override
    protected void mouseMovedOrUp(int mx, int my, int which) {
        super.mouseMovedOrUp(mx, my, which);
        if (which != 0 || drag == Drag.NONE) return;
        Drag finished = drag;
        Node from = dragNode;
        drag = Drag.NONE;
        dragNode = null;
        List<Chapter> chapters = ClientQuests.getBook()
            .getChapters();

        if (finished == Drag.CHAPTER) {
            if (dragChapter >= chapters.size()) return;
            Chapter dragged = chapters.get(dragChapter);
            if (!dragMoved) {
                if (dragged != chapter) {
                    selectChapter(dragged);
                    playClick();
                }
                return;
            }
            int target = dropTarget(my, chapters.size());
            if (target != dragChapter) ClientQuests.sendEdit(BookEdits.moveChapter(dragged, target));
            return;
        }

        if (finished == Drag.MOVE && dragMoved && GuiUtil.inside(mx, my, listX, listY, LIST_WIDTH, listH)) {
            // Dropped on a chapter: move the quest there, or with Alt leave it and add a link to it there.
            Chapter target = chapterAt(my, chapters);
            if (target == null || target == chapter) return;
            if (isAltKeyDown()) {
                if (target != from.quest.getChapter())
                    ClientQuests.sendEdit(BookEdits.addLink(target, from.quest, from.quest.getX(), from.quest.getY()));
            } else if (!from.link) {
                ClientQuests.sendEdit(BookEdits.moveQuestToChapter(from.quest, target));
            }
            return;
        }

        if (!dragMoved) {
            // A click without dragging opens the quest, in edit mode too.
            Quest quest = questAt(mx, my);
            if (quest != null) {
                openQuest(quest);
                playClick();
            }
            return;
        }
        if (finished == Drag.MOVE) {
            double x = snap(worldX(mx)), y = snap(worldY(my));
            ClientQuests.sendEdit(
                from.link ? BookEdits.moveLink(chapter, from.linkIndex, x, y) : BookEdits.moveQuest(from.quest, x, y));
        } else if (finished == Drag.CONNECT) {
            Node to = nodeAt(nodes(), mx, my);
            // Dragging from A to B makes B require A (or removes that requirement).
            if (to != null && to.quest != from.quest)
                ClientQuests.sendEdit(BookEdits.toggleDependency(to.quest, from.quest));
        }
    }

    private static boolean isAltKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    private Chapter chapterAt(int my, List<Chapter> chapters) {
        int index = (my - listY - 2 + listScroll) / ROW;
        return my >= listY + 2 && index < chapters.size() ? chapters.get(index) : null;
    }

    /**
     * Where the dragged chapter ends up if dropped now: the row under the mouse, counted in the list without the
     * dragged chapter, so the preview and the drop always agree.
     */
    private int dropTarget(int my, int count) {
        int index = (my - listY - 2 + listScroll) / ROW;
        return Math.max(0, Math.min(count - 1, index));
    }

    private void openChapterMenu(Chapter clicked, int index, int count, int mx, int my) {
        menu = new ContextMenu(mx, my);
        menu.add(
            I18n.format("waymark.edit.new_chapter"),
            () -> ClientQuests.sendEdit(BookEdits.createChapter(I18n.format("waymark.edit.new_chapter_title"))));
        if (clicked == null) return;
        menu.add(
            I18n.format("waymark.edit.edit_chapter"),
            () -> mc.displayGuiScreen(new ChapterEditScreen(this, clicked)));
        menu.addDangerous(
            I18n.format("waymark.edit.delete_chapter"),
            I18n.format("waymark.edit.confirm"),
            () -> ClientQuests.sendEdit(BookEdits.deleteChapter(clicked)));
    }

    private void openEditMenu(Node node, int mx, int my) {
        menu = new ContextMenu(mx, my);
        if (node == null) {
            double x = snap(worldX(mx)), y = snap(worldY(my));
            menu.add(
                I18n.format("waymark.edit.new_quest"),
                () -> ClientQuests.sendEdit(BookEdits.createQuest(chapter, x, y)));
            Quest copied = ClientQuests.getBook()
                .getQuest(linkClipboard);
            if (copied != null && copied.getChapter() != chapter) menu.add(
                I18n.format("waymark.edit.paste_link", GuiUtil.colorCodes(copied.getDisplayTitle())),
                () -> ClientQuests.sendEdit(BookEdits.addLink(chapter, copied, x, y)));
        } else if (node.link) {
            int index = node.linkIndex;
            menu.add(
                I18n.format("waymark.edit.remove_link"),
                () -> ClientQuests.sendEdit(BookEdits.deleteLink(chapter, index)));
        } else {
            Quest quest = node.quest;
            menu.add(I18n.format("waymark.edit.edit_quest"), () -> editQuest(quest));
            menu.add(I18n.format("waymark.edit.copy_link"), () -> linkClipboard = quest.getId());
            menu.addDangerous(
                I18n.format("waymark.edit.delete_quest"),
                I18n.format("waymark.edit.confirm"),
                () -> ClientQuests.sendEdit(BookEdits.deleteQuest(quest)));
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
        if (key == Keyboard.KEY_ESCAPE && menu != null) {
            menu = null;
            return;
        }
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
