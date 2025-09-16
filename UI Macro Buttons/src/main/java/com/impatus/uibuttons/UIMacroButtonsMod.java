package com.impatus.uibuttons;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod("uibuttons")
public class UIMacroButtonsMod {

    public static final List<Button> LIVE_BUTTONS = new ArrayList<>();

    private static KeyMapping OPEN_KEY;
    public static boolean EDIT_MODE = false;
    public static boolean DRAG_MODE = false;

    private static int draggingIndex = -1;
    private static int grabDX = 0, grabDY = 0;

    private static boolean SUPPRESS_CLOSE_ON_EDITOR_OPEN = false;

    private static final int GUI_W = 176;
    private static final int GUI_H = 166;

    private static final int GRID = 2;

    public UIMacroButtonsMod() {
        ButtonsConfig.load();
    }

    @Mod.EventBusSubscriber(modid = "uibuttons", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent e) {
            OPEN_KEY = new KeyMapping(
                    "key.uibuttons.open_menu",
                    InputConstants.Type.KEYSYM,
                    InputConstants.KEY_G,
                    "key.categories.uibuttons"
            );
            e.register(OPEN_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = "uibuttons", value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (OPEN_KEY != null && OPEN_KEY.consumeClick()) {
                EDIT_MODE = !EDIT_MODE;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    if (EDIT_MODE) mc.setScreen(new InventoryScreen(mc.player));
                    mc.gui.getChat().addMessage(
                            Component.literal("[UI Buttons] Edit mode " + (EDIT_MODE ? "ON" : "OFF"))
                    );
                }
            }
        }

        @SubscribeEvent
        public static void onScreenClosing(ScreenEvent.Closing event) {
            Screen s = event.getScreen();
            boolean isInventory = s instanceof InventoryScreen;
            boolean isContainer = s instanceof AbstractContainerScreen<?>;
            if (!EDIT_MODE || (!isInventory && !isContainer)) return;

            if (SUPPRESS_CLOSE_ON_EDITOR_OPEN) {
                SUPPRESS_CLOSE_ON_EDITOR_OPEN = false;
                return;
            }

            EDIT_MODE = false;
            DRAG_MODE = false;
            draggingIndex = -1;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.gui.getChat().addMessage(Component.literal("[UI Buttons] Edit mode OFF"));
            }
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            Screen s = event.getScreen();
            boolean isInventory = s instanceof InventoryScreen;
            boolean isContainer = s instanceof AbstractContainerScreen<?>;
            if (!isInventory && !isContainer) {
                LIVE_BUTTONS.clear();
                return;
            }

            int left = (s.width - GUI_W) / 2;
            int top  = (s.height - GUI_H) / 2;

            LIVE_BUTTONS.clear();

            for (int i = 0; i < ButtonsConfig.buttons.size(); i++) {
                final int index = i;
                ButtonsConfig.ButtonSpec spec = ButtonsConfig.buttons.get(i);

                if (spec.scope == ButtonsConfig.Scope.INVENTORY_ONLY && !isInventory) {
                    LIVE_BUTTONS.add(null);
                    continue;
                }

                int x, y;
                switch (spec.anchor) {
                    case RIGHT -> { x = left + GUI_W + spec.xOffset; y = top + spec.yOffset; }
                    case LEFT  -> { x = left - spec.xOffset - spec.width; y = top + spec.yOffset; }
                    case BOTTOM -> {
                        x = left + (GUI_W - spec.width) / 2 + spec.xOffset;
                        y = top + GUI_H + spec.yOffset;
                    }
                    default -> { x = left + GUI_W + spec.xOffset; y = top + spec.yOffset; }
                }

                Button btn = new MacroButton(
                        x, y,
                        Math.max(20, spec.width), Math.max(20, spec.height),
                        spec,
                        b -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (EDIT_MODE) {
                                if (!DRAG_MODE) {
                                    SUPPRESS_CLOSE_ON_EDITOR_OPEN = true;
                                    mc.setScreen(new EditorScreen(index));
                                }
                            } else {
                                if (mc.player != null && mc.player.connection != null) {
                                    String cmd = spec.command == null ? "" : spec.command.trim();
                                    if (!cmd.isEmpty()) {
                                        if (cmd.startsWith("/")) cmd = cmd.substring(1);
                                        mc.player.connection.sendCommand(cmd);
                                    }
                                }
                            }
                        }
                );

                event.addListener(btn);
                LIVE_BUTTONS.add(btn);
            }

            if (EDIT_MODE && isInventory) {
                int newX = left + GUI_W + 4;
                int newY = Math.max(4, top - 22);
                Button createBtn = Button.builder(Component.literal("Create Macro"), b -> {
                    SUPPRESS_CLOSE_ON_EDITOR_OPEN = true;
                    Minecraft.getInstance().setScreen(new EditorScreen());
                }).bounds(newX, newY, 100, 20).build();
                event.addListener(createBtn);

                int dragX = newX + 106;
                int dragY = newY;
                Button dragBtn = Button.builder(
                        Component.literal("Drag: " + (DRAG_MODE ? "ON" : "OFF")),
                        b -> {
                            DRAG_MODE = !DRAG_MODE;
                            b.setMessage(Component.literal("Drag: " + (DRAG_MODE ? "ON" : "OFF")));
                            draggingIndex = -1;
                        }
                ).bounds(dragX, dragY, 90, 20).build();
                event.addListener(dragBtn);
            }
        }

        // Dragging
        @SubscribeEvent
        public static void onMouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
            if (!EDIT_MODE || !DRAG_MODE) return;

            Screen s = event.getScreen();
            boolean isInventory = s instanceof InventoryScreen;
            boolean isContainer = s instanceof AbstractContainerScreen<?>;
            if (!isInventory && !isContainer) return;

            if (event.getButton() != 0) return;
            if (LIVE_BUTTONS.isEmpty()) return;

            int mx = (int) Math.round(event.getMouseX());
            int my = (int) Math.round(event.getMouseY());

            for (int i = 0; i < LIVE_BUTTONS.size(); i++) {
                Button btn = LIVE_BUTTONS.get(i);
                if (btn == null) continue;
                int x = btn.getX(), y = btn.getY(), w = btn.getWidth(), h = btn.getHeight();
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    draggingIndex = i;
                    grabDX = mx - x;
                    grabDY = my - y;
                    return;
                }
            }
        }

        @SubscribeEvent
        public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
            if (!EDIT_MODE || !DRAG_MODE) return;
            if (draggingIndex < 0) return;

            Screen s = event.getScreen();
            boolean isInventory = s instanceof InventoryScreen;
            boolean isContainer = s instanceof AbstractContainerScreen<?>;
            if (!isInventory && !isContainer) return;

            if (draggingIndex >= ButtonsConfig.buttons.size()) { draggingIndex = -1; return; }
            ButtonsConfig.ButtonSpec spec = ButtonsConfig.buttons.get(draggingIndex);
            Button live = (draggingIndex < LIVE_BUTTONS.size()) ? LIVE_BUTTONS.get(draggingIndex) : null;
            if (live == null) { draggingIndex = -1; return; }

            int left = (s.width - GUI_W) / 2;
            int top  = (s.height - GUI_H) / 2;

            int mx = (int) Math.round(event.getMouseX());
            int my = (int) Math.round(event.getMouseY());

            int newX = snap(mx - grabDX, GRID);
            int newY = snap(my - grabDY, GRID);

            switch (spec.anchor) {
                case RIGHT -> {
                    spec.xOffset = newX - (left + GUI_W);
                    spec.yOffset = newY - top;
                }
                case LEFT -> {
                    spec.xOffset = (left - spec.width) - newX;
                    if (spec.xOffset < 0) spec.xOffset = 0;
                    spec.yOffset = newY - top;
                }
                case BOTTOM -> {
                    int baseX = left + (GUI_W - spec.width) / 2;
                    spec.xOffset = newX - baseX;
                    spec.yOffset = newY - (top + GUI_H);
                }
            }

            live.setX(newX);
            live.setY(newY);
        }

        @SubscribeEvent
        public static void onMouseUp(ScreenEvent.MouseButtonReleased.Pre event) {
            if (!EDIT_MODE || !DRAG_MODE) return;
            if (event.getButton() != 0) return;
            if (draggingIndex >= 0) {
                ButtonsConfig.save();
                draggingIndex = -1;
            }
        }
    }

    private static int snap(int v, int grid) {
        if (grid <= 1) return v;
        int r = v % grid;
        if (r < 0) r += grid;
        return v - r;
    }
}
