package com.example.armorposeritemgiver.mixin;

import com.example.armorposeritemgiver.screen.ItemBrowserScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Интеграция с Armor Poser через mixin в его screen.
 *
 * Важно: target указывается строкой и класс помечен @Pseudo,
 * чтобы мод компилировался и запускался даже без прямой compile-time зависимости на Armor Poser.
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.armorposer.gui.ArmorPoserScreen")
public abstract class ArmorPoserScreenMixin extends Screen {
    protected ArmorPoserScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void armorPoserItemGiver$addButton(CallbackInfo ci) {
        int x = this.width - 128;
        int y = 8;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Item Giver"), button -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new ItemBrowserScreen((Screen) (Object) this));
                }).position(x, y)
                .size(120, 20)
                .build());
    }
}
