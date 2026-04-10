package com.example.armorposeritemgiver.screen;

import com.example.armorposeritemgiver.config.ModConfig;
import com.example.armorposeritemgiver.service.ItemGiveService;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.ComponentChanges;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.nbt.StringNbtReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Упрощенный item-browser в духе творческого меню:
 * - поиск по id/названию
 * - фильтрация по namespace (как категории)
 * - применение NBT через SNBT
 */
public class ItemBrowserScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemBrowserScreen.class);
    private static final int ROWS = 5;
    private static final int COLS = 9;
    private static final int PER_PAGE = ROWS * COLS;
    private static final int SEARCH_MAX_LENGTH = 256;
    private static final int NBT_MAX_LENGTH = 1024;

    private final Screen parent;
    private TextFieldWidget searchField;
    private TextFieldWidget nbtField;

    private List<Item> allItems = List.of();
    private List<Item> filteredItems = List.of();
    private List<String> namespaces = List.of("all");
    private String activeNamespace = "all";
    private int page = 0;
    private Text status = Text.empty();

    public ItemBrowserScreen(Screen parent) {
        super(Text.literal("Armor Poser Item Giver"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.searchField = new TextFieldWidget(textRenderer, 10, 26, 180, 20, Text.literal("Поиск"));
        this.searchField.setPlaceholder(Text.literal("id/название"));
        this.searchField.setMaxLength(SEARCH_MAX_LENGTH);
        this.searchField.setChangedListener(s -> refilter());
        this.addDrawableChild(searchField);

        this.nbtField = new TextFieldWidget(textRenderer, 200, 26, width - 210, 20, Text.literal("NBT"));
        this.nbtField.setPlaceholder(Text.literal("{Enchantments:[...]}"));
        this.nbtField.setMaxLength(NBT_MAX_LENGTH);
        this.addDrawableChild(nbtField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> previousPage())
                .position(width - 60, height - 24).size(20, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> nextPage())
                .position(width - 36, height - 24).size(20, 20).build());

        buildData();
        buildCategoryButtons();
        refilter();

        setInitialFocus(searchField);
    }

    private void buildData() {
        List<Item> temp = new ArrayList<>();
        List<String> ns = new ArrayList<>();
        ns.add("all");

        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (id == null || "air".equals(id.getPath())) {
                continue;
            }
            if (ModConfig.get().isBlacklisted(id)) {
                continue;
            }
            temp.add(item);
            if (!ns.contains(id.getNamespace())) {
                ns.add(id.getNamespace());
            }
        }

        temp.sort(Comparator.comparing(i -> Registries.ITEM.getId(i).toString()));
        ns.sort(String::compareTo);

        this.allItems = temp;
        this.namespaces = ns;
    }

    private void buildCategoryButtons() {
        int x = 10;
        int y = 50;
        int shown = Math.min(8, namespaces.size());
        for (int i = 0; i < shown; i++) {
            String ns = namespaces.get(i);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(ns), b -> {
                        activeNamespace = ns;
                        page = 0;
                        refilter();
                    }).position(x, y).size(70, 20)
                    .build());
            x += 72;
        }
    }

    private void refilter() {
        String query = searchField == null ? "" : searchField.getText().toLowerCase(Locale.ROOT);
        List<Item> out = new ArrayList<>();
        for (Item item : allItems) {
            Identifier id = Registries.ITEM.getId(item);
            if (!"all".equals(activeNamespace) && !id.getNamespace().equals(activeNamespace)) {
                continue;
            }

            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String sid = id.toString().toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !sid.contains(query) && !name.contains(query)) {
                continue;
            }
            out.add(item);
        }
        filteredItems = out;
        page = 0;
    }

    private void previousPage() {
        if (page > 0) page--;
    }

    private void nextPage() {
        int maxPage = Math.max(0, (filteredItems.size() - 1) / PER_PAGE);
        if (page < maxPage) page++;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int startX = 10;
        int startY = 80;
        int slot = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = startX + c * 20;
                int y = startY + r * 20;
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                    int index = page * PER_PAGE + slot;
                    if (index >= 0 && index < filteredItems.size()) {
                        give(filteredItems.get(index));
                        return true;
                    }
                }
                slot++;
            }
        }
        return false;
    }

    private void give(Item item) {
        ItemStack stack = new ItemStack(item);
        String snbt = nbtField.getText().trim();
        if (!snbt.isEmpty()) {
            try {
                stack.applyChanges(ComponentChanges.fromNbt(StringNbtReader.parse(snbt)));
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Invalid SNBT in NBT field: {}", e.getMessage());
            }
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int selected = client.player == null ? 36 : 36 + client.player.getInventory().selectedSlot;
        String rawComponents = nbtField.getText().trim();
        boolean ok = ItemGiveService.give(stack, selected, rawComponents);
        status = ok
                ? Text.literal("Готово: предмет отправлен (инвентарь/стойка)")
                : Text.literal("Не удалось: нужен creative или права на /data merge");
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawText(textRenderer, title, 10, 10, 0xFFFFFF, false);

        super.render(context, mouseX, mouseY, delta);

        int startX = 10;
        int startY = 80;
        int slot = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = startX + c * 20;
                int y = startY + r * 20;
                context.fill(x, y, x + 18, y + 18, 0x66000000);

                int index = page * PER_PAGE + slot;
                if (index >= 0 && index < filteredItems.size()) {
                    ItemStack stack = new ItemStack(filteredItems.get(index));
                    context.drawItem(stack, x + 1, y + 1);
                    if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                        context.drawTooltip(textRenderer, stack.getName(), mouseX, mouseY);
                    }
                }
                slot++;
            }
        }

        int maxPage = Math.max(0, (filteredItems.size() - 1) / PER_PAGE);
        context.drawText(textRenderer, Text.literal("Стр. " + (page + 1) + "/" + (maxPage + 1)), width - 120, height - 18, 0xAAAAAA, false);
        context.drawText(textRenderer, status, 10, height - 18, 0x55FF55, false);
    }
}
