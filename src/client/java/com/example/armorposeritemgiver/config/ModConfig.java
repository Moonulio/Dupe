package com.example.armorposeritemgiver.config;

import com.example.armorposeritemgiver.client.ArmorPoserItemGiverClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Конфиг с горячей клавишей и blacklist-ом предметов.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<ModConfig>() { }.getType();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("armorposer-itemgiver.json");

    private static ModConfig INSTANCE;

    public int openScreenKey = ArmorPoserItemGiverClient.defaultKey();
    public Set<String> blacklist = new HashSet<>();
    public boolean allowUnsafeWithoutCreative = true;

    public static ModConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                INSTANCE = GSON.fromJson(reader, TYPE);
            } catch (Exception ignored) {
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            save();
        }

        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(get(), TYPE, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public boolean isBlacklisted(Identifier id) {
        return blacklist.contains(id.toString());
    }
}
