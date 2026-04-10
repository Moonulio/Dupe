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
 * CompoundTag отправленный игроком не проверяется сервером на правдоподность —
 * можно указать любой предмет с любым количеством и это будет применено к стойке.
 * Например: 64 алмазных блока в руке стойки.
 * <p>
 * Также устанавливает предмет на клиентскую сущность через equipStack()
 * для немедленного визуального отображения.
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

        // Устанавливаем предмет на клиентскую сущность для немедленного отображения
        target.equipStack(EquipmentSlot.MAINHAND, copy);

        // Формируем CompoundTag с HandItems и отправляем через Armor Poser
        RegistryWrapper.WrapperLookup registries = client.world.getRegistryManager();
        NbtCompound compound = buildHandItemsCompound(copy, registries);

        return sendArmorStandSync(target.getUuid(), compound);
    }

    /**
     * Построить CompoundTag с ключом "HandItems" для отправки на сервер.
     * <p>
     * HandItems — это список из двух предметов: [MainHand, OffHand].
     * Предмет сериализуется через ItemStack.toNbt() (Yarn 1.21.4).
     *
     * @param mainHand   предмет для основной руки стойки
     * @param registries реестр для сериализации предмета
     * @return CompoundTag вида {HandItems: [{id:"...", count:N, ...}, {}]}
     */
    private static NbtCompound buildHandItemsCompound(ItemStack mainHand,
                                                       RegistryWrapper.WrapperLookup registries) {
        NbtList handItems = new NbtList();

        // Основная рука — сериализуем предмет в NBT
        NbtElement mainHandNbt = mainHand.toNbt(registries);
        handItems.add(mainHandNbt);

        // Вторая рука — пустой CompoundTag (OffHand остаётся как есть)
        handItems.add(new NbtCompound());

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
