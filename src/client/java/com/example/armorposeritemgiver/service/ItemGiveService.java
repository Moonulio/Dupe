package com.example.armorposeritemgiver.service;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.lang.reflect.Constructor;
import java.util.UUID;

/**
 * Сервис выдачи предметов на стойку для брони через Armor Poser.
 * <p>
 * Механизм работы — напрямую через SyncData и ArmorStandSyncPayload:
 * <ol>
 *   <li>Формирует CompoundTag с ключом HandItems, содержащим нужный предмет</li>
 *   <li>Создаёт SyncData(UUID entityUUID, CompoundTag tag) через рефлексию</li>
 *   <li>Оборачивает в ArmorStandSyncPayload и отправляет через ClientPlayNetworking.send()</li>
 *   <li>Сервер получает CompoundTag и применяет данные к стойке для брони</li>
 * </ol>
 * <p>
 * <b>ВАЖНО:</b> В Armor Poser 8.0.1+ для MC 1.21.4 на сервере добавлена фильтрация
 * allowedKeys в SyncData.handleData(). Ключ "HandItems" НЕ входит в allowedKeys,
 * поэтому на серверах с новой версией AP HandItems будет отфильтрован.
 * На серверах со СТАРОЙ версией AP (без фильтрации) CompoundTag принимается целиком
 * и предметы выдаются корректно.
 * <p>
 * Предмет НЕ устанавливается на клиентскую сущность (без equipStack()),
 * чтобы избежать "фантомных" предметов, которые видны только на клиенте.
 * Если сервер принял HandItems — он сам синхронизирует стойку обратно клиенту.
 * <p>
 * НЕ использует /data merge entity.
 * НЕ использует CreativeInventoryActionC2SPacket.
 * НЕ использует Services.PLATFORM.updateEntity().
 * Работает без оператора и креативного режима — через пакеты Armor Poser напрямую.
 */
@Environment(EnvType.CLIENT)
public final class ItemGiveService {

    /**
     * Кэшированный конструктор SyncData(UUID, CompoundTag).
     * SyncData — record из Armor Poser: com.mrbysco.armorposer.data.SyncData
     */
    private static Constructor<?> cachedSyncDataCtor = null;

    /**
     * Кэшированный конструктор ArmorStandSyncPayload(SyncData).
     * ArmorStandSyncPayload — пакет из Armor Poser: com.mrbysco.armorposer.packets.ArmorStandSyncPayload
     */
    private static Constructor<?> cachedPayloadCtor = null;

    private ItemGiveService() {
        // Утилитный класс — конструктор закрыт
    }

    /**
     * Выдать предмет на стойку для брони через Armor Poser.
     * <p>
     * Формирует CompoundTag с HandItems и отправляет напрямую через
     * ArmorStandSyncPayload → ClientPlayNetworking.send().
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
        if (client.player == null || client.world == null) {
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
                NbtCompound parsed = StringNbtReader.parse(rawSnbt);
                copy.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                        net.minecraft.component.type.NbtComponent.of(parsed));
            } catch (Exception ignored) {
                // Некорректный SNBT — игнорируем, предмет всё равно будет выдан
            }
        }

        // НЕ вызываем equipStack() на клиенте — это создавало "фантомные" предметы,
        // которые видны только на клиенте, но отсутствуют на сервере.
        // Если сервер примет HandItems из нашего CompoundTag, он сам синхронизирует
        // стойку обратно клиенту через EntityEquipmentUpdateS2CPacket.

        // Формируем CompoundTag с HandItems и отправляем через Armor Poser
        RegistryWrapper.WrapperLookup registries = client.world.getRegistryManager();
        NbtCompound compound = buildHandItemsCompound(copy, target, registries);

        return sendArmorStandSync(target.getUuid(), compound);
    }

    /**
     * Построить CompoundTag с ключом "HandItems" для отправки на сервер.
     * <p>
     * HandItems — это список из двух предметов: [MainHand, OffHand].
     * Предмет сериализуется через ItemStack.toNbt() (Yarn 1.21.4).
     * <p>
     * Для OffHand берём текущий предмет стойки, чтобы не затирать его при выдаче
     * в основную руку.
     *
     * @param mainHand   предмет для основной руки стойки
     * @param target     целевая стойка для брони (для чтения текущего OffHand)
     * @param registries реестр для сериализации предметов
     * @return CompoundTag вида {HandItems: [{mainHand NBT}, {offHand NBT}]}
     */
    private static NbtCompound buildHandItemsCompound(ItemStack mainHand,
                                                       ArmorStandEntity target,
                                                       RegistryWrapper.WrapperLookup registries) {
        NbtList handItems = new NbtList();

        // Основная рука — сериализуем новый предмет в NBT
        NbtElement mainHandNbt = mainHand.toNbt(registries);
        handItems.add(mainHandNbt);

        // Вторая рука — сохраняем текущий предмет стойки, чтобы не затереть его
        ItemStack currentOffHand = target.getEquippedStack(EquipmentSlot.OFFHAND);
        if (currentOffHand != null && !currentOffHand.isEmpty()) {
            handItems.add(currentOffHand.toNbt(registries));
        } else {
            handItems.add(new NbtCompound());
        }

        NbtCompound compound = new NbtCompound();
        compound.put("HandItems", handItems);

        return compound;
    }

    /**
     * Отправить ArmorStandSyncPayload напрямую через ClientPlayNetworking.
     * <p>
     * Создаёт через рефлексию:
     * <ol>
     *   <li>SyncData(UUID entityUUID, CompoundTag tag) — запись с UUID стойки и данными</li>
     *   <li>ArmorStandSyncPayload(SyncData data) — пакет Armor Poser</li>
     * </ol>
     * Затем отправляет через ClientPlayNetworking.send().
     * <p>
     * Рефлексия используется т.к. Armor Poser — опциональная зависимость.
     * На уровне рантайма NbtCompound (Yarn) = CompoundTag (Mojang) — один класс.
     *
     * @param entityUUID UUID стойки для брони
     * @param compound   CompoundTag с данными (HandItems и т.д.)
     * @return true, если пакет был отправлен успешно
     */
    private static boolean sendArmorStandSync(UUID entityUUID, NbtCompound compound) {
        try {
            // Кэшируем конструкторы при первом вызове
            if (cachedSyncDataCtor == null || cachedPayloadCtor == null) {
                // SyncData — record из Armor Poser
                // public record SyncData(UUID entityUUID, CompoundTag tag)
                Class<?> syncDataClass = Class.forName("com.mrbysco.armorposer.data.SyncData");
                for (Constructor<?> ctor : syncDataClass.getConstructors()) {
                    if (ctor.getParameterCount() == 2) {
                        cachedSyncDataCtor = ctor;
                        break;
                    }
                }

                // ArmorStandSyncPayload — пакет из Armor Poser
                // public record ArmorStandSyncPayload(SyncData data) implements CustomPayload
                Class<?> payloadClass = Class.forName("com.mrbysco.armorposer.packets.ArmorStandSyncPayload");
                for (Constructor<?> ctor : payloadClass.getConstructors()) {
                    if (ctor.getParameterCount() == 1) {
                        cachedPayloadCtor = ctor;
                        break;
                    }
                }

                if (cachedSyncDataCtor == null || cachedPayloadCtor == null) {
                    return false;
                }
            }

            // Создаём SyncData(uuid, compound)
            Object syncData = cachedSyncDataCtor.newInstance(entityUUID, compound);

            // Создаём ArmorStandSyncPayload(syncData)
            Object payload = cachedPayloadCtor.newInstance(syncData);

            // Отправляем через Fabric API
            // ArmorStandSyncPayload implements CustomPayload → безопасный каст
            ClientPlayNetworking.send((CustomPayload) payload);
            return true;

        } catch (ClassNotFoundException e) {
            // Armor Poser не установлен — классы SyncData/ArmorStandSyncPayload не найдены
            return false;
        } catch (Exception e) {
            // Ошибка рефлексии — сбрасываем кэш для повторной попытки
            cachedSyncDataCtor = null;
            cachedPayloadCtor = null;
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
