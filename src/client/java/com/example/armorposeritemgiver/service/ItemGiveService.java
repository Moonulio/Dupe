package com.example.armorposeritemgiver.service;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.lang.reflect.Method;

/**
 * Сервис выдачи предметов на стойку для брони через Armor Poser.
 * <p>
 * Весь процесс выдачи идёт через механизм Armor Poser:
 * <ol>
 *   <li>Устанавливает предмет на клиентскую сущность стойки через equipStack()</li>
 *   <li>Вызывает updateEntity() экрана Armor Poser через рефлексию</li>
 *   <li>Armor Poser сохраняет состояние стойки (включая HandItems) и отправляет
 *       ArmorStandSyncPayload на сервер через свою сеть</li>
 * </ol>
 * <p>
 * НЕ использует /data merge entity.
 * НЕ использует CreativeInventoryActionC2SPacket.
 * Работает без оператора и креативного режима — через пакеты Armor Poser.
 */
@Environment(EnvType.CLIENT)
public final class ItemGiveService {

    private ItemGiveService() {
        // Утилитный класс — конструктор закрыт
    }

    /**
     * Выдать предмет на стойку для брони через Armor Poser.
     * <p>
     * Устанавливает предмет в основную руку (MainHand) стойки на клиенте,
     * затем синхронизирует через updateEntity() Armor Poser.
     *
     * @param stack             стак предмета для выдачи
     * @param rawSnbt           SNBT-строка для custom_data компонента (может быть пустой)
     * @param count             количество предметов (1-99)
     * @param armorStand        целевая стойка для брони (может быть null — ищем по взгляду)
     * @param armorPoserScreen  экран Armor Poser для вызова updateEntity (может быть null)
     * @return true, если выдача была выполнена
     */
    public static boolean give(ItemStack stack, String rawSnbt, int count,
                                ArmorStandEntity armorStand,
                                Screen armorPoserScreen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        }

        // Определяем целевую стойку: переданная или по взгляду игрока
        ArmorStandEntity target = armorStand;
        if (target == null) {
            target = getLookedArmorStand(client);
        }
        if (target == null) {
            return false;
        }

        // Создаём копию стака с нужным количеством
        ItemStack copy = stack.copy();
        copy.setCount(Math.max(1, Math.min(count, 99)));

        // Применяем пользовательские NBT-данные как custom_data компонент
        if (rawSnbt != null && !rawSnbt.isBlank()) {
            try {
                NbtCompound compound = StringNbtReader.parse(rawSnbt);
                copy.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                        net.minecraft.component.type.NbtComponent.of(compound));
            } catch (Exception ignored) {
                // Некорректный SNBT — игнорируем, предмет всё равно будет выдан
            }
        }

        // Устанавливаем предмет в основную руку стойки на клиенте
        target.equipStack(EquipmentSlot.MAINHAND, copy);

        // Синхронизируем через Armor Poser: вызываем updateEntity()
        // Это отправит ArmorStandSyncPayload через сеть Armor Poser
        if (armorPoserScreen != null) {
            return callArmorPoserUpdate(armorPoserScreen, new NbtCompound());
        }

        // Если нет экрана Armor Poser — предмет установлен только на клиенте
        return true;
    }

    /**
     * Вызвать метод updateEntity(CompoundTag) на экране Armor Poser через рефлексию.
     * <p>
     * Метод updateEntity определён в ArmorStandScreen Armor Poser и выполняет:
     * <ol>
     *   <li>Сохранение текущего состояния стойки (включая HandItems)</li>
     *   <li>Объединение с переданным compound</li>
     *   <li>Загрузку обновлённого состояния на клиенте</li>
     *   <li>Отправку ArmorStandSyncPayload на сервер</li>
     * </ol>
     * <p>
     * Используется рефлексия, т.к. Armor Poser — опциональная зависимость (@Pseudo mixin).
     * На уровне рантайма NbtCompound (Yarn) и CompoundTag (Mojang) — один и тот же класс.
     *
     * @param screen   экран Armor Poser (ArmorStandScreen)
     * @param compound NBT-данные для обновления (может быть пустым — тогда отправляется текущее состояние)
     * @return true, если вызов прошёл успешно
     */
    private static boolean callArmorPoserUpdate(Screen screen, NbtCompound compound) {
        // Ищем публичный метод updateEntity с одним параметром
        // "updateEntity" — пользовательский метод Armor Poser, не ремапится Fabric
        try {
            for (Method m : screen.getClass().getMethods()) {
                if ("updateEntity".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(screen, compound);
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Ошибка рефлексии — метод не найден или вызов не удался
        }

        // Попытка через getDeclaredMethods (на случай protected/package-private метода)
        try {
            for (Method m : screen.getClass().getDeclaredMethods()) {
                if ("updateEntity".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    m.invoke(screen, compound);
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Не удалось вызвать метод
        }

        return false;
    }

    /**
     * Получить стойку для брони, на которую смотрит игрок.
     *
     * @param client экземпляр MinecraftClient
     * @return ArmorStandEntity, если игрок смотрит на стойку, иначе null
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
