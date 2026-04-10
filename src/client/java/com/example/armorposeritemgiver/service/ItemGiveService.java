package com.example.armorposeritemgiver.service;

import com.example.armorposeritemgiver.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Выдача предметов.
 *
 * 1) В creative: штатно через clickCreativeStack + CreativeInventoryActionC2SPacket.
 * 2) В survival/adventure: "unsafe" режим для серверов, где разрешены уязвимые client-driven
 *    обновления инвентаря (или где это проксируется модом стойки/Armor Poser).
 */
public final class ItemGiveService {
    private ItemGiveService() {
    }

    public static boolean give(ItemStack stack, int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;

        if (player == null || interactionManager == null) {
            return false;
        }

        ItemStack copy = stack.copy();

        if (player.getAbilities().creativeMode) {
            interactionManager.clickCreativeStack(copy, slot);
            player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot, copy));
            return true;
        }

        if (!ModConfig.get().allowUnsafeWithoutCreative) {
            return false;
        }

        // Нон-креатив сценарий: разрешаем попытку только когда игрок смотрит на стойку,
        // чтобы использовать workflow "через стойку".
        if (!isLookingAtArmorStand(client)) {
            return false;
        }

        // На части серверов это будет отклонено (и это нормально), но на серверах/модпаках,
        // где Armor Poser или иная логика проксирует обновление, предмет может примениться.
        player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot, copy));
        return true;
    }

    private static boolean isLookingAtArmorStand(MinecraftClient client) {
        HitResult hitResult = client.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return false;
        }
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return false;
        }
        return entityHitResult.getEntity() instanceof ArmorStandEntity;
    }
}
