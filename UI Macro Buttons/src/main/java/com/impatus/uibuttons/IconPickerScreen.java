package com.impatus.uibuttons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class IconPickerScreen extends Screen {

    private final Screen parent;
    private final BiConsumer<ButtonsConfig.IconKind, String> onPick;

    private ButtonsConfig.IconKind kindInit;
    private String valueInit;

    private CycleButton<ButtonsConfig.IconKind> sourceBtn;
    private EditBox searchBox;

    // Grid config
    private static final int CELL = 20;
    private static final int PAD  = 6;
    private int cols = 12;
    private int rows = 6;
    private int pageSize = cols * rows;

    // Grid placement
    private int gridX, gridY, gridW, gridH;

    // Data
    private final List<String> all = new ArrayList<>();
    private List<String> filtered = new ArrayList<>();
    private int page = 0;
    private String selectedValue = null;

    // Controls
    private Button prevBtn, nextBtn, useBtn, cancelBtn;

    public IconPickerScreen(Screen parent, ButtonsConfig.IconKind currentKind, String currentValue,
                            BiConsumer<ButtonsConfig.IconKind, String> onPick) {
        super(Component.literal("Pick Icon"));
        this.parent = parent;
        this.kindInit = (currentKind == null || currentKind == ButtonsConfig.IconKind.NONE)
                ? ButtonsConfig.IconKind.ITEM : currentKind;
        this.valueInit = currentValue == null ? "" : currentValue;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        int panelW = Math.min(Math.max(420, 320), this.width - 40);
        int y = this.height / 2 - 132; // move header up a bit

        sourceBtn = addRenderableWidget(
                CycleButton.builder((ButtonsConfig.IconKind k) -> Component.literal(k.name()))
                        .withValues(ButtonsConfig.IconKind.ITEM, ButtonsConfig.IconKind.IMAGE)
                        .withInitialValue(kindInit)
                        .create(cx - panelW/2, y, panelW, 20, Component.literal("Source"))
        );
        y += 24;

        searchBox = new EditBox(this.font, cx - panelW/2, y, panelW, 20, Component.literal("Search"));
        searchBox.setMaxLength(200);
        searchBox.setValue("");
        addRenderableWidget(searchBox);
        y += 28; // extra gap so grid never overlaps search

        // Grid geometry
        cols = Math.max(6, Math.min(16, (panelW + PAD) / (CELL + PAD)));
        rows = 6;
        pageSize = cols * rows;

        gridW = cols * (CELL + PAD) - PAD;
        gridH = rows * (CELL + PAD) - PAD;
        gridX = cx - gridW / 2;
        gridY = y;

        int controlsY = gridY + gridH + 12;

        prevBtn = addRenderableWidget(Button.builder(Component.literal("<< Prev"), b -> {
            if (page > 0) { page--; updateNavStates(); }
        }).bounds(cx - panelW/2, controlsY, 80, 20).build());

        nextBtn = addRenderableWidget(Button.builder(Component.literal("Next >>"), b -> {
            int maxPage = Math.max(0, (filtered.size() - 1) / pageSize);
            if (page < maxPage) { page++; updateNavStates(); }
        }).bounds(cx - panelW/2 + 86, controlsY, 80, 20).build());

        useBtn = addRenderableWidget(Button.builder(Component.literal("Use Selected"), b -> {
            if (selectedValue != null) {
                onPick.accept(sourceBtn.getValue(), selectedValue);
                Minecraft.getInstance().setScreen(parent);
            }
        }).bounds(cx + panelW/2 - 180, controlsY, 110, 20).build());

        cancelBtn = addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(cx + panelW/2 - 64, controlsY, 64, 20).build());

        rebuildData();
        setInitialFocus(searchBox);
    }

    private void rebuildData() {
        all.clear();
        selectedValue = null;

        if (sourceBtn.getValue() == ButtonsConfig.IconKind.ITEM) {
            for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) all.add(id.toString());
            Collections.sort(all);
        } else {
            try (var stream = Files.list(ButtonsConfig.IMG_DIR)) {
                all.addAll(stream
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .map(p -> p.getFileName().toString())
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList()));
            } catch (IOException ignored) {}
        }

        filterAndGotoValue(valueInit);
        updateNavStates();
    }

    private void filterAndGotoValue(String preferredValue) {
        String q = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filtered = all;
        } else {
            String[] tokens = q.split("\\s+");
            filtered = all.stream().filter(s -> {
                String t = s.toLowerCase(Locale.ROOT);
                for (String tok : tokens) if (!t.contains(tok)) return false;
                return true;
            }).collect(Collectors.toList());
        }
        page = 0;
        selectedValue = null;
        if (preferredValue != null && !preferredValue.isEmpty()) {
            int idx = filtered.indexOf(preferredValue);
            if (idx >= 0) { page = idx / pageSize; selectedValue = preferredValue; }
        }
    }

    private void updateNavStates() {
        int maxPage = Math.max(0, (filtered.size() - 1) / pageSize);
        if (prevBtn != null) prevBtn.active = page > 0;
        if (nextBtn != null) nextBtn.active = page < maxPage;
        if (useBtn  != null) useBtn .active = (selectedValue != null);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean r = super.charTyped(codePoint, modifiers);
        if (searchBox.isFocused()) { filterAndGotoValue(null); updateNavStates(); }
        return r;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean r = super.keyPressed(keyCode, scanCode, modifiers);
        if (searchBox.isFocused()) { filterAndGotoValue(null); updateNavStates(); }
        return r;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY >= gridY && mouseY < gridY + gridH && mouseX >= gridX && mouseX < gridX + gridW) {
            int relX = (int)mouseX - gridX;
            int relY = (int)mouseY - gridY;
            int col = relX / (CELL + PAD);
            int row = relY / (CELL + PAD);
            if (col >= 0 && col < cols && row >= 0 && row < rows) {
                int idxInPage = row * cols + col;
                int globalIdx = page * pageSize + idxInPage;
                if (globalIdx >= 0 && globalIdx < filtered.size()) {
                    selectedValue = filtered.get(globalIdx);
                    updateNavStates();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (sourceBtn != null && sourceBtn.getValue() != kindInit) {
            kindInit = sourceBtn.getValue();
            rebuildData();
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTicks);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, "Pick Icon", cx, this.height / 2 - 148, 0xFFFFFF);

        // grid bg
        g.fill(gridX - 2, gridY - 2, gridX + gridW + 2, gridY + gridH + 2, 0x66000000);

        int start = page * pageSize;
        int end   = Math.min(filtered.size(), start + pageSize);

        for (int i = 0; i < (end - start); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = gridX + col * (CELL + PAD);
            int y = gridY + row * (CELL + PAD);
            String val = filtered.get(start + i);

            if (val.equals(selectedValue)) {
                g.fill(x - 1, y - 1, x + CELL + 1, y,              0xFFFFFFFF);
                g.fill(x - 1, y + CELL, x + CELL + 1, y + CELL + 1, 0xFFFFFFFF);
                g.fill(x - 1, y,       x,         y + CELL,         0xFFFFFFFF);
                g.fill(x + CELL, y,    x + CELL + 1, y + CELL,      0xFFFFFFFF);
            }

            if (sourceBtn.getValue() == ButtonsConfig.IconKind.ITEM) {
                try {
                    var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(val));
                    if (item != null) {
                        var stack = new net.minecraft.world.item.ItemStack(item);
                        g.renderItem(stack, x + 2, y + 2);
                    }
                } catch (Exception ignored) {}
            } else {
                var img = IconManager.get(val);
                if (img != null) {
                    // Scale full PNG to cell
                    g.blit(
                        img.rl,
                        x, y,
                        CELL, CELL,
                        0f, 0f,
                        img.w, img.h,
                        img.w, img.h
                    );
                } else {
                    g.fill(x, y, x + CELL, y + CELL, 0xFF222222);
                    g.drawString(this.font, "X", x + 7, y + 6, 0xFFFF0000, false);
                }
            }
        }

        g.drawCenteredString(this.font,
                "Items: ids like minecraft:diamond • Images: put PNGs in config/uibuttons/textures/",
                cx, gridY + gridH + 36, 0xAAAAAA);
    }
}
