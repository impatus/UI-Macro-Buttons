package com.impatus.uibuttons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ButtonsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ButtonSpec>>(){}.getType();

    public static final Path DIR  = FMLPaths.CONFIGDIR.get().resolve("uibuttons");
    public static final Path FILE = DIR.resolve("buttons.json");
    public static final Path IMG_DIR = DIR.resolve("textures"); // put PNGs here

    public static List<ButtonSpec> buttons = new ArrayList<>();

    public static void load() {
        try {
            if (!Files.exists(DIR)) Files.createDirectories(DIR);
            if (!Files.exists(IMG_DIR)) Files.createDirectories(IMG_DIR);
            if (!Files.exists(FILE)) { save(); return; }
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                List<ButtonSpec> list = GSON.fromJson(r, LIST_TYPE);
                buttons = (list != null) ? list : new ArrayList<>();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (buttons == null) buttons = new ArrayList<>();
        }
    }

    public static void save() {
        try {
            if (!Files.exists(DIR)) Files.createDirectories(DIR);
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(buttons, LIST_TYPE, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -------- data model --------
    public enum Anchor { RIGHT, LEFT, BOTTOM }
    public enum Scope  { INVENTORY_ONLY, ANY_CONTAINER }
    public enum IconKind { NONE, ITEM, IMAGE }

    public static class ButtonSpec {
        public String label   = "BTN";
        public String command = "/help";
        public Anchor anchor  = Anchor.RIGHT;
        public int xOffset    = 6;
        public int yOffset    = 10;
        public int width      = 20;
        public int height     = 20;
        public Scope scope    = Scope.INVENTORY_ONLY;

        // --- icon fields ---
        public IconKind iconKind = IconKind.NONE;  // NONE / ITEM / IMAGE
        public String   iconValue = "";            // ITEM: "minecraft:diamond", IMAGE: "myicon.png"
        public boolean  iconOnly  = true;          // hide label if true
    }
}
