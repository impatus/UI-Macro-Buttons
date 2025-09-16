package com.impatus.uibuttons;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class MacroButton extends Button {

    private final ButtonsConfig.ButtonSpec spec;

    public MacroButton(int x, int y, int w, int h,
                       ButtonsConfig.ButtonSpec spec,
                       OnPress onPress) {
        super(x, y, w, h, Component.empty(), onPress, DEFAULT_NARRATION);
        this.spec = spec;
        // Icon-only face (no vanilla label)
        this.setMessage(Component.empty());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        // draw vanilla button face (hover/press states)
        super.renderWidget(g, mouseX, mouseY, partialTicks);

        // Draw our icon INSIDE the button render, so tooltips (drawn later) appear above.
        String val = (spec.iconValue == null) ? "" : spec.iconValue.trim();
        if (val.isEmpty()) return;

        if (isItemId(val)) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(val));
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                int ix = getX() + (getWidth()  - 16) / 2;
                int iy = getY() + (getHeight() - 16) / 2;
                g.renderItem(stack, ix, iy);
            }
        } else {
            IconManager.Img img = IconManager.get(val);
            if (img != null) {
                // Scale full PNG to button rect
                g.blit(
                    img.rl,
                    getX(), getY(),
                    getWidth(), getHeight(),
                    0f, 0f,
                    img.w, img.h,
                    img.w, img.h
                );
            }
        }
    }

    private static boolean isItemId(String s) {
        try {
            ResourceLocation rl = new ResourceLocation(s);
            return ForgeRegistries.ITEMS.containsKey(rl);
        } catch (Exception ignored) { return false; }
    }
}
