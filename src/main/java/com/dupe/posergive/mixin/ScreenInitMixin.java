package com.dupe.posergive.mixin;

import com.dupe.posergive.gui.ItemPickerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a small button into Armor Poser screens using a generic screen mixin,
 * so the addon stays binary-compatible even when Armor Poser updates internals.
 */
@Mixin(Screen.class)
public abstract class ScreenInitMixin {
    @Shadow protected int width;
    @Shadow protected int height;

    @Inject(method = "init", at = @At("TAIL"))
    private void posergive$addButton(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        String className = self.getClass().getName().toLowerCase();
        if (!className.contains("armorposer")) {
            return;
        }

        ButtonWidget btn = ButtonWidget.builder(Text.literal("PoserGive"), b ->
            MinecraftClient.getInstance().setScreen(new ItemPickerScreen())
        ).dimensions(width - 88, height - 24, 80, 20).build();

        self.addDrawableChild(btn);
    }
}
