package com.dupemod.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Помощник для отправки пакетов синхронизации данных стойки для брони.
 * Использует сетевой протокол мода Armor Poser для отправки NBT-данных на сервер.
 * 
 * Формат пакета ArmorPoser SyncData:
 * - UUID entityUUID - идентификатор стойки для брони
 * - CompoundTag tag  - NBT-данные для применения к стойке
 * 
 * Сервер принимает данные и применяет их к стойке без проверки содержимого.
 */
public class ArmorStandPacketHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("armorposer_itemgiver");

    // Идентификатор пакета синхронизации из Armor Poser
    public static final Identifier SYNC_PACKET_ID = Identifier.of("armorposer", "sync_packet");

    /**
     * Payload-класс для пакета синхронизации Armor Poser.
     * Реализует CustomPayload для совместимости с Fabric Networking API.
     */
    public record SyncPayload(UUID entityUUID, NbtCompound tag) implements CustomPayload {
        public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(SYNC_PACKET_ID);

        public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.entityUUID());
                    buf.writeNbt(payload.tag());
                },
                buf -> new SyncPayload(buf.readUuid(), buf.readNbt())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Отправляет NBT-данные на сервер через пакет синхронизации Armor Poser.
     * Данные применяются к указанной стойке для брони.
     *
     * @param armorStand стойка для брони, к которой применяются данные
     * @param nbtData    NBT-данные для отправки
     */
    public static void sendSyncPacket(ArmorStandEntity armorStand, NbtCompound nbtData) {
        try {
            SyncPayload payload = new SyncPayload(armorStand.getUuid(), nbtData);
            ClientPlayNetworking.send(payload);
            LOGGER.info("[ArmorStandPacketHelper] Пакет синхронизации отправлен для стойки {}", armorStand.getUuid());
        } catch (Exception e) {
            LOGGER.error("[ArmorStandPacketHelper] Ошибка отправки пакета", e);
        }
    }

    /**
     * Формирует NBT-данные для установки предмета в руку стойки для брони.
     * Включает идентификатор предмета, количество и дополнительные NBT-теги.
     *
     * @param itemId    идентификатор предмета (например, "minecraft:diamond_block")
     * @param count     количество предметов
     * @param customNbt дополнительные NBT-данные предмета (может быть null)
     * @return NbtCompound с данными для отправки на сервер
     */
    public static NbtCompound buildItemHandNbt(String itemId, int count, String customNbt) {
        NbtCompound compound = new NbtCompound();

        // Показываем руки стойки
        compound.putBoolean("ShowArms", true);

        // Формируем данные о предмете в руке (HandItems)
        // HandItems - это список из 2 элементов: [главная_рука, вторичная_рука]
        net.minecraft.nbt.NbtList handItems = new net.minecraft.nbt.NbtList();

        // Предмет в главной руке
        NbtCompound mainHandItem = new NbtCompound();
        mainHandItem.putString("id", itemId);
        mainHandItem.putInt("count", count);

        // Если есть пользовательские NBT-данные, добавляем их как компоненты
        if (customNbt != null && !customNbt.isEmpty()) {
            try {
                NbtCompound customTag = net.minecraft.nbt.StringNbtReader.parse(customNbt);
                // Объединяем пользовательские данные с предметом
                for (String key : customTag.getKeys()) {
                    mainHandItem.put(key, customTag.get(key));
                }
            } catch (Exception e) {
                LOGGER.warn("[ArmorStandPacketHelper] Ошибка парсинга пользовательских NBT: {}", e.getMessage());
            }
        }

        handItems.add(mainHandItem);
        // Пустой слот для вторичной руки
        handItems.add(new NbtCompound());

        compound.put("HandItems", handItems);

        return compound;
    }

    /**
     * Формирует полные NBT-данные стойки для телепортации к игроку.
     * Изменяет позицию стойки через данные Move (смещение).
     *
     * @param offsetX смещение по X
     * @param offsetY смещение по Y
     * @param offsetZ смещение по Z
     * @return NbtCompound с данными смещения
     */
    public static NbtCompound buildMoveNbt(double offsetX, double offsetY, double offsetZ) {
        NbtCompound compound = new NbtCompound();

        net.minecraft.nbt.NbtList moveTag = new net.minecraft.nbt.NbtList();
        moveTag.add(net.minecraft.nbt.NbtDouble.of(offsetX));
        moveTag.add(net.minecraft.nbt.NbtDouble.of(offsetY));
        moveTag.add(net.minecraft.nbt.NbtDouble.of(offsetZ));
        compound.put("Move", moveTag);

        return compound;
    }

    /**
     * Формирует полный NBT для стойки, включая предмет и позицию.
     * Объединяет данные предмета и телепортации в один пакет.
     */
    public static NbtCompound buildFullNbt(String itemId, int count, String customNbt,
                                            double moveX, double moveY, double moveZ) {
        NbtCompound compound = buildItemHandNbt(itemId, count, customNbt);

        // Добавляем данные перемещения
        net.minecraft.nbt.NbtList moveTag = new net.minecraft.nbt.NbtList();
        moveTag.add(net.minecraft.nbt.NbtDouble.of(moveX));
        moveTag.add(net.minecraft.nbt.NbtDouble.of(moveY));
        moveTag.add(net.minecraft.nbt.NbtDouble.of(moveZ));
        compound.put("Move", moveTag);

        // Базовые параметры отображения
        compound.putBoolean("ShowArms", true);
        compound.putBoolean("NoGravity", true);

        return compound;
    }
}
