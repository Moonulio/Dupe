package com.example.armorposeritemgiver.client;

import com.example.armorposeritemgiver.config.ModConfig;
import com.example.armorposeritemgiver.screen.ItemBrowserScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Главная точка входа клиентского мода Armor Poser Item Giver.
 * <p>
 * Регистрирует горячую клавишу для открытия окна поиска/выдачи предметов
 * и загружает конфигурацию мода при инициализации.
 */
@Environment(EnvType.CLIENT)
public class ArmorPoserItemGiverClient implements ClientModInitializer {

    /** Уникальный идентификатор мода */
    public static final String MOD_ID = "armorposer-itemgiver";

    /** Привязка горячей клавиши для открытия окна Item Browser */
    private static KeyBinding openBrowserKey;

    /**
     * Инициализация клиентской части мода.
     * Загружает конфигурацию, регистрирует клавишу и подписывается на тик клиента.
     */
    @Override
    public void onInitializeClient() {
        // Загрузка конфигурации из файла (или создание нового при отсутствии)
        ModConfig.load();

        // Регистрация горячей клавиши (по умолчанию — P, код 80)
        openBrowserKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.armorposer-itemgiver.open_browser",         // Ключ перевода
                InputUtil.Type.KEYSYM,                            // Тип ввода — клавиша
                ModConfig.get().openScreenKey,                    // Код клавиши из конфига
                "category.armorposer-itemgiver"                   // Категория в настройках
        ));

        // Подписка на обработку нажатий каждый клиентский тик
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    /**
     * Обработчик клиентского тика: открывает окно Item Browser при нажатии горячей клавиши.
     *
     * @param client экземпляр MinecraftClient
     */
    private void onClientTick(MinecraftClient client) {
        while (openBrowserKey.wasPressed()) {
            if (client.player == null) continue;
            // Открываем окно поиска предметов, передавая текущий экран как родительский
            client.setScreen(new ItemBrowserScreen(client.currentScreen));
        }
    }

    /**
     * Возвращает код клавиши по умолчанию (P = 80 в GLFW).
     *
     * @return код клавиши по умолчанию
     */
    public static int defaultKey() {
        return 80; // GLFW_KEY_P
    }
}
