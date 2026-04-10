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
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Creative-like item picker: icon grid + search + categories.
 */
public class ItemPickerScreen extends Screen {
    private static final int SLOT = 18;
    private static final int COLS = 9;
    private static final int ROWS = 5;

    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();

    private TextFieldWidget searchField;
    private TextFieldWidget nbtField;
    private TextFieldWidget countField;
    private TextFieldWidget rawCompoundField;

    private ItemCategory category = ItemCategory.ALL;
    private int scrollRow = 0;
    private ItemEntry selected;

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
        nbtField.setPlaceholder(Text.literal("NBT тега предмета (без лимита длины)"));
        addDrawableChild(nbtField);

        countField = new TextFieldWidget(this.textRenderer, 10, 70, 60, 20, Text.literal("Count"));
        countField.setText("64");
        addDrawableChild(countField);

        int x = 80;
        for (ItemCategory c : ItemCategory.values()) {
            ItemCategory local = c;
            addDrawableChild(ButtonWidget.builder(Text.literal(c.name()), b -> {
                category = local;
                refilter();
            }).dimensions(x, 70, 65, 20).build());
            x += 69;
        }

        rawCompoundField = new TextFieldWidget(this.textRenderer, 10, 95, this.width - 20, 20, Text.literal("Compound"));
        rawCompoundField.setMaxLength(Integer.MAX_VALUE);
        rawCompoundField.setPlaceholder(Text.literal("Raw CompoundTag для отправки на сервер (без лимита длины)"));
        addDrawableChild(rawCompoundField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Сгенерировать"), b -> generateCompoundFromSelected())
            .dimensions(this.width - 220, 120, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отправить на сервер"), b -> sendCompound())
            .dimensions(this.width - 115, 120, 105, 20).build());

        refilter();
    }

    private void refilter() {
        String query = searchField == null ? "" : searchField.getText().toLowerCase(Locale.ROOT);
        filteredItems.clear();
        filteredItems.addAll(allItems.stream()
            .filter(entry -> category.matches(entry.item()))
            .filter(entry -> query.isBlank() || entry.searchableText().contains(query))
            .collect(Collectors.toList()));

        scrollRow = 0;
        selected = filteredItems.isEmpty() ? null : filteredItems.getFirst();
        generateCompoundFromSelected();
    }

    private void generateCompoundFromSelected() {
        if (selected == null) {
            return;
        }

        try {
            int count = Integer.parseInt(countField.getText().trim());
            ItemStack stack = new ItemStack(selected.item(), Math.max(1, count));
            String nbt = nbtField.getText();
            if (!nbt.isBlank()) {
                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(net.minecraft.nbt.StringNbtReader.parse(nbt)));
            }
            rawCompoundField.setText(ArmorPoserBridge.buildMainhandTemplate(stack));
        } catch (Exception e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("[PoserGive] Ошибка генерации: " + e.getMessage()), true);
            }
        }
    }

    private void sendCompound() {
        ArmorPoserBridge.sendRawCompoundTag(rawCompoundField.getText());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int gridX = 10;
            int gridY = 150;
            int start = scrollRow * COLS;
            int maxVisible = COLS * ROWS;

            for (int i = 0; i < maxVisible; i++) {
                int index = start + i;
                if (index >= filteredItems.size()) {
                    break;
                }
                int cx = gridX + (i % COLS) * SLOT;
                int cy = gridY + (i / COLS) * SLOT;
                if (mouseX >= cx && mouseX <= cx + 16 && mouseY >= cy && mouseY <= cy + 16) {
                    selected = filteredItems.get(index);
                    generateCompoundFromSelected();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int totalRows = (int) Math.ceil(filteredItems.size() / (double) COLS);
        int maxRow = Math.max(0, totalRows - ROWS);
        scrollRow = MathHelper.clamp(scrollRow - (int) Math.signum(verticalAmount), 0, maxRow);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawText(textRenderer, "Выбор предмета (как креатив-сетка)", 10, 6, 0xFFFFFF, false);
        context.drawText(textRenderer, "Категория: " + category.name(), 10, 124, 0xAAAAAA, false);

        int gridX = 10;
        int gridY = 150;
        int start = scrollRow * COLS;
        int maxVisible = COLS * ROWS;

        for (int i = 0; i < maxVisible; i++) {
            int index = start + i;
            if (index >= filteredItems.size()) {
                break;
            }

            ItemEntry entry = filteredItems.get(index);
            int cx = gridX + (i % COLS) * SLOT;
            int cy = gridY + (i / COLS) * SLOT;

            int bg = (selected != null && selected.id().equals(entry.id())) ? 0x80FFFF00 : 0x80333333;
            context.fill(cx - 1, cy - 1, cx + 17, cy + 17, bg);
            context.drawItem(new ItemStack(entry.item()), cx, cy);

            if (mouseX >= cx && mouseX <= cx + 16 && mouseY >= cy && mouseY <= cy + 16) {
                context.drawTooltip(textRenderer, Text.literal(entry.id().toString()), (int) mouseX, (int) mouseY);
            }
        }

        if (selected != null) {
            context.drawText(textRenderer, "Выбрано: " + selected.id(), 180, 150, 0x88FF88, false);
        }

        context.drawText(textRenderer, "Отправка идёт C2S-пакетом Armor Poser (не в чат).", 10, this.height - 12, 0x88FF88, false);
    }
}
