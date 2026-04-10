package com.dupe.posergive.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Client-only bridge for Armor Poser flow where a player chat payload contains
 * a CompoundTag that server-side Armor Poser applies to an armor stand template.
 */
public final class ArmorPoserBridge {
    private ArmorPoserBridge() {
    }

    public static boolean isArmorPoserLoaded() {
        return FabricLoader.getInstance().isModLoaded("armorposer");
    }

    /**
     * Builds a minimal armor-stand NBT patch that sets MAINHAND item count/id.
     * User can further edit it in raw form before sending.
     */
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
     * Sends raw nbt text as chat message (Armor Poser server-side parser target).
     */
    public static void sendRawCompoundTag(String rawTag) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
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

        client.getNetworkHandler().sendChatMessage(rawTag);
        client.player.sendMessage(Text.literal("[PoserGive] CompoundTag отправлен в чат-канал Armor Poser."), true);
    }

    public static boolean isLookingAtArmorStand() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return false;
        }

        return (client.crosshairTarget instanceof EntityHitResult ehr)
            && ehr.getType() == HitResult.Type.ENTITY
            && (ehr.getEntity() instanceof ArmorStandEntity);
    }
}
