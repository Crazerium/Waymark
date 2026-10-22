package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.crazerium.waymark.quest.EditorField;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Edits one task or reward. The form is built from the type's {@link EditorField}s, so every type, including ones
 * from other mods, gets an editor without writing one.
 */
@SideOnly(Side.CLIENT)
public class ObjectEditScreen extends GuiScreen {

    private static final int ROW = 22;
    private static final int LABEL_W = 100;
    private static final int SLOT = 18;

    private final GuiScreen parent;
    private final String heading;
    private final List<EditorField> fields;
    private final JsonObject original;
    private final Consumer<JsonObject> onSave;

    private final Map<String, GuiTextField> textFields = new HashMap<>();
    private final Map<String, Boolean> bools = new HashMap<>();
    private final Map<String, JsonObject> items = new HashMap<>();
    private final Map<String, List<JsonObject>> itemLists = new HashMap<>();
    /** Text values kept across initGui, which recreates the fields on resize. */
    private final Map<String, String> texts = new HashMap<>();

    private ItemPicker picker;
    private ItemStack tooltipStack;
    private int px, py, pw, ph;

    /** {@code onSave} gets the edited JSON; it is not called on cancel. */
    public ObjectEditScreen(GuiScreen parent, String heading, List<EditorField> fields, JsonObject json,
        Consumer<JsonObject> onSave) {
        this.parent = parent;
        this.heading = heading;
        this.fields = fields;
        this.original = json;
        this.onSave = onSave;
        for (EditorField f : fields) {
            JsonElement v = json.get(f.key);
            switch (f.kind) {
                case BOOLEAN:
                    bools.put(
                        f.key,
                        v != null && v.isJsonPrimitive() ? v.getAsBoolean() : f.defaultValue.getAsBoolean());
                    break;
                case ITEM:
                    items.put(f.key, v != null && v.isJsonObject() ? v.getAsJsonObject() : null);
                    break;
                case ITEM_LIST: {
                    List<JsonObject> list = new ArrayList<>();
                    if (v != null && v.isJsonArray())
                        for (JsonElement e : v.getAsJsonArray()) if (e.isJsonObject()) list.add(e.getAsJsonObject());
                    itemLists.put(f.key, list);
                    break;
                }
                default:
                    texts.put(f.key, v != null && v.isJsonPrimitive() ? v.getAsString() : f.defaultValue.getAsString());
            }
        }
    }

    static String label(String key) {
        String k = "waymark.field." + key;
        String s = I18n.format(k);
        return s.equals(k) ? key : s;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        pw = Math.min(360, width - 20);
        ph = Math.min(fields.size() * ROW + 60, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        for (GuiTextField field : textFields.values()) texts.put(keyOf(field), field.getText());
        textFields.clear();
        int y = py + 24;
        for (EditorField f : fields) {
            if (f.kind == EditorField.Kind.TEXT || f.kind == EditorField.Kind.INTEGER) {
                GuiTextField field = new GuiTextField(fontRendererObj, px + LABEL_W, y + 3, pw - LABEL_W - 10, 14);
                field.setMaxStringLength(2000);
                field.setText(texts.get(f.key));
                textFields.put(f.key, field);
            }
            y += ROW;
        }
    }

    private String keyOf(GuiTextField field) {
        for (Map.Entry<String, GuiTextField> e : textFields.entrySet()) if (e.getValue() == field) return e.getKey();
        return "";
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : textFields.values()) field.updateCursorCounter();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        tooltipStack = null;
        drawDefaultBackground();
        int hx = picker == null ? mx : -1, hy = picker == null ? my : -1;
        GuiUtil.frame(px, py, pw, ph, 0xF0101018, 0xFF8090B0);
        fontRendererObj.drawStringWithShadow(heading, px + 8, py + 8, 0xFFFFFF);

        int y = py + 24;
        for (EditorField f : fields) {
            fontRendererObj.drawString(label(f.key), px + 8, y + 6, 0xC0C0C0);
            int cx = px + LABEL_W;
            switch (f.kind) {
                case BOOLEAN:
                    GuiUtil.button(
                        fontRendererObj,
                        cx,
                        y + 2,
                        60,
                        16,
                        I18n.format(bools.get(f.key) ? "waymark.edit.yes" : "waymark.edit.no"),
                        hx,
                        hy);
                    break;
                case ITEM:
                    drawSlot(items.get(f.key), cx, y + 1, hx, hy, false);
                    break;
                case ITEM_LIST: {
                    List<JsonObject> list = itemLists.get(f.key);
                    int max = maxSlots();
                    for (int i = 0; i < list.size() && i < max; i++)
                        drawSlot(list.get(i), cx + i * SLOT, y + 1, hx, hy, true);
                    if (list.size() < max) {
                        int sx = cx + list.size() * SLOT;
                        GuiUtil.button(fontRendererObj, sx, y + 1, SLOT - 1, SLOT - 1, "+", hx, hy);
                    }
                    break;
                }
                default:
                    textFields.get(f.key)
                        .drawTextBox();
            }
            y += ROW;
        }

        int by = py + ph - 22;
        GuiUtil.button(fontRendererObj, px + pw - 140, by, 64, 16, I18n.format("waymark.edit.save"), hx, hy);
        GuiUtil.button(fontRendererObj, px + pw - 72, by, 64, 16, I18n.format("gui.cancel"), hx, hy);
        if (picker == null) fontRendererObj.drawString(I18n.format("waymark.edit.slot_help"), px + 8, by + 4, 0x808080);

        if (picker != null) {
            picker.draw(fontRendererObj, width, height, mx, my);
            tooltipStack = picker.hovered();
        }
        super.drawScreen(mx, my, partialTicks);
        if (tooltipStack != null) renderToolTip(tooltipStack, mx, my);
    }

    private int maxSlots() {
        return Math.max(1, (pw - LABEL_W - 10) / SLOT);
    }

    private void drawSlot(JsonObject json, int x, int y, int mx, int my, boolean showCount) {
        boolean hover = GuiUtil.inside(mx, my, x, y, SLOT - 1, SLOT - 1);
        GuiUtil.frame(x, y, SLOT - 1, SLOT - 1, hover ? 0xFF404058 : 0xFF202030, 0xFF404860);
        ItemStack stack = JsonUtil.readStack(json);
        if (stack == null) return;
        GuiUtil.item(stack, x, y);
        if (showCount && stack.stackSize > 1) {
            String n = String.valueOf(stack.stackSize);
            fontRendererObj.drawStringWithShadow(n, x + 17 - fontRendererObj.getStringWidth(n), y + 9, 0xFFFFFF);
        }
        if (hover) tooltipStack = stack;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (picker != null) {
            if (picker.mouseClicked(mx, my)) picker = null;
            return;
        }
        for (GuiTextField field : textFields.values()) field.mouseClicked(mx, my, button);

        int y = py + 24;
        for (EditorField f : fields) {
            int cx = px + LABEL_W;
            switch (f.kind) {
                case BOOLEAN:
                    if (GuiUtil.inside(mx, my, cx, y + 2, 60, 16)) bools.put(f.key, !bools.get(f.key));
                    break;
                case ITEM:
                    if (GuiUtil.inside(mx, my, cx, y + 1, SLOT, SLOT)) {
                        if (button == 1) items.put(f.key, null);
                        else picker = new ItemPicker(stack -> items.put(f.key, single(stack)), true);
                    }
                    break;
                case ITEM_LIST: {
                    List<JsonObject> list = itemLists.get(f.key);
                    int index = (mx - cx) / SLOT;
                    if (!GuiUtil.inside(mx, my, cx, y + 1, maxSlots() * SLOT, SLOT) || index > list.size()) break;
                    if (index == list.size()) {
                        picker = new ItemPicker(
                            stack -> { if (stack != null) list.add(JsonUtil.writeStack(stack)); },
                            false);
                    } else if (button == 1) {
                        list.remove(index);
                    } else {
                        picker = new ItemPicker(stack -> {
                            if (stack == null) list.remove(index);
                            else list.set(index, JsonUtil.writeStack(stack));
                        }, true);
                    }
                    break;
                }
                default:
            }
            y += ROW;
        }

        int by = py + ph - 22;
        if (GuiUtil.inside(mx, my, px + pw - 140, by, 64, 16)) save();
        else if (GuiUtil.inside(mx, my, px + pw - 72, by, 64, 16)) mc.displayGuiScreen(parent);
    }

    private static JsonObject single(ItemStack stack) {
        if (stack == null) return null;
        stack.stackSize = 1;
        return JsonUtil.writeStack(stack);
    }

    /** The mouse wheel over a slot in an item list changes that stack's count (Shift: by 10). */
    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || picker != null) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int y = py + 24;
        for (EditorField f : fields) {
            if (f.kind == EditorField.Kind.ITEM_LIST && my >= y + 1 && my < y + 1 + SLOT) {
                List<JsonObject> list = itemLists.get(f.key);
                int index = (mx - px - LABEL_W) / SLOT;
                if (mx >= px + LABEL_W && index < list.size()) {
                    JsonObject item = list.get(index);
                    int step = (wheel > 0 ? 1 : -1) * (isShiftKeyDown() ? 10 : 1);
                    int count = Math.max(1, JsonUtil.getInt(item, "count", 1) + step);
                    if (count == 1) item.remove("count");
                    else item.addProperty("count", count);
                }
            }
            y += ROW;
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (picker != null) picker = null;
            else mc.displayGuiScreen(parent);
            return;
        }
        for (EditorField f : fields) {
            GuiTextField field = textFields.get(f.key);
            if (field == null || !field.isFocused()) continue;
            // Number fields only take digits and a minus sign (control keys still work).
            if (f.kind == EditorField.Kind.INTEGER && c >= ' ' && !Character.isDigit(c) && c != '-') return;
            field.textboxKeyTyped(c, key);
        }
    }

    private void save() {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> e : original.entrySet()) result.add(e.getKey(), e.getValue());
        for (EditorField f : fields) {
            result.remove(f.key);
            switch (f.kind) {
                case BOOLEAN:
                    if (bools.get(f.key) != f.defaultValue.getAsBoolean()) result.addProperty(f.key, bools.get(f.key));
                    break;
                case ITEM:
                    if (items.get(f.key) != null) result.add(f.key, items.get(f.key));
                    break;
                case ITEM_LIST: {
                    List<JsonObject> list = itemLists.get(f.key);
                    if (list.isEmpty()) break;
                    JsonArray array = new JsonArray();
                    for (JsonObject item : list) array.add(item);
                    result.add(f.key, array);
                    break;
                }
                case INTEGER: {
                    long value;
                    try {
                        value = Long.parseLong(
                            textFields.get(f.key)
                                .getText()
                                .trim());
                    } catch (NumberFormatException ex) {
                        value = f.defaultValue.getAsLong();
                    }
                    if (value != f.defaultValue.getAsLong()) result.add(f.key, new JsonPrimitive(value));
                    break;
                }
                default: {
                    String text = textFields.get(f.key)
                        .getText();
                    if (!text.equals(f.defaultValue.getAsString())) result.addProperty(f.key, text);
                }
            }
        }
        onSave.accept(result);
        mc.displayGuiScreen(parent);
    }
}
