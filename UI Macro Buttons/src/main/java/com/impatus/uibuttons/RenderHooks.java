package com.impatus.uibuttons;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "uibuttons", value = Dist.CLIENT)
public class RenderHooks {

    private static final int GUI_W = 176, GUI_H = 166;

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        Screen s = event.getScreen();
        boolean isInventory = s instanceof InventoryScreen;
        boolean isContainer = s instanceof AbstractContainerScreen<?>;
        if (!isInventory && !isContainer) return;

        GuiGraphics g = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        // Tooltip only (labels), no icon drawing here
        for (int i = 0; i < ButtonsConfig.buttons.size(); i++) {
            ButtonsConfig.ButtonSpec spec = ButtonsConfig.buttons.get(i);
            if (spec.scope == ButtonsConfig.Scope.INVENTORY_ONLY && !isInventory) continue;
            if (spec.label == null || spec.label.isBlank()) continue;

            Button btn = (i < UIMacroButtonsMod.LIVE_BUTTONS.size()) ? UIMacroButtonsMod.LIVE_BUTTONS.get(i) : null;
            if (btn == null) continue;

            int x = btn.getX(), y = btn.getY(), w = btn.getWidth(), h = btn.getHeight();
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                g.renderTooltip(s.getMinecraft().font, Component.literal(spec.label), mouseX, mouseY);
                break; // only one tooltip per frame
            }
        }
    }
}
