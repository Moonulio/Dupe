package com.example.armorposeritemgiver.mixin;

import com.example.armorposeritemgiver.screen.ItemBrowserScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
 * Миксин для интеграции с Armor Poser.
 * <p>
 * Использует аннотацию {@code @Pseudo}, чтобы мод корректно работал
 * даже без установленного Armor Poser — миксин просто не применяется.
 * <p>
 * Инжектирует кнопку "Item Giver" в экран Armor Poser ({@code ArmorPoserScreen})
 * в конце метода {@code init()}. При нажатии кнопки открывается
 * {@link ItemBrowserScreen} — окно поиска и выдачи предметов.
 */
@Environment(EnvType.CLIENT)
@Pseudo // Soft-target: мод работает без Armor Poser (миксин игнорируется)
@Mixin(targets = "dev.tr7zw.armorposer.gui.ArmorPoserScreen")
public abstract class ArmorPoserScreenMixin extends Screen {

    /**
     * Конструктор-заглушка (требуется для extends Screen в миксине).
     *
     * @param title заголовок экрана
     */
    protected ArmorPoserScreenMixin(Text title) {
        super(title);
    }

    /**
     * Инжектирует кнопку "Item Giver" в конец метода init() экрана Armor Poser.
     * Кнопка располагается в правом верхнем углу экрана.
     *
     * @param ci информация о callback'е (не используется)
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void armorPoserItemGiver$addButton(CallbackInfo ci) {
        // Позиция кнопки: правый верхний угол с отступом
        int x = this.width - 128;
        int y = 8;

        // Создаём и добавляем кнопку "Item Giver"
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Item Giver"),
                button -> {
                    // При нажатии — открываем экран Item Browser
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new ItemBrowserScreen(this));
                }
        ).position(x, y).size(120, 20).build());
    }
}
