package com.example.armorposeritemgiver.mixin;

import com.example.armorposeritemgiver.screen.ItemBrowserScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Миксин для интеграции с Armor Poser.
 * <p>
 * Использует аннотацию {@code @Pseudo}, чтобы мод корректно работал
 * даже без установленного Armor Poser — миксин просто не применяется.
 * <p>
 * Инжектирует кнопку "Item Giver" в экран Armor Poser ({@code ArmorStandScreen})
 * в конце метода {@code init()}. При нажатии кнопки открывается
 * {@link ItemBrowserScreen} — окно поиска и выдачи предметов на стойку для брони.
 * <p>
 * Стойка для брони извлекается из экрана Armor Poser через рефлексию
 * (метод getArmorStandEntity() или поле entityArmorStand),
 * что позволяет передать её в ItemBrowserScreen для прямой выдачи предметов.
 */
@Environment(EnvType.CLIENT)
@Pseudo // Soft-target: мод работает без Armor Poser (миксин игнорируется)
@Mixin(targets = "com.mrbysco.armorposer.client.gui.ArmorStandScreen")
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
     * При нажатии извлекает стойку для брони из экрана Armor Poser
     * и открывает ItemBrowserScreen с привязкой к этой стойке.
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
                    // Извлекаем стойку для брони из экрана Armor Poser
                    ArmorStandEntity armorStand = extractArmorStand();

                    // Открываем экран Item Browser с привязкой к стойке
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new ItemBrowserScreen(this, armorStand));
                }
        ).position(x, y).size(120, 20).build());
    }

    /**
     * Извлекает стойку для брони из текущего экрана Armor Poser через рефлексию.
     * <p>
     * Пробует два подхода:
     * 1. Метод getArmorStandEntity() — публичный геттер в ArmorStandScreen
     * 2. Поле entityArmorStand — приватное поле класса
     * <p>
     * Используется рефлексия, т.к. миксин @Pseudo не позволяет @Shadow.
     *
     * @return ArmorStandEntity, если удалось извлечь, иначе null
     */
    private ArmorStandEntity extractArmorStand() {
        // Способ 1: Попытка вызвать метод getArmorStandEntity()
        try {
            Method getter = this.getClass().getMethod("getArmorStandEntity");
            Object result = getter.invoke(this);
            if (result instanceof ArmorStandEntity stand) {
                return stand;
            }
        } catch (Exception ignored) {
            // Метод не найден или ошибка — пробуем поле
        }

        // Способ 2: Попытка получить поле entityArmorStand
        try {
            Field field = this.getClass().getDeclaredField("entityArmorStand");
            field.setAccessible(true);
            Object result = field.get(this);
            if (result instanceof ArmorStandEntity stand) {
                return stand;
            }
        } catch (Exception ignored) {
            // Поле не найдено или ошибка
        }

        return null;
    }
}
