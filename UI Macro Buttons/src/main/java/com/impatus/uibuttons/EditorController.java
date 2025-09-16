package com.impatus.uibuttons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EditorController {

    public static boolean dragEnabled = true;
    private static Button lockBtnRef = null;
    private static Button createBtnRef = null;

    private static final int GRID = 8;

    private static int draggingIndex = -1;
    private static int grabDX = 0, grabDY = 0;
    private static int leftCache = 0, topCache = 0;

    private static final int GUI_W = 176, GUI_H = 166;

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (!UIMacroButtonsMod.EDIT_MODE) return;

        Screen s = event.getScreen();
        boolean isInventory = s instanceof InventoryScreen;
        boolean isContainer = s instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
        if (!isInventory && !isContainer) return;

        // Drag toggle
        lockBtnRef = Button.builder(
                Component.literal(dragEnabled ? "Drag: ON" : "Drag: OFF"),
                b -> {
                    dragEnabled = !dragEnabled;
                    if (lockBtnRef != null) {
                        lockBtnRef.setMessage(Component.literal(dragEnabled ? "Drag: ON" : "Drag: OFF"));
                    }
                }
        ).bounds(6, 6, 80, 20).build();
        event.addListener(lockBtnRef);

        // Create Macro
        createBtnRef = Button.builder(
                Component.literal("Create Macro"),
                b -> Minecraft.getInstance().setScreen(new EditorScreen())
        ).bounds(92, 6, 110, 20).build();
        event.addListener(createBtnRef);
    }

    @SubscribeEvent
    public void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!UIMacroButtonsMod.EDIT_MODE) return;

        Screen s = event.getScreen();
        boolean isInventory = s instanceof InventoryScreen;
        boolean isContainer = s instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
        if (!isInventory && !isContainer) return;

        // Cache inventory top-left
        leftCache = (s.width - GUI_W) / 2;
        topCache  = (s.height - GUI_H) / 2;

        double mx = event.getMouseX();
        double my = event.getMouseY();

        // RIGHT-CLICK => open editor for the clicked macro (no dragging)
        if (event.getButton() == 1) { // right mouse
            for (int i = ButtonsConfig.buttons.size() - 1; i >= 0; i--) {
                ButtonsConfig.ButtonSpec spec = ButtonsConfig.buttons.get(i);
                if (spec.scope == ButtonsConfig.Scope.INVENTORY_ONLY && !isInventory) continue;

                int[] xy = btnTopLeft(spec, leftCache, topCache);
                int x = xy[0], y = xy[1];

                if (mx >= x && mx <= x + spec.width && my >= y && my <= y + spec.height) {
                    Minecraft.getInstance().setScreen(new EditorScreen(i));
                    event.setCanceled(true);
                    return;
                }
            }
            return; // if right click misses a macro, do nothing special
        }

        // LEFT-CLICK + dragEnabled => try to start dragging
        if (event.getButton() == 0 && dragEnabled) {
            for (int i = ButtonsConfig.buttons.size() - 1; i >= 0; i--) {
                ButtonsConfig.ButtonSpec spec = ButtonsConfig.buttons.get(i);
                if (spec.scope == ButtonsConfig.Scope.INVENTORY_ONLY && !isInventory) continue;

                int[] xy = btnTopLeft(spec, leftCache, topCache);
                int x = xy[0], y = xy[1];

                if (mx >= x && mx <= x + spec.width && my >= y && my <= y + spec.height) {
                    draggingIndex = i;
                    grabDX = (int)Math.round(mx) - x;
                    grabDY = (int)Math.round(my) - y;
                    event.setCanceled(true); // capture drag
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!UIMacroButtonsMod.EDIT_MODE || !dragEnabled) return;
        if (draggingIndex < 0) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();

        int desiredX = (int)Math.round(mx) - grabDX;
        int desiredY = (int)Math.round(my) - grabDY;

        // Snap to grid anchored at inventory top-left
        int snappedX = snapToGrid(desiredX, leftCache, GRID);
        int snappedY = snapToGrid(desiredY, topCache,  GRID);

        ButtonsConfig.ButtonSpec spec = ButtonsConfig.buttons.get(draggingIndex);

        // Update offsets from snapped position
        switch (spec.anchor) {
            case RIGHT -> {
                spec.xOffset = snappedX - (leftCache + GUI_W);
                spec.yOffset = snappedY - topCache;
            }
            case LEFT -> {
                spec.xOffset = (leftCache - snappedX) - spec.width;
                spec.yOffset = snappedY - topCache;
            }
            case BOTTOM -> {
                int centerBase = leftCache + (GUI_W - spec.width) / 2;
                spec.xOffset = snappedX - centerBase;
                spec.yOffset = snappedY - (topCache + GUI_H);
            }
        }

        // Live move
        Button live = UIMacroButtonsMod.LIVE_BUTTONS.get(draggingIndex);
        if (live != null) live.setPosition(snappedX, snappedY);

        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!UIMacroButtonsMod.EDIT_MODE || !dragEnabled) return;
        if (event.getButton() != 0) return; // left mouse only
        if (draggingIndex < 0) return;

        draggingIndex = -1;
        ButtonsConfig.save();
        event.setCanceled(true);
    }

    // Helpers
    private static int snapToGrid(int value, int origin, int grid) {
        int rel = value - origin;
        int half = grid / 2;
        int snappedRel = ((rel + (rel >= 0 ? half : -half)) / grid) * grid;
        return origin + snappedRel;
    }

    private static int[] btnTopLeft(ButtonsConfig.ButtonSpec spec, int left, int top) {
        return switch (spec.anchor) {
            case RIGHT -> new int[]{ left + GUI_W + spec.xOffset, top + spec.yOffset };
            case LEFT  -> new int[]{ left - spec.xOffset - spec.width, top + spec.yOffset };
            case BOTTOM-> new int[]{ left + (GUI_W - spec.width) / 2 + spec.xOffset, top + GUI_H + spec.yOffset };
        };
    }
}
