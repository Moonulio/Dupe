package com.example.armorposeritemgiver.service;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Сервис выдачи предметов на стойку для брони через Armor Poser.
 * <p>
 * Механизм работы:
 * <ol>
 *   <li>Устанавливает предмет на клиентскую сущность стойки через equipStack()</li>
 *   <li>Вызывает Services.PLATFORM.updateEntity(armorStand, compound) через рефлексию</li>
 *   <li>Armor Poser (FabricPlatformHelper) выполняет:
 *       <ul>
 *         <li>saveWithoutId() — сохраняет ВСЁ текущее состояние стойки (включая HandItems)</li>
 *         <li>merge(compound) — объединяет с переданными данными</li>
 *         <li>load() — загружает обновлённое состояние на клиенте</li>
 *         <li>Отправляет ПОЛНЫЙ CompoundTag через ArmorStandSyncPayload на сервер</li>
 *       </ul>
 *   </li>
 * </ol>
 * <p>
 * CompoundTag отправленный игроком не проверяется сервером на правдоподность —
 * можно указать любой предмет с любым количеством и это будет применено к стойке.
 * <p>
 * НЕ использует /data merge entity.
 * НЕ использует CreativeInventoryActionC2SPacket.
 * Работает без оператора и креативного режима — через пакеты Armor Poser.
 */
@Environment(EnvType.CLIENT)
public final class ItemGiveService {

    /** Кэшированная ссылка на Services.PLATFORM объект Armor Poser */
    private static Object cachedPlatform = null;
    /** Кэшированный метод updateEntity(ArmorStand, CompoundTag) */
    private static Method cachedUpdateMethod = null;

    private ItemGiveService() {
        // Утилитный класс — конструктор закрыт
    }

    /**
     * Выдать предмет на стойку для брони через Armor Poser.
     * <p>
     * Устанавливает предмет в основную руку (MainHand) стойки на клиенте,
     * затем вызывает Services.PLATFORM.updateEntity() Armor Poser для синхронизации.
     * Armor Poser отправляет ПОЛНЫЙ CompoundTag стойки (включая HandItems) на сервер.
     *
     * @param stack      стак предмета для выдачи
     * @param rawSnbt    SNBT-строка для custom_data компонента (может быть пустой)
     * @param count      количество предметов (1-99)
     * @param armorStand целевая стойка для брони (может быть null — ищем по взгляду)
     * @return true, если выдача была выполнена
     */
    public static boolean give(ItemStack stack, String rawSnbt, int count,
                                ArmorStandEntity armorStand) {
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

        // Устанавливаем предмет в основную руку стойки НА КЛИЕНТЕ
        // После этого saveWithoutId() в updateEntity включит HandItems в CompoundTag
        target.equipStack(EquipmentSlot.MAINHAND, copy);

        // Вызываем Services.PLATFORM.updateEntity(armorStand, emptyCompound)
        // через рефлексию — это запускает полный цикл синхронизации Armor Poser:
        // saveWithoutId → merge → load → send ArmorStandSyncPayload
        // ПОЛНЫЙ CompoundTag (с HandItems) отправляется на сервер
        return callPlatformUpdateEntity(target, new NbtCompound());
    }

    /**
     * Вызвать Services.PLATFORM.updateEntity(ArmorStand, CompoundTag) через рефлексию.
     * <p>
     * Services — класс из Armor Poser (com.mrbysco.armorposer.platform.Services).
     * Services.PLATFORM — статическое поле типа IPlatformHelper.
     * На Fabric реализация — FabricPlatformHelper, метод updateEntity выполняет:
     * <ol>
     *   <li>saveWithoutId() — сохраняет ВСЁ состояние стойки (включая HandItems)</li>
     *   <li>merge(compound) — объединяет с переданным CompoundTag</li>
     *   <li>load() — загружает обновлённое состояние на клиенте</li>
     *   <li>Отправляет ПОЛНЫЙ outputCompound через ArmorStandSyncPayload</li>
     * </ol>
     * <p>
     * Используется рефлексия, т.к. Armor Poser — опциональная зависимость.
     * На уровне рантайма NbtCompound (Yarn) = CompoundTag (Mojang) — один класс.
     *
     * @param armorStand стойка для брони
     * @param compound   NBT-данные для merge (может быть пустым — тогда используется текущее состояние)
     * @return true, если вызов прошёл успешно
     */
    private static boolean callPlatformUpdateEntity(ArmorStandEntity armorStand, NbtCompound compound) {
        try {
            // Получаем Services.PLATFORM (кэшируем для повторных вызовов)
            if (cachedPlatform == null || cachedUpdateMethod == null) {
                // Ищем класс Services из Armor Poser
                Class<?> servicesClass = Class.forName("com.mrbysco.armorposer.platform.Services");

                // Получаем статическое поле PLATFORM
                Field platformField = servicesClass.getField("PLATFORM");
                cachedPlatform = platformField.get(null);

                if (cachedPlatform == null) {
                    return false;
                }

                // Ищем метод updateEntity с двумя параметрами (ArmorStand, CompoundTag)
                for (Method m : cachedPlatform.getClass().getMethods()) {
                    if ("updateEntity".equals(m.getName()) && m.getParameterCount() == 2) {
                        cachedUpdateMethod = m;
                        break;
                    }
                }

                if (cachedUpdateMethod == null) {
                    return false;
                }
            }

            // Вызываем updateEntity(armorStand, compound)
            // ArmorStandEntity (Yarn) = ArmorStand (Mojang) на уровне рантайма
            // NbtCompound (Yarn) = CompoundTag (Mojang) на уровне рантайма
            cachedUpdateMethod.invoke(cachedPlatform, armorStand, compound);
            return true;

        } catch (ClassNotFoundException e) {
            // Armor Poser не установлен — класс Services не найден
            return false;
        } catch (Exception e) {
            // Ошибка рефлексии — сбрасываем кэш для повторной попытки
            cachedPlatform = null;
            cachedUpdateMethod = null;
            return false;
        }
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
