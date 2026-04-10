package com.example.armorposeritemgiver.service;

import com.example.armorposeritemgiver.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Сервис выдачи предметов игроку.
 * <p>
 * Основной метод {@link #give(ItemStack, int, String)} пытается выдать предмет
 * тремя способами (в порядке приоритета):
 * <ol>
 *   <li><b>Креативный режим</b> — через {@code clickCreativeStack()} и
 *       {@link CreativeInventoryActionC2SPacket}. Это самый надёжный способ,
 *       работает только если игрок в креативе.</li>
 *   <li><b>Команда /data merge</b> — если игрок смотрит на стойку для брони,
 *       отправляет серверу команду для замены предмета в руке стойки.</li>
 *   <li><b>Unsafe-пакет</b> — если разрешено в конфиге ({@code allowUnsafeWithoutCreative}),
 *       отправляет пакет напрямую (может не сработать на некоторых серверах).</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class ItemGiveService {

    private ItemGiveService() {
        // Утилитный класс — конструктор закрыт
    }

    /**
     * Выдать предмет игроку в указанный слот.
     *
     * @param stack             стак предмета для выдачи
     * @param slot              номер слота инвентаря (36 + selectedSlot для хотбара)
     * @param rawSnbtComponents необработанная строка SNBT-компонентов (может быть пустой)
     * @return true, если выдача прошла успешно
     */
    public static boolean give(ItemStack stack, int slot, String rawSnbtComponents) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;

        if (player == null || interactionManager == null) {
            return false;
        }

        ItemStack copy = stack.copy();

        // Способ 1: Креативный режим — используем clickCreativeStack для синхронизации
        if (player.getAbilities().creativeMode) {
            // clickCreativeStack гарантирует корректную синхронизацию с сервером
            interactionManager.clickCreativeStack(copy, slot);
            // Дополнительно отправляем пакет для полной надёжности
            player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot, copy));
            return true;
        }

        // Способ 2: Применение через команду /data merge к стойке для брони
        if (tryApplyToLookedArmorStandViaCommand(copy, rawSnbtComponents)) {
            return true;
        }

        // Способ 3: Прямой пакет (unsafe — может быть отклонён сервером)
        if (!ModConfig.get().allowUnsafeWithoutCreative || !isLookingAtArmorStand(client)) {
            return false;
        }

        player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot, copy));
        return true;
    }

    /**
     * Попытка применить предмет к стойке для брони через серверную команду /data merge.
     * Работает, если у игрока есть права на выполнение этой команды (оператор/командный блок).
     *
     * @param stack             стак предмета
     * @param rawSnbtComponents SNBT-компоненты для включения в команду
     * @return true, если команда была отправлена (стойка найдена)
     */
    private static boolean tryApplyToLookedArmorStandViaCommand(ItemStack stack, String rawSnbtComponents) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ArmorStandEntity stand = getLookedArmorStand(client);

        if (player == null || stand == null) {
            return false;
        }

        // Получаем ID предмета для формирования команды
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id == null) {
            return false;
        }

        // Формируем SNBT-компоненты (пустой объект, если не указаны)
        String components = (rawSnbtComponents == null || rawSnbtComponents.isBlank())
                ? "{}" : rawSnbtComponents.trim();

        // Формируем описание предмета для руки стойки
        String handItem = "{id:\"" + id + "\",count:" + stack.getCount()
                + ",components:" + components + "}";

        // Отправляем команду /data merge для замены предмета в руке стойки
        String cmd = "data merge entity " + stand.getUuidAsString()
                + " {Invisible:0b,Marker:0b,HandItems:[" + handItem + ",{}]}";
        player.networkHandler.sendCommand(cmd);

        return true;
    }

    /**
     * Проверяет, смотрит ли игрок на стойку для брони.
     *
     * @param client экземпляр MinecraftClient
     * @return true, если crosshair наведён на ArmorStandEntity
     */
    private static boolean isLookingAtArmorStand(MinecraftClient client) {
        return getLookedArmorStand(client) != null;
    }

    /**
     * Получить стойку для брони, на которую смотрит игрок.
     *
     * @param client экземпляр MinecraftClient
     * @return ArmorStandEntity, если найдена, иначе null
     */
    private static ArmorStandEntity getLookedArmorStand(MinecraftClient client) {
        HitResult hitResult = client.crosshairTarget;

        // Проверяем, что это попадание по сущности
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return null;
        }

        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        // Проверяем, что сущность — стойка для брони
        Entity entity = entityHitResult.getEntity();
        if (entity instanceof ArmorStandEntity stand) {
            return stand;
        }

        return null;
    }
}
