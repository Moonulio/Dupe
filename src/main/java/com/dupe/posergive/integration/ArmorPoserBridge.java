package com.dupe.posergive.integration;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Client-only bridge for Armor Poser-like SyncData(UUID, CompoundTag) C2S flow.
 */
public final class ArmorPoserBridge {
    private static final Identifier CHANNEL = Identifier.of("armorposer", "sync_data");

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

    public static void sendRawCompoundTag(String rawTag) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (!isArmorPoserLoaded()) {
            client.player.sendMessage(Text.literal("[PoserGive] Armor Poser не найден на клиенте."), true);
            return;
        }

        if (!(client.crosshairTarget instanceof EntityHitResult ehr)
            || ehr.getType() != HitResult.Type.ENTITY
            || !(ehr.getEntity() instanceof ArmorStandEntity stand)) {
            client.player.sendMessage(Text.literal("[PoserGive] Наведитесь на ArmorStand перед отправкой."), true);
            return;
        }

        try {
            NbtCompound tag = StringNbtReader.parse(rawTag);

        if (!ClientPlayNetworking.canSend(CHANNEL)) {
            client.player.sendMessage(Text.literal("[PoserGive] Сервер не зарегистрировал канал armorposer:sync_data (отправка отменена)."), true);
            return;
        }

            SyncData syncData = new SyncData(stand.getUuid(), tag);

            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            syncData.write(buf);
            ClientPlayNetworking.send(CHANNEL, buf);

            client.player.sendMessage(Text.literal("[PoserGive] SyncData(UUID + CompoundTag) отправлен на сервер."), true);
        } catch (Exception e) {
            client.player.sendMessage(Text.literal("[PoserGive] Невалидный CompoundTag: " + e.getMessage()), true);
        }
    }
}
