package com.dupe.posergive.config;

import com.dupe.posergive.client.PoserGiveClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON config: hotkey + blacklist.
 */
public class PoserGiveConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("posergive.json");

    public static PoserGiveConfig INSTANCE = defaults();

    public int openPickerKey;
    public List<String> blacklist;

    public static PoserGiveConfig defaults() {
        PoserGiveConfig cfg = new PoserGiveConfig();
        cfg.openPickerKey = GLFW.GLFW_KEY_P;
        cfg.blacklist = new ArrayList<>();
        return cfg;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                INSTANCE = GSON.fromJson(Files.readString(CONFIG_PATH), PoserGiveConfig.class);
            } catch (Exception ignored) {
                INSTANCE = defaults();
            }
        }

        if (INSTANCE == null) {
            INSTANCE = defaults();
        }

        if (INSTANCE.blacklist == null) {
            INSTANCE.blacklist = new ArrayList<>();
        }

        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            System.err.println("[" + PoserGiveClient.MOD_ID + "] Failed to save config: " + e.getMessage());
        }
    }
}
