package com.dupemod.client.gui;

import com.dupemod.client.config.ItemGiverConfig;
import com.dupemod.client.network.ArmorStandPacketHelper;
import com.dupemod.client.util.ItemCategories;
import com.dupemod.client.util.StatusOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Главный экран выдачи предметов через стойку для брони.
 *
 * Левая панель: категории предметов, сетка предметов с прокруткой, строка поиска.
 * Правая панель: квадратное окно редактирования NBT, кнопка "Использовать".
 *
 * При нажатии "Использовать":
 * 1. Формируется CompoundTag с данными предмета (HandItems)
 * 2. Стойка телепортируется к игроку через Move
 * 3. Отправляется пакет синхронизации через Armor Poser
 * 4. Стойка телепортируется обратно
 * 5. Отображается переливающийся текст "Исполнено"
 */
public class ItemGiverScreen extends Screen {
    // Стойка для брони, через которую выдаются предметы
    private final ArmorStandEntity armorStand;

    // Левая панель - предметы
    private TextFieldWidget searchField;
    private List<Item> displayedItems = new ArrayList<>();
    private int scrollOffset = 0;
    private String currentCategory = ItemCategories.CAT_ALL;
    private Item selectedItem = null;

    // Правая панель - NBT редактор
    private TextFieldWidget nbtField;
    private TextFieldWidget countField;

    // Размеры и позиции панелей
    private static final int LEFT_PANEL_WIDTH = 200;
    private static final int RIGHT_PANEL_SIZE = 180;
    private static final int ITEM_SIZE = 18;
    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 7;
    private static final int CATEGORY_BTN_HEIGHT = 14;
    private static final int PADDING = 6;

    // Позиции панелей (вычисляются в init)
    private int leftPanelX;
    private int leftPanelY;
    private int rightPanelX;
    private int rightPanelY;
    private int itemGridX;
    private int itemGridY;

    // Кнопки категорий
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();

    // Список категорий для кнопок
    private static final String[] CATEGORIES = {
            ItemCategories.CAT_ALL, ItemCategories.CAT_BUILDING, ItemCategories.CAT_DECORATION,
            ItemCategories.CAT_REDSTONE, ItemCategories.CAT_TRANSPORT, ItemCategories.CAT_FOOD,
            ItemCategories.CAT_TOOLS, ItemCategories.CAT_COMBAT, ItemCategories.CAT_BREWING,
            ItemCategories.CAT_MATERIALS, ItemCategories.CAT_MISC
    };

    // Прокрутка категорий
    private int categoryScrollOffset = 0;
    private static final int MAX_VISIBLE_CATEGORIES = 10;

    public ItemGiverScreen(ArmorStandEntity armorStand) {
        super(Text.literal("Item Giver"));
        this.armorStand = armorStand;
        // Регистрация оверлея "Исполнено"
        StatusOverlay.register();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        // Вычисляем позиции панелей
        int totalWidth = LEFT_PANEL_WIDTH + PADDING + RIGHT_PANEL_SIZE + PADDING * 2;
        int startX = (this.width - totalWidth) / 2;
        int startY = 20;

        leftPanelX = startX;
        leftPanelY = startY;
        rightPanelX = startX + LEFT_PANEL_WIDTH + PADDING;
        rightPanelY = startY;

        // === ЛЕВАЯ ПАНЕЛЬ ===

        // Кнопки категорий (вертикальный список слева)
        int catBtnX = leftPanelX;
        int catBtnY = leftPanelY;
        categoryButtons.clear();

        int visibleCats = Math.min(CATEGORIES.length, MAX_VISIBLE_CATEGORIES);
        for (int i = 0; i < visibleCats; i++) {
            final String category = CATEGORIES[i];
            ButtonWidget catBtn = ButtonWidget.builder(
                    Text.literal(category),
                    button -> {
                        currentCategory = category;
                        scrollOffset = 0;
                        updateDisplayedItems();
                        highlightActiveCategory();
                    }
            ).dimensions(catBtnX, catBtnY + i * (CATEGORY_BTN_HEIGHT + 2), LEFT_PANEL_WIDTH, CATEGORY_BTN_HEIGHT).build();
            this.addDrawableChild(catBtn);
            categoryButtons.add(catBtn);
        }

        // Сетка предметов
        itemGridX = leftPanelX;
        itemGridY = catBtnY + visibleCats * (CATEGORY_BTN_HEIGHT + 2) + 4;

        // Поле поиска (под сеткой предметов)
        int searchY = itemGridY + VISIBLE_ROWS * ITEM_SIZE + 4;
        searchField = new TextFieldWidget(this.textRenderer, leftPanelX, searchY, LEFT_PANEL_WIDTH, 16, Text.literal("Поиск..."));
        searchField.setMaxLength(100);
        searchField.setPlaceholder(Text.literal("\u00a77Поиск предметов..."));
        searchField.setChangedListener(query -> {
            scrollOffset = 0;
            updateDisplayedItems();
        });
        this.addDrawableChild(searchField);

        // === ПРАВАЯ ПАНЕЛЬ ===

        // Поле количества
        int rightContentY = rightPanelY + 20;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("\u041a\u043e\u043b-\u0432\u043e:"), btn -> {})
                .dimensions(rightPanelX, rightContentY, 50, 16).build());
        countField = new TextFieldWidget(this.textRenderer, rightPanelX + 52, rightContentY, 40, 16, Text.literal("64"));
        countField.setMaxLength(4);
        countField.setText("64");
        this.addDrawableChild(countField);

        // Поле NBT (квадратное, для редактирования NBT-данных)
        int nbtY = rightContentY + 22;
        nbtField = new TextFieldWidget(this.textRenderer, rightPanelX, nbtY, RIGHT_PANEL_SIZE, RIGHT_PANEL_SIZE - 50, Text.literal("NBT"));
        nbtField.setMaxLength(ItemGiverConfig.get().maxNbtLength);
        nbtField.setPlaceholder(Text.literal("\u00a77NBT \u0434\u0430\u043d\u043d\u044b\u0435 (\u043e\u043f\u0446\u0438\u043e\u043d\u0430\u043b\u044c\u043d\u043e)"));
        this.addDrawableChild(nbtField);

        // Кнопка "Использовать" (под NBT полем)
        int useY = nbtY + RIGHT_PANEL_SIZE - 50 + 4;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00a7a\u00a7l\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u044c"),
                button -> executeItemGive()
        ).dimensions(rightPanelX, useY, RIGHT_PANEL_SIZE, 20).build());

        // Загружаем предметы
        updateDisplayedItems();
        highlightActiveCategory();
    }

    /**
     * Обновляет список отображаемых предметов на основе текущей категории и поиска.
     */
    private void updateDisplayedItems() {
        String query = searchField != null ? searchField.getText() : "";
        if (!query.isEmpty()) {
            // Режим поиска - ищем по всем предметам
            displayedItems = ItemCategories.searchItems(query);
        } else {
            // Режим категорий
            Map<String, List<Item>> categories = ItemCategories.getCategories();
            displayedItems = categories.getOrDefault(currentCategory, new ArrayList<>());
        }

        // Фильтруем чёрный список
        ItemGiverConfig config = ItemGiverConfig.get();
        displayedItems = displayedItems.stream()
                .filter(item -> !config.isBlacklisted(ItemCategories.getItemId(item)))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Подсвечивает активную кнопку категории.
     */
    private void highlightActiveCategory() {
        for (int i = 0; i < categoryButtons.size(); i++) {
            ButtonWidget btn = categoryButtons.get(i);
            if (i < CATEGORIES.length) {
                boolean isActive = CATEGORIES[i].equals(currentCategory);
                btn.active = !isActive;
            }
        }
    }

    /**
     * Выполняет выдачу предмета через стойку для брони.
     * 
     * Алгоритм:
     * 1. Проверяем, выбран ли предмет
     * 2. Формируем NBT с данными предмета
     * 3. Телепортируем стойку к игроку (Move)
     * 4. Отправляем пакет с предметом
     * 5. Телепортируем стойку обратно
     * 6. Показываем "Исполнено"
     */
    private void executeItemGive() {
        if (selectedItem == null || selectedItem == Items.AIR) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("\u00a7c\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u043f\u0440\u0435\u0434\u043c\u0435\u0442!"), true);
            }
            return;
        }

        if (armorStand == null || client == null || client.player == null) return;

        // Получаем ID предмета
        String itemId = ItemCategories.getItemId(selectedItem);

        // Получаем количество
        int count = 64;
        try {
            count = Integer.parseInt(countField.getText().trim());
            count = MathHelper.clamp(count, 1, 127);
        } catch (NumberFormatException e) {
            count = 64;
        }

        // Получаем пользовательские NBT
        String customNbt = nbtField.getText().trim();

        // Вычисляем смещение для телепортации стойки к игроку
        double moveX = client.player.getX() - armorStand.getX();
        double moveY = client.player.getY() - armorStand.getY();
        double moveZ = client.player.getZ() - armorStand.getZ();

        // Сохраняем оригинальное смещение для возврата
        double returnX = -moveX;
        double returnY = -moveY;
        double returnZ = -moveZ;

        // Шаг 1: Телепортируем стойку к игроку и ставим предмет в руку
        NbtCompound itemNbt = new NbtCompound();
        itemNbt.putBoolean("ShowArms", true);
        itemNbt.putBoolean("NoGravity", true);

        // Данные предмета в руке (HandItems)
        NbtList handItems = new NbtList();
        NbtCompound mainHandItem = new NbtCompound();
        mainHandItem.putString("id", itemId);
        mainHandItem.putInt("count", count);

        // Добавляем пользовательские NBT если есть
        if (!customNbt.isEmpty()) {
            try {
                NbtCompound customTag = net.minecraft.nbt.StringNbtReader.parse(customNbt);
                for (String key : customTag.getKeys()) {
                    mainHandItem.put(key, customTag.get(key));
                }
            } catch (Exception e) {
                // Игнорируем ошибки парсинга - выдаём предмет без NBT
            }
        }

        handItems.add(mainHandItem);
        handItems.add(new NbtCompound()); // Пустой слот для второй руки
        itemNbt.put("HandItems", handItems);

        // Добавляем Move для телепортации к игроку
        NbtList moveTag = new NbtList();
        moveTag.add(NbtDouble.of(moveX));
        moveTag.add(NbtDouble.of(moveY));
        moveTag.add(NbtDouble.of(moveZ));
        itemNbt.put("Move", moveTag);

        // Пустые позы (чтобы не нарушить текущую позу)
        NbtCompound poseTag = new NbtCompound();
        addPoseList(poseTag, "Head", 0, 0, 0);
        addPoseList(poseTag, "Body", 0, 0, 0);
        addPoseList(poseTag, "LeftLeg", 0, 0, 0);
        addPoseList(poseTag, "RightLeg", 0, 0, 0);
        addPoseList(poseTag, "LeftArm", 0, 0, 0);
        addPoseList(poseTag, "RightArm", -90, 0, 0); // Рука вытянута вперёд для удобства
        itemNbt.put("Pose", poseTag);

        NbtList rotationTag = new NbtList();
        rotationTag.add(net.minecraft.nbt.NbtFloat.of(0));
        itemNbt.put("Rotation", rotationTag);

        // Отправляем пакет с предметом и телепортацией к игроку
        ArmorStandPacketHelper.sendSyncPacket(armorStand, itemNbt);

        // Шаг 2: Через небольшую задержку - имитируем взятие предмета (ПКМ по стойке)
        // и телепортируем стойку обратно
        final double finalReturnX = returnX;
        final double finalReturnY = returnY;
        final double finalReturnZ = returnZ;

        // Запускаем отложенную задачу для возврата стойки
        new Thread(() -> {
            try {
                // Ждём чтобы сервер обработал первый пакет
                Thread.sleep(500);

                // Телепортируем стойку обратно
                MinecraftClient.getInstance().execute(() -> {
                    NbtCompound returnNbt = new NbtCompound();
                    NbtList returnMove = new NbtList();
                    returnMove.add(NbtDouble.of(finalReturnX));
                    returnMove.add(NbtDouble.of(finalReturnY));
                    returnMove.add(NbtDouble.of(finalReturnZ));
                    returnNbt.put("Move", returnMove);

                    // Пустые обязательные поля
                    NbtCompound returnPose = new NbtCompound();
                    addPoseList(returnPose, "Head", 0, 0, 0);
                    addPoseList(returnPose, "Body", 0, 0, 0);
                    addPoseList(returnPose, "LeftLeg", 0, 0, 0);
                    addPoseList(returnPose, "RightLeg", 0, 0, 0);
                    addPoseList(returnPose, "LeftArm", 0, 0, 0);
                    addPoseList(returnPose, "RightArm", 0, 0, 0);
                    returnNbt.put("Pose", returnPose);

                    NbtList returnRotation = new NbtList();
                    returnRotation.add(net.minecraft.nbt.NbtFloat.of(0));
                    returnNbt.put("Rotation", returnRotation);

                    ArmorStandPacketHelper.sendSyncPacket(armorStand, returnNbt);

                    // Показываем "Исполнено"
                    StatusOverlay.showSuccess();
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Вспомогательный метод для добавления данных позы в CompoundTag.
     */
    private static void addPoseList(NbtCompound poseTag, String key, float x, float y, float z) {
        NbtList list = new NbtList();
        list.add(net.minecraft.nbt.NbtFloat.of(x));
        list.add(net.minecraft.nbt.NbtFloat.of(y));
        list.add(net.minecraft.nbt.NbtFloat.of(z));
        poseTag.put(key, list);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Затемнённый фон
        this.renderBackground(context, mouseX, mouseY, delta);

        // === Фон левой панели ===
        int leftPanelHeight = this.height - leftPanelY - 20;
        context.fill(leftPanelX - 2, leftPanelY - 2, leftPanelX + LEFT_PANEL_WIDTH + 2, leftPanelY + leftPanelHeight, 0x80000000);
        // Рамка
        drawBorder(context, leftPanelX - 2, leftPanelY - 2, LEFT_PANEL_WIDTH + 4, leftPanelHeight + 2, 0xFF555555);

        // === Фон правой панели ===
        int rightPanelHeight = leftPanelHeight;
        context.fill(rightPanelX - 2, rightPanelY - 2, rightPanelX + RIGHT_PANEL_SIZE + 2, rightPanelY + rightPanelHeight, 0x80000000);
        drawBorder(context, rightPanelX - 2, rightPanelY - 2, RIGHT_PANEL_SIZE + 4, rightPanelHeight + 2, 0xFF555555);

        // === Заголовки ===
        context.drawText(this.textRenderer, "\u00a7e\u00a7lItem Giver", leftPanelX + LEFT_PANEL_WIDTH / 2 - 30, leftPanelY - 14, 0xFFFFFF, true);
        context.drawText(this.textRenderer, "\u00a7b\u00a7lNBT \u0420\u0435\u0434\u0430\u043a\u0442\u043e\u0440", rightPanelX + RIGHT_PANEL_SIZE / 2 - 35, rightPanelY + 6, 0xFFFFFF, true);

        // === Сетка предметов ===
        renderItemGrid(context, mouseX, mouseY);

        // === Информация о выбранном предмете ===
        if (selectedItem != null && selectedItem != Items.AIR) {
            String itemName = new ItemStack(selectedItem).getName().getString();
            String itemId = ItemCategories.getItemId(selectedItem);
            int infoY = rightPanelY + RIGHT_PANEL_SIZE + 30;
            context.drawText(this.textRenderer, "\u00a7f" + itemName, rightPanelX, infoY, 0xFFFFFF, true);
            context.drawText(this.textRenderer, "\u00a77" + itemId, rightPanelX, infoY + 12, 0xAAAAAA, true);
        }

        // === Полоса прокрутки предметов ===
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollBarX = itemGridX + ITEMS_PER_ROW * ITEM_SIZE + 2;
            int scrollBarHeight = VISIBLE_ROWS * ITEM_SIZE;
            int thumbHeight = Math.max(10, scrollBarHeight * VISIBLE_ROWS / (maxScroll + VISIBLE_ROWS));
            int thumbY = itemGridY + (int) ((float) scrollOffset / maxScroll * (scrollBarHeight - thumbHeight));

            // Фон полосы прокрутки
            context.fill(scrollBarX, itemGridY, scrollBarX + 6, itemGridY + scrollBarHeight, 0xFF333333);
            // Ползунок
            context.fill(scrollBarX, thumbY, scrollBarX + 6, thumbY + thumbHeight, 0xFF888888);
        }

        // Рендерим виджеты поверх
        super.render(context, mouseX, mouseY, delta);

        // Тултип для предмета под курсором
        renderItemTooltip(context, mouseX, mouseY);
    }

    /**
     * Отрисовка сетки предметов.
     */
    private void renderItemGrid(DrawContext context, int mouseX, int mouseY) {
        int totalItems = displayedItems.size();
        int startIndex = scrollOffset * ITEMS_PER_ROW;

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < ITEMS_PER_ROW; col++) {
                int index = startIndex + row * ITEMS_PER_ROW + col;
                int x = itemGridX + col * ITEM_SIZE;
                int y = itemGridY + row * ITEM_SIZE;

                // Фон слота
                boolean isHovered = mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE;

                if (index < totalItems) {
                    Item item = displayedItems.get(index);
                    boolean isSelected = item == selectedItem;

                    // Цвет фона слота
                    int bgColor = isSelected ? 0xFF44AA44 : (isHovered ? 0xFF666666 : 0xFF2A2A2A);
                    context.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, bgColor);

                    // Рендерим иконку предмета
                    ItemStack stack = new ItemStack(item);
                    context.drawItem(stack, x + 1, y + 1);
                } else {
                    // Пустой слот
                    context.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, 0xFF1A1A1A);
                }

                // Рамка слота
                drawBorder(context, x, y, ITEM_SIZE, ITEM_SIZE, 0xFF444444);
            }
        }
    }

    /**
     * Отрисовка тултипа для предмета под курсором.
     */
    private void renderItemTooltip(DrawContext context, int mouseX, int mouseY) {
        if (mouseX < itemGridX || mouseX >= itemGridX + ITEMS_PER_ROW * ITEM_SIZE) return;
        if (mouseY < itemGridY || mouseY >= itemGridY + VISIBLE_ROWS * ITEM_SIZE) return;

        int col = (mouseX - itemGridX) / ITEM_SIZE;
        int row = (mouseY - itemGridY) / ITEM_SIZE;
        int index = (scrollOffset + row) * ITEMS_PER_ROW + col;

        if (index >= 0 && index < displayedItems.size()) {
            Item item = displayedItems.get(index);
            ItemStack stack = new ItemStack(item);
            context.drawItemTooltip(this.textRenderer, stack, mouseX, mouseY);
        }
    }

    /**
     * Рисует рамку вокруг прямоугольника.
     */
    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color); // Верх
        context.fill(x, y + height - 1, x + width, y + height, color); // Низ
        context.fill(x, y, x + 1, y + height, color); // Лево
        context.fill(x + width - 1, y, x + width, y + height, color); // Право
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Проверяем клик по сетке предметов
        if (mouseX >= itemGridX && mouseX < itemGridX + ITEMS_PER_ROW * ITEM_SIZE
                && mouseY >= itemGridY && mouseY < itemGridY + VISIBLE_ROWS * ITEM_SIZE) {
            int col = (int) (mouseX - itemGridX) / ITEM_SIZE;
            int row = (int) (mouseY - itemGridY) / ITEM_SIZE;
            int index = (scrollOffset + row) * ITEMS_PER_ROW + col;

            if (index >= 0 && index < displayedItems.size()) {
                selectedItem = displayedItems.get(index);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Прокрутка сетки предметов
        if (mouseX >= itemGridX && mouseX < itemGridX + ITEMS_PER_ROW * ITEM_SIZE + 10
                && mouseY >= itemGridY && mouseY < itemGridY + VISIBLE_ROWS * ITEM_SIZE) {
            int maxScroll = getMaxScroll();
            scrollOffset = MathHelper.clamp(scrollOffset - (int) verticalAmount, 0, maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Вычисляет максимальное значение прокрутки.
     */
    private int getMaxScroll() {
        int totalRows = (displayedItems.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        return Math.max(0, totalRows - VISIBLE_ROWS);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Позволяем вводить текст в поля, но не блокируем Escape
        if (searchField != null && searchField.isFocused()) {
            if (searchField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (nbtField != null && nbtField.isFocused()) {
            if (nbtField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (countField != null && countField.isFocused()) {
            if (countField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
