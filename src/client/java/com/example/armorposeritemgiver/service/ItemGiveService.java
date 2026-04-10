package com.example.armorposeritemgiver.service;

import com.example.armorposeritemgiver.config.ModConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Выдача предметов/применение к стойке.
 */
public final class ItemGiveService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemGiveService.class);

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

        // Legacy unsafe packet fallback is disabled by default.
        // Sending creative packets outside creative mode can be exploited for item duplication
        // on servers that do not properly validate game mode.
        if (!ModConfig.get().allowUnsafeWithoutCreative || !isLookingAtArmorStand(client)) {
            return false;
        }

        LOGGER.warn("Using unsafe creative-packet fallback outside creative mode");
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

        String components;
        if (rawSnbtComponents == null || rawSnbtComponents.isBlank()) {
            components = "{}";
        } else {
            String trimmed = rawSnbtComponents.trim();
            try {
                // Validate that the input is well-formed SNBT before embedding in the command
                StringNbtReader.parse(trimmed);
                components = trimmed;
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Invalid SNBT input rejected: {}", e.getMessage());
                return false;
            }
        }

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
