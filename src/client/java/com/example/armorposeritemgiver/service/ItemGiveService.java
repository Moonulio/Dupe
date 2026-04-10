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
 * Сервис выдачи предметов на стойку для брони.
 * <p>
 * Поддерживает несколько способов выдачи:
 * <ol>
 *   <li><b>Команда /data merge</b> — устанавливает предмет в руку стойки для брони.
 *       Работает если у игрока есть права на выполнение команд (оператор).
 *       Это основной способ, не требующий креативного режима.</li>
 *   <li><b>CreativeInventoryActionC2SPacket</b> — отправляет пакет напрямую
 *       для помещения предмета в инвентарь игрока.
 *       В режиме allowUnsafeWithoutCreative отправляется независимо от режима игры.</li>
 * </ol>
 * <p>
 * Если передана ссылка на конкретную стойку для брони (из экрана Armor Poser),
 * команда /data merge нацеливается именно на неё по UUID.
 */
@Environment(EnvType.CLIENT)
public final class ItemGiveService {

    private ItemGiveService() {
        // Утилитный класс — конструктор закрыт
    }

    /**
     * Выдать предмет на стойку для брони или в инвентарь игрока.
     * <p>
     * Порядок приоритета:
     * 1. Если передана стойка (armorStand != null) — /data merge на эту стойку
     * 2. Если игрок смотрит на стойку — /data merge на неё
     * 3. Если включён allowUnsafeWithoutCreative — отправка CreativeInventoryActionC2SPacket
     * 4. Если игрок в креативе — clickCreativeStack
     *
     * @param stack         стак предмета для выдачи
     * @param rawSnbt       SNBT-строка для компонентов (может быть пустой)
     * @param armorStand    конкретная стойка для брони (может быть null)
     * @return true, если выдача была выполнена
     */
    public static boolean give(ItemStack stack, String rawSnbt, ArmorStandEntity armorStand) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        if (player == null) {
            return false;
        }

        ItemStack copy = stack.copy();

        // Способ 1: Если передана конкретная стойка — /data merge на неё
        if (armorStand != null) {
            return applyToArmorStand(player, copy, rawSnbt, armorStand);
        }

        // Способ 2: Если игрок смотрит на стойку — /data merge на неё
        ArmorStandEntity looked = getLookedArmorStand(client);
        if (looked != null) {
            return applyToArmorStand(player, copy, rawSnbt, looked);
        }

        // Способ 3: Прямой пакет CreativeInventoryActionC2SPacket
        // (работает без ограничений если сервер не проверяет режим игры)
        if (ModConfig.get().allowUnsafeWithoutCreative) {
            int slot = 36 + player.getInventory().selectedSlot;
            player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot, copy));
            return true;
        }

        // Способ 4: Стандартная выдача в креативном режиме
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        if (player.getAbilities().creativeMode && interactionManager != null) {
            int slot = 36 + player.getInventory().selectedSlot;
            interactionManager.clickCreativeStack(copy, slot);
            return true;
        }

        return false;
    }

    /**
     * Применить предмет к стойке для брони через команду /data merge.
     * Устанавливает предмет в основную руку (MainHand) стойки.
     * <p>
     * Команда формируется с полным SNBT предмета, включая NBT-компоненты
     * если они были указаны пользователем.
     *
     * @param player  игрок, от имени которого отправляется команда
     * @param stack   стак предмета
     * @param rawSnbt SNBT-строка компонентов
     * @param stand   целевая стойка для брони
     * @return true (команда всегда отправляется, результат определяется сервером)
     */
    private static boolean applyToArmorStand(ClientPlayerEntity player, ItemStack stack,
                                              String rawSnbt, ArmorStandEntity stand) {
        // Получаем ID предмета
        Identifier id = Registries.ITEM.getId(stack.getItem());

        // Формируем SNBT-описание компонентов
        String components = (rawSnbt == null || rawSnbt.isBlank()) ? "" : ",components:" + rawSnbt.trim();

        // Формируем описание предмета для HandItems
        // HandItems — массив из двух элементов: [MainHand, OffHand]
        String handItem = "{id:\"" + id + "\",count:" + stack.getCount() + components + "}";

        // Отправляем команду /data merge для установки предмета в руку стойки
        String cmd = "data merge entity " + stand.getUuidAsString()
                + " {HandItems:[" + handItem + ",{}]}";
        player.networkHandler.sendCommand(cmd);

        return true;
    }

    /**
     * Получить стойку для брони, на которую смотрит игрок.
     *
     * @param client экземпляр MinecraftClient
     * @return ArmorStandEntity, если найдена, иначе null
     */
    public static ArmorStandEntity getLookedArmorStand(MinecraftClient client) {
        HitResult hitResult = client.crosshairTarget;

        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return null;
        }

        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        Entity entity = entityHitResult.getEntity();
        if (entity instanceof ArmorStandEntity stand) {
            return stand;
        }

        return null;
    }
}
