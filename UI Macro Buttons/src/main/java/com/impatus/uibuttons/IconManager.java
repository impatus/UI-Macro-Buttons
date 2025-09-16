package com.impatus.uibuttons;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class IconManager {
    public static final class Img {
        public final ResourceLocation rl;
        public final int w, h;
        public Img(ResourceLocation rl, int w, int h) { this.rl = rl; this.w = w; this.h = h; }
    }

    private static final Map<String, Img> CACHE = new HashMap<>();

    public static Img get(String fileName) {
        try {
            if (fileName == null || fileName.isBlank()) return null;
            Path p = ButtonsConfig.IMG_DIR.resolve(fileName).normalize();
            if (!p.startsWith(ButtonsConfig.IMG_DIR)) return null; // safety
            if (!Files.exists(p)) return null;

            Img cached = CACHE.get(fileName);
            if (cached != null) return cached;

            try (InputStream in = Files.newInputStream(p)) {
                NativeImage img = NativeImage.read(in);
                DynamicTexture tex = new DynamicTexture(img);
                String key = "icons/" + Integer.toHexString(fileName.hashCode());
                ResourceLocation rl = new ResourceLocation("uibuttons", key);
                Minecraft.getInstance().getTextureManager().register(rl, tex);
                Img wrapped = new Img(rl, img.getWidth(), img.getHeight());
                CACHE.put(fileName, wrapped);
                return wrapped;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
