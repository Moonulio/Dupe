package com.dupe.posergive.integration;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Bridge to Armor Poser networking.
 *
 * This class stays client-only and sends a custom packet that Armor Poser can consume.
 */
public final class ArmorPoserBridge {
    private static final Identifier CHANNEL = Identifier.of("armorposer", "pose_action");

    private ArmorPoserBridge() {
    }

    public static boolean isArmorPoserLoaded() {
        return FabricLoader.getInstance().isModLoaded("armorposer");
    }

    public static void giveToFocusedArmorStand(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (!(client.crosshairTarget instanceof EntityHitResult ehr)
            || ehr.getType() != HitResult.Type.ENTITY
            || !(ehr.getEntity() instanceof ArmorStandEntity stand)) {
            client.player.sendMessage(Text.literal("[PoserGive] Наведитесь на стойку для брони."), true);
            return;
        }

        if (!isArmorPoserLoaded()) {
            client.player.sendMessage(Text.literal("[PoserGive] Armor Poser не найден."), true);
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(stand.getId());
        buf.writeItemStack(stack);
        ClientPlayNetworking.send(CHANNEL, buf);
        client.player.sendMessage(Text.literal("[PoserGive] Отправлен предмет в руку стойки: " + stack.getName().getString()), true);
    }
}
