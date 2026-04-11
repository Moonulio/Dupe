package com.dupemod.client;

import com.dupemod.client.config.ItemGiverConfig;
import com.dupemod.client.gui.ItemGiverScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главный класс клиентского мода - дополнения к Armor Poser.
 * Регистрирует клавишу для открытия интерфейса выдачи предметов через стойку для брони.
 */
public class ItemGiverClientMod implements ClientModInitializer {
    public static final String MOD_ID = "armorposer_itemgiver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Горячая клавиша для открытия интерфейса (по умолчанию - G)
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ArmorPoserItemGiver] Инициализация клиентского мода...");

        // Загрузка конфигурации
        ItemGiverConfig.load();

        // Регистрация горячей клавиши
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.armorposer_itemgiver.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.armorposer_itemgiver"
        ));

        // Обработка нажатия клавиши каждый тик
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                openItemGiverScreen(client);
            }
        });

        LOGGER.info("[ArmorPoserItemGiver] Мод успешно инициализирован!");
    }

    /**
     * Открывает экран выдачи предметов.
     * Ищет ближайшую стойку для брони в радиусе видимости игрока.
     */
    private void openItemGiverScreen(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // Сначала проверяем, смотрит ли игрок на стойку для брони
        ArmorStandEntity targetStand = null;

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity instanceof ArmorStandEntity stand) {
                targetStand = stand;
            }
        }

        // Если игрок не смотрит на стойку, ищем ближайшую в радиусе 5 блоков
        if (targetStand == null) {
            double closestDist = 25.0; // 5 блоков в квадрате
            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof ArmorStandEntity stand) {
                    double dist = client.player.squaredDistanceTo(stand);
                    if (dist < closestDist) {
                        closestDist = dist;
                        targetStand = stand;
                    }
                }
            }
        }

        if (targetStand != null) {
            client.setScreen(new ItemGiverScreen(targetStand));
        } else {
            // Показываем сообщение о необходимости стойки для брони
            if (client.player != null) {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("\u00a7c[ItemGiver] \u00a7fПоставьте стойку для брони рядом!"),
                        true
                );
            }
        }
    }
}
