package com.dupe.posergive.integration;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Armor Poser-like payload model:
 *   record SyncData(UUID entityUUID, CompoundTag tag)
 */
public record SyncData(UUID entityUUID, NbtCompound tag) {
    public void write(PacketByteBuf buf) {
        buf.writeUuid(entityUUID);
        buf.writeNbt(tag);
    }

    public static SyncData read(PacketByteBuf buf) {
        return new SyncData(buf.readUuid(), buf.readNbt());
    }
}
