package com.dupemod.client.util;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

/**
 * Оверлей для отображения переливающегося текста "Исполнено" над хотбаром.
 * Текст постепенно появляется и исчезает с радужным эффектом.
 */
public class StatusOverlay {
    private static long displayStartTime = 0;
    private static final long DISPLAY_DURATION_MS = 3000; // 3 секунды показа
    private static boolean isActive = false;
    private static boolean registered = false;

    /**
     * Регистрирует HUD-оверлей. Вызывается один раз при инициализации мода.
     */
    public static void register() {
        if (registered) return;
        registered = true;
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            renderOverlay(drawContext, tickCounter);
        });
    }

    /**
     * Активирует отображение текста "Исполнено".
     */
    public static void showSuccess() {
        displayStartTime = System.currentTimeMillis();
        isActive = true;
    }

    /**
     * Рендеринг оверлея с переливающимся текстом.
     */
    private static void renderOverlay(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!isActive) return;

        long elapsed = System.currentTimeMillis() - displayStartTime;
        if (elapsed > DISPLAY_DURATION_MS) {
            isActive = false;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        TextRenderer textRenderer = client.textRenderer;
        String text = "\u0418\u0441\u043f\u043e\u043b\u043d\u0435\u043d\u043e";
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Позиция: над хотбаром, по центру экрана
        int textWidth = textRenderer.getWidth(text);
        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - 59; // Над хотбаром

        // Вычисляем прозрачность (плавное появление и исчезание)
        float alpha;
        float progress = (float) elapsed / DISPLAY_DURATION_MS;
        if (progress < 0.1f) {
            // Появление (первые 10% времени)
            alpha = progress / 0.1f;
        } else if (progress > 0.7f) {
            // Исчезание (последние 30% времени)
            alpha = (1.0f - progress) / 0.3f;
        } else {
            alpha = 1.0f;
        }
        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        int alphaInt = (int) (alpha * 255);
        if (alphaInt < 4) return;

        // Рисуем каждую букву с переливающимся цветом (радужный эффект)
        float time = (float) elapsed / 500.0f; // Скорость переливания
        int currentX = x;
        for (int i = 0; i < text.length(); i++) {
            // Радужный цвет с фазовым сдвигом для каждой буквы
            float hue = (time + i * 0.15f) % 1.0f;
            int color = hsvToRgb(hue, 0.8f, 1.0f);
            // Добавляем альфа-канал
            color = (alphaInt << 24) | (color & 0x00FFFFFF);

            String ch = String.valueOf(text.charAt(i));
            // Тень для лучшей читаемости
            drawContext.drawText(textRenderer, ch, currentX + 1, y + 1, (alphaInt << 24) | 0x000000, false);
            drawContext.drawText(textRenderer, ch, currentX, y, color, false);
            currentX += textRenderer.getWidth(ch);
        }
    }

    /**
     * Конвертация HSV в RGB цвет.
     */
    private static int hsvToRgb(float hue, float saturation, float value) {
        int h = (int) (hue * 6) % 6;
        float f = hue * 6 - (int) (hue * 6);
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);

        float r, g, b;
        switch (h) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }

        int ri = (int) (r * 255);
        int gi = (int) (g * 255);
        int bi = (int) (b * 255);
        return (ri << 16) | (gi << 8) | bi;
    }
}
