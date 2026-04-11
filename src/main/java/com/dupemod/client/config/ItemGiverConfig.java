package com.dupemod.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурация мода. Хранит настройки горячей клавиши и чёрный список предметов.
 * Файл конфигурации: config/armorposer_itemgiver.json
 */
public class ItemGiverConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("armorposer_itemgiver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ItemGiverConfig INSTANCE;

    // Код клавиши для открытия GUI (GLFW key code, по умолчанию G = 71)
    public int openGuiKeyCode = 71;

    // Чёрный список предметов (идентификаторы, например "minecraft:bedrock")
    public List<String> blacklistedItems = new ArrayList<>();

    // Максимальная длина NBT строки
    public int maxNbtLength = 32000;

    /**
     * Загрузка конфигурации из файла. Если файл не существует - создаётся по умолчанию.
     */
    public static void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("armorposer_itemgiver.json");
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                INSTANCE = GSON.fromJson(json, ItemGiverConfig.class);
                LOGGER.info("[ItemGiverConfig] Конфигурация загружена");
            } catch (IOException e) {
                LOGGER.error("[ItemGiverConfig] Ошибка загрузки конфигурации", e);
                INSTANCE = new ItemGiverConfig();
                save();
            }
        } else {
            INSTANCE = new ItemGiverConfig();
            save();
        }
    }

    /**
     * Сохранение конфигурации в файл.
     */
    public static void save() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("armorposer_itemgiver.json");
        try {
            Files.writeString(configPath, GSON.toJson(INSTANCE));
            LOGGER.info("[ItemGiverConfig] Конфигурация сохранена");
        } catch (IOException e) {
            LOGGER.error("[ItemGiverConfig] Ошибка сохранения конфигурации", e);
        }
    }

    /**
     * Получить текущий экземпляр конфигурации.
     */
    public static ItemGiverConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    /**
     * Проверить, находится ли предмет в чёрном списке.
     */
    public boolean isBlacklisted(String itemId) {
        return blacklistedItems != null && blacklistedItems.contains(itemId);
    }
}
