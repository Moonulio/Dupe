package com.dupe.posergive.client;

import com.dupe.posergive.config.PoserGiveConfig;
import com.dupe.posergive.gui.ItemPickerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint.
 */
public class PoserGiveClient implements ClientModInitializer {
    public static final String MOD_ID = "posergive";
    private static KeyBinding openPickerKey;

    @Override
    public void onInitializeClient() {
        PoserGiveConfig.load();

        openPickerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.posergive.open_picker",
            InputUtil.Type.KEYSYM,
            PoserGiveConfig.INSTANCE.openPickerKey,
            "category.posergive"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        while (openPickerKey.wasPressed()) {
            if (client.player != null && client.world != null) {
                client.setScreen(new ItemPickerScreen());
            }
        }
    }
}
