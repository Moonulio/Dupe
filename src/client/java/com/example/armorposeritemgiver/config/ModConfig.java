package com.example.armorposeritemgiver.config;

import com.example.armorposeritemgiver.client.ArmorPoserItemGiverClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Конфигурация мода Armor Poser Item Giver.
 * <p>
 * Хранит настройки:
 * <ul>
 *   <li>{@link #openScreenKey} — код горячей клавиши для открытия окна (по умолчанию P = 80)</li>
 *   <li>{@link #blacklist} — чёрный список ID предметов, которые не отображаются в окне</li>
 *   <li>{@link #allowUnsafeWithoutCreative} — разрешить отправку пакетов вне креативного режима</li>
 * </ul>
 * Файл конфигурации: {@code config/armorposer-itemgiver.json}
 */
@Environment(EnvType.CLIENT)
public class ModConfig {

    /** Gson-инстанс с красивым форматированием для записи конфигурации */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Тип для десериализации через Gson */
    private static final Type TYPE = new TypeToken<ModConfig>() {}.getType();

    /** Путь к файлу конфигурации */
    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("armorposer-itemgiver.json");

    /** Текущий экземпляр конфигурации (singleton) */
    private static ModConfig INSTANCE;

    /** Код клавиши для открытия экрана (GLFW keycode, по умолчанию 80 = P) */
    public int openScreenKey = ArmorPoserItemGiverClient.defaultKey();

    /**
     * Чёрный список идентификаторов предметов (формат: "namespace:path").
     * Предметы из этого списка не будут отображаться в окне поиска.
     */
    public Set<String> blacklist = new HashSet<>();

    /**
     * Если true — разрешает отправку пакетов выдачи предметов даже вне креативного режима
     * (например, при наведении на стойку для брони через команду /data merge).
     */
    public boolean allowUnsafeWithoutCreative = true;

    /**
     * Получить текущий экземпляр конфигурации.
     * Если ещё не загружен, создаёт конфигурацию по умолчанию.
     *
     * @return текущий экземпляр ModConfig
     */
    public static ModConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        return INSTANCE;
    }

    /**
     * Загрузить конфигурацию из файла.
     * Если файл не существует, создаёт новый с настройками по умолчанию.
     */
    public static void load() {
        if (Files.exists(PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(PATH)) {
                INSTANCE = GSON.fromJson(reader, TYPE);
            } catch (Exception ignored) {
                // При ошибке чтения — используем значения по умолчанию
                INSTANCE = new ModConfig();
            }
        } else {
            // Файл не найден — создаём конфигурацию по умолчанию и сохраняем
            INSTANCE = new ModConfig();
            save();
        }
        // Дополнительная проверка на null (на случай повреждения JSON)
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
    }

    /**
     * Сохранить текущую конфигурацию в файл.
     */
    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(get(), TYPE, writer);
            }
        } catch (IOException ignored) {
            // Ошибка записи — тихо игнорируем (не критично для работы мода)
        }
    }

    /**
     * Проверить, находится ли предмет в чёрном списке.
     *
     * @param id идентификатор предмета
     * @return true, если предмет в чёрном списке
     */
    public boolean isBlacklisted(Identifier id) {
        return blacklist.contains(id.toString());
    }
}
