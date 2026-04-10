package com.dupe.posergive.gui;

import com.dupe.posergive.config.PoserGiveConfig;
import com.dupe.posergive.integration.ArmorPoserBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Searchable item picker with unlimited NBT input line and count editor.
 */
public class ItemPickerScreen extends Screen {
    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();

    private TextFieldWidget searchField;
    private TextFieldWidget nbtField;
    private TextFieldWidget countField;

    private ItemCategory category = ItemCategory.ALL;
    private int scroll = 0;

    public ItemPickerScreen() {
        super(Text.literal("PoserGive"));
    }

    @Override
    protected void init() {
        super.init();

        if (allItems.isEmpty()) {
            allItems.addAll(Registries.ITEM.getIds().stream()
                .filter(id -> !PoserGiveConfig.INSTANCE.blacklist.contains(id.toString()))
                .map(ItemEntry::of)
                .sorted(Comparator.comparing(entry -> entry.id().toString()))
                .toList());
        }

        searchField = new TextFieldWidget(this.textRenderer, 10, 20, this.width - 20, 20, Text.literal("Search"));
        searchField.setChangedListener(v -> refilter());
        addDrawableChild(searchField);

        nbtField = new TextFieldWidget(this.textRenderer, 10, 45, this.width - 20, 20, Text.literal("NBT"));
        nbtField.setMaxLength(Integer.MAX_VALUE);
        nbtField.setPlaceholder(Text.literal("NBT/components string (без ограничений длины)"));
        addDrawableChild(nbtField);

        countField = new TextFieldWidget(this.textRenderer, 10, 70, 80, 20, Text.literal("Count"));
        countField.setText("1");
        addDrawableChild(countField);

        int x = 100;
        for (ItemCategory c : ItemCategory.values()) {
            ItemCategory local = c;
            addDrawableChild(ButtonWidget.builder(Text.literal(c.name()), b -> {
                category = local;
                refilter();
            }).dimensions(x, 70, 70, 20).build());
            x += 74;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Выдать в руку стойки"), b -> tryGive())
            .dimensions(this.width - 190, 70, 180, 20).build());

        refilter();
    }

    private void tryGive() {
        if (filteredItems.isEmpty()) {
            return;
        }

        ItemEntry selected = filteredItems.get(Math.min(scroll, filteredItems.size() - 1));
        try {
            int count = Integer.parseInt(countField.getText().trim());
            ItemStack stack = new ItemStack(selected.item(), Math.max(1, count));
            String nbt = nbtField.getText();
            if (!nbt.isBlank()) {
                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(net.minecraft.nbt.StringNbtReader.parse(nbt)));
            }
            ArmorPoserBridge.giveToFocusedArmorStand(stack);
        } catch (Exception e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("[PoserGive] Ошибка NBT/count: " + e.getMessage()), true);
            }
        }
    }

    private void refilter() {
        String query = searchField == null ? "" : searchField.getText().toLowerCase(Locale.ROOT);
        filteredItems.clear();
        filteredItems.addAll(allItems.stream()
            .filter(entry -> category.matches(entry.item()))
            .filter(entry -> query.isBlank() || entry.searchableText().contains(query))
            .collect(Collectors.toList()));
        scroll = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!filteredItems.isEmpty()) {
            int max = Math.max(0, filteredItems.size() - 12);
            scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount), 0, max);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawText(textRenderer, "Поиск предметов (включая модовые)", 10, 6, 0xFFFFFF, false);
        context.drawText(textRenderer, "Выбранная категория: " + category.name(), 10, 96, 0xAAAAAA, false);

        int y = 112;
        int from = scroll;
        int to = Math.min(filteredItems.size(), from + 12);
        for (int i = from; i < to; i++) {
            ItemEntry entry = filteredItems.get(i);
            boolean hovered = mouseX >= 10 && mouseX <= this.width - 10 && mouseY >= y && mouseY <= y + 12;
            int color = hovered ? 0xFFFF88 : 0xFFFFFF;
            Identifier id = entry.id();
            context.drawText(textRenderer, id.toString(), 10, y, color, false);
            y += 12;
        }

        context.drawText(textRenderer, "Примечание: сервер должен принимать пакет Armor Poser.", 10, this.height - 12, 0x888888, false);
    }
}
