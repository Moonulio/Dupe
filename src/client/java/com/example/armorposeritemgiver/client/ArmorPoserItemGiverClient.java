package com.example.armorposeritemgiver.client;

import com.example.armorposeritemgiver.config.ModConfig;
import com.example.armorposeritemgiver.screen.ItemBrowserScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Точка входа клиентского мода.
 */
public class ArmorPoserItemGiverClient implements ClientModInitializer {
    public static final String MOD_ID = "armorposer-itemgiver";
    private static KeyBinding openBrowserKey;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        openBrowserKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.armorposer-itemgiver.open_browser",
                InputUtil.Type.KEYSYM,
                ModConfig.get().openScreenKey,
                "category.armorposer-itemgiver"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        while (openBrowserKey.wasPressed()) {
            if (client.player == null) {
                continue;
            }
            client.setScreen(new ItemBrowserScreen(client.currentScreen));
        }
    }

    public static int defaultKey() {
        return GLFW.GLFW_KEY_P;
    }
}
