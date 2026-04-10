package com.example.armorposeritemgiver.service;

import com.example.armorposeritemgiver.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Выдача предметов/применение к стойке.
 */
public final class ItemGiveService {
    private ItemGiveService() {
    }

    public static boolean give(ItemStack stack, int slot, String rawSnbtComponents) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;

        if (player == null || interactionManager == null) {
            return false;
        }

        ItemStack copy = stack.copy();

        // 1) Обычный creative путь
        if (player.getAbilities().creativeMode) {
            interactionManager.clickCreativeStack(copy, slot);
            player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot, copy));
            return true;
        }

        // 2) Без creative: пытаемся изменить NBT стойки через команду data merge.
        // Требует прав (op/cheats) на стороне сервера, но работает в survival.
        if (tryApplyToLookedArmorStandViaCommand(copy, rawSnbtComponents)) {
            return true;
        }

        // 3) Legacy unsafe пакетный fallback (как было ранее), только если включено в конфиге.
        if (!ModConfig.get().allowUnsafeWithoutCreative || !isLookingAtArmorStand(client)) {
            return false;
        }

        player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot, copy));
        return true;
    }

    private static boolean tryApplyToLookedArmorStandViaCommand(ItemStack stack, String rawSnbtComponents) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        ArmorStandEntity stand = getLookedArmorStand(client);
        if (player == null || stand == null) {
            return false;
        }

        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id == null) {
            return false;
        }

        String components = rawSnbtComponents == null || rawSnbtComponents.isBlank()
                ? "{}"
                : rawSnbtComponents.trim();

        // Для 1.21+ item данные в командах используют components.
        String handItem = "{id:\"" + id + "\",count:" + stack.getCount() + ",components:" + components + "}";
        String cmd = "data merge entity " + stand.getUuidAsString() + " {Invisible:0b,Marker:0b,HandItems:[" + handItem + ",{}]}";

        player.networkHandler.sendChatCommand(cmd);
        return true;
    }

    private static boolean isLookingAtArmorStand(MinecraftClient client) {
        return getLookedArmorStand(client) != null;
    }

    private static ArmorStandEntity getLookedArmorStand(MinecraftClient client) {
        HitResult hitResult = client.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }
        if (entityHitResult.getEntity() instanceof ArmorStandEntity stand) {
            return stand;
        }
        return null;
    }
}
