package com.dupe.posergive.integration;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Client-only bridge for Armor Poser C2S payload flow.
 */
public final class ArmorPoserBridge {
    /**
     * Channel name expected by Armor Poser-side packet listener.
     * If your server build uses another id, change it here or move to config.
     */
    private static final Identifier CHANNEL = Identifier.of("armorposer", "compoundtag");

    private ArmorPoserBridge() {
    }

    public static boolean isArmorPoserLoaded() {
        return FabricLoader.getInstance().isModLoaded("armorposer");
    }

    public static String buildMainhandTemplate(ItemStack stack) {
        NbtCompound root = new NbtCompound();

        NbtList handItems = new NbtList();
        NbtCompound mainhand = new NbtCompound();
        mainhand.putString("id", Registries.ITEM.getId(stack.getItem()).toString());
        mainhand.putByte("Count", (byte) Math.max(1, stack.getCount()));

        if (stack.getComponents().contains(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)) {
            NbtCompound custom = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA).copyNbt();
            mainhand.put("tag", custom);
        }

        handItems.add(mainhand);
        handItems.add(new NbtCompound());

        root.put("HandItems", handItems);
        return root.asString();
    }

    /**
     * Sends raw armor-stand patch to server using Armor Poser custom payload.
     */
    public static void sendRawCompoundTag(String rawTag) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (!isArmorPoserLoaded()) {
            client.player.sendMessage(Text.literal("[PoserGive] Armor Poser не найден на клиенте."), true);
            return;
        }

        if (rawTag == null || rawTag.isBlank()) {
            client.player.sendMessage(Text.literal("[PoserGive] Пустой CompoundTag отправлять нельзя."), true);
            return;
        }

        int entityId = -1;
        if (client.crosshairTarget instanceof EntityHitResult ehr
            && ehr.getType() == HitResult.Type.ENTITY
            && ehr.getEntity() instanceof ArmorStandEntity stand) {
            entityId = stand.getId();
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entityId);
        buf.writeString(rawTag);
        ClientPlayNetworking.send(CHANNEL, buf);

        client.player.sendMessage(Text.literal("[PoserGive] CompoundTag отправлен на сервер через пакет Armor Poser."), true);
    }
}
