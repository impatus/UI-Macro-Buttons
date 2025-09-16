package com.impatus.uibuttons;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class EditorScreen extends Screen {

    private EditBox labelBox;
    private EditBox cmdBox;
    private EditBox widthBox, heightBox;
    private EditBox iconValueBox;

    // persist selection across re-init after returning from picker
    private String pendingIconValue = null;

    private final Integer editIndex; // null => creating

    private static final int GUI_W = 176, GUI_H = 166;
    private static final int PADX = 6, PADY = 10, SP = 4;

    public EditorScreen() { super(Component.literal("Create Macro")); this.editIndex = null; }
    public EditorScreen(int index) { super(Component.literal("Edit Macro")); this.editIndex = index; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y  = this.height / 2 - 90;
        int w  = 200;

        ButtonsConfig.ButtonSpec existing = null;
        if (editIndex != null && editIndex >= 0 && editIndex < ButtonsConfig.buttons.size()) {
            existing = ButtonsConfig.buttons.get(editIndex);
        }

        if (pendingIconValue == null) pendingIconValue = (existing != null ? existing.iconValue : "");

        // Label (now optional)
        addRenderableWidget(Button.builder(Component.literal("Label (optional):"), b -> {}).bounds(cx - w/2 - 110, y, 100, 20).build());
        labelBox = new EditBox(this.font, cx - w/2, y, w, 20, Component.literal("Label"));
        labelBox.setMaxLength(64);
        labelBox.setValue(existing != null ? (existing.label == null ? "" : existing.label) : ""); // default empty
        addRenderableWidget(labelBox);
        y += 24;

        // Command
        addRenderableWidget(Button.builder(Component.literal("Command:"), b -> {}).bounds(cx - w/2 - 110, y, 100, 20).build());
        cmdBox = new EditBox(this.font, cx - w/2, y, w, 20, Component.literal("Command"));
        cmdBox.setValue(existing != null ? existing.command : "/help");
        addRenderableWidget(cmdBox);
        y += 24;

        // Size
        addRenderableWidget(Button.builder(Component.literal("Width:"), b -> {}).bounds(cx - w/2 - 110, y, 100, 20).build());
        widthBox = new EditBox(this.font, cx - w/2, y, 96, 20, Component.literal("Width"));
        widthBox.setFilter(EditorScreen::isDigits);
        widthBox.setValue(String.valueOf(existing != null ? Math.max(20, existing.width) : 20));
        addRenderableWidget(widthBox);

        addRenderableWidget(Button.builder(Component.literal("Height:"), b -> {}).bounds(cx + w/2 - 96, y, 96, 20).build());
        heightBox = new EditBox(this.font, cx + w/2, y, 96, 20, Component.literal("Height"));
        heightBox.setFilter(EditorScreen::isDigits);
        heightBox.setValue(String.valueOf(existing != null ? Math.max(20, existing.height) : 20));
        addRenderableWidget(heightBox);
        y += 24;

        // Icon value + picker
        addRenderableWidget(Button.builder(Component.literal("Icon Value:"), b -> {}).bounds(cx - w/2 - 110, y, 100, 20).build());
        iconValueBox = new EditBox(this.font, cx - w/2, y, w, 20, Component.literal("Icon Value"));
        iconValueBox.setMaxLength(200);
        iconValueBox.setValue(pendingIconValue);
        addRenderableWidget(iconValueBox);

        addRenderableWidget(Button.builder(Component.literal("Pick…"), b -> {
            Minecraft.getInstance().setScreen(new IconPickerScreen(
                    this,
                    ButtonsConfig.IconKind.ITEM, // initial tab; saving auto-detects anyway
                    iconValueBox.getValue(),
                    (kind, value) -> {
                        pendingIconValue = value;
                        iconValueBox.setValue(value);
                    }
            ));
        }).bounds(cx + w/2 + 6, y, 64, 20).build());
        y += 24;

        // Open icons folder
        addRenderableWidget(Button.builder(Component.literal("Open icons folder"), b -> {
            try { Util.getPlatform().openFile(ButtonsConfig.IMG_DIR.toFile()); } catch (Exception ignored) {}
        }).bounds(cx - w/2, y, w, 20).build());
        y += 24;

        // Save / Cancel / Delete
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> onSave())
                .bounds(cx - 90, y, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onCancel())
                .bounds(cx + 10, y, 80, 20).build());
        y += 24;

        if (existing != null) {
            addRenderableWidget(Button.builder(Component.literal("Delete"), b -> onDelete())
                    .bounds(cx - 40, y, 80, 20).build());
        }

        setInitialFocus(labelBox);
    }

    private static boolean isDigits(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private void onSave() {
        String label = labelBox.getValue(); // allow empty
        String cmd   = cmdBox.getValue().trim();
        if (cmd.isEmpty()) cmd = "/help";

        int w = parseOr(widthBox.getValue(), 20);
        int h = parseOr(heightBox.getValue(), 20);
        if (w < 20) w = 20;
        if (h < 20) h = 20;

        String iconVal = (pendingIconValue != null) ? pendingIconValue.trim() : iconValueBox.getValue().trim();

        if (editIndex != null && editIndex >= 0 && editIndex < ButtonsConfig.buttons.size()) {
            ButtonsConfig.ButtonSpec spec = ButtonsConfig.buttons.get(editIndex);
            spec.label = label; // may be empty
            spec.command = cmd;
            spec.anchor = ButtonsConfig.Anchor.RIGHT; // default
            spec.scope  = ButtonsConfig.Scope.INVENTORY_ONLY; // default
            spec.width  = w;
            spec.height = h;
            spec.iconValue = iconVal;
        } else {
            ButtonsConfig.ButtonSpec spec = new ButtonsConfig.ButtonSpec();
            spec.label = label; // may be empty
            spec.command = cmd;
            spec.anchor = ButtonsConfig.Anchor.RIGHT;
            spec.scope  = ButtonsConfig.Scope.INVENTORY_ONLY;
            spec.width  = w;
            spec.height = h;
            spec.iconValue = iconVal;

            // auto-place on right edge in a tidy column
            int indexSameAnchor = countWithAnchor(spec.anchor);
            int rowsPerCol = Math.max(1, (GUI_H - (PADY * 2)) / (h + SP));
            int col = indexSameAnchor / rowsPerCol;
            int row = indexSameAnchor % rowsPerCol;
            spec.xOffset = PADX + col * (w + SP);
            spec.yOffset = PADY + row * (h + SP);

            ButtonsConfig.buttons.add(spec);
        }

        ButtonsConfig.save();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
        else onClose();
    }

    private void onDelete() {
        if (editIndex != null && editIndex >= 0 && editIndex < ButtonsConfig.buttons.size()) {
            ButtonsConfig.buttons.remove((int)editIndex);
            ButtonsConfig.save();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
        else onClose();
    }

    private int countWithAnchor(ButtonsConfig.Anchor a) {
        int c = 0;
        for (ButtonsConfig.ButtonSpec s : ButtonsConfig.buttons) if (s.anchor == a) c++;
        return c;
    }

    private int parseOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private void onCancel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
        else onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTicks);

        int cx = this.width / 2;

        // Tip ABOVE the title so it never overlaps the UI
        g.drawCenteredString(this.font,
                "Tip: Label is optional (shown as tooltip). Use Pick… for items/PNGs. Save to apply.",
                cx, this.height / 2 - 140, 0xAAAAAA);

        // Title below the tip
        g.drawCenteredString(this.font,
                (editIndex == null ? "Create Macro" : "Edit Macro"),
                cx, this.height / 2 - 120, 0xFFFFFF);
    }
}
