package com.example.armorposeritemgiver.screen;

import com.example.armorposeritemgiver.config.ModConfig;
import com.example.armorposeritemgiver.service.ItemGiveService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Экран поиска и выдачи предметов на стойку для брони (Item Browser).
 * <p>
 * Интерфейс:
 * <ul>
 *   <li>Поле поиска по ID или названию предмета</li>
 *   <li>Поле ввода NBT-данных без ограничения по символам (SNBT-формат)</li>
 *   <li>Поле ввода количества предметов (1-99)</li>
 *   <li>Категории по пространствам имён (minecraft, modid и т.д.)</li>
 *   <li>Постраничная навигация по 45 предметов на странице (9x5 сетка)</li>
 * </ul>
 * При клике на предмет он выдаётся на стойку для брони через {@link ItemGiveService}.
 * Выдача идёт через механизм Armor Poser (updateEntity), без /data merge и без креатива.
 */
@Environment(EnvType.CLIENT)
public class ItemBrowserScreen extends Screen {

    /** Количество строк в сетке предметов */
    private static final int ROWS = 5;
    /** Количество столбцов в сетке предметов */
    private static final int COLS = 9;
    /** Количество предметов на одной странице */
    private static final int PER_PAGE = ROWS * COLS; // 45

    /** Родительский экран (для возврата при закрытии) */
    private final Screen parent;

    /**
     * Ссылка на стойку для брони из экрана Armor Poser.
     * Может быть null, если экран открыт через горячую клавишу.
     */
    private final ArmorStandEntity targetArmorStand;

    /** Поле поиска по ID/названию предмета */
    private TextFieldWidget searchField;
    /** Поле ввода NBT-компонентов в SNBT-формате (без ограничения символов) */
    private TextFieldWidget nbtField;
    /** Поле ввода количества предметов (1-99) */
    private TextFieldWidget countField;

    /**
     * Ссылка на экран Armor Poser (для вызова updateEntity через рефлексию).
     * Если экран открыт из Armor Poser, parent И armorPoserScreen — один и тот же объект.
     * Если экран открыт через горячую клавишу — null.
     */
    private final Screen armorPoserScreen;

    /** Полный список предметов (без air и заблокированных) */
    private List<Item> allItems = List.of();
    /** Отфильтрованный список предметов (после поиска и выбора категории) */
    private List<Item> filteredItems = List.of();
    /** Список пространств имён для категорий */
    private List<String> namespaces = List.of("all");
    /** Текущее активное пространство имён ("all" = показать все) */
    private String activeNamespace = "all";
    /** Текущая страница (с 0) */
    private int page = 0;
    /** Статусное сообщение в нижней части экрана */
    private Text status = Text.empty();

    /**
     * Создаёт экран Item Browser без привязки к конкретной стойке.
     * Предметы будут выдаваться на стойку, на которую смотрит игрок.
     *
     * @param parent родительский экран для возврата при закрытии
     */
    public ItemBrowserScreen(Screen parent) {
        this(parent, null);
    }


    /**
     * Создаёт экран Item Browser с привязкой к конкретной стойке для брони.
     * Используется при открытии из экрана Armor Poser.
     * Родительский экран (parent) также используется как экран Armor Poser
     * для вызова updateEntity() при выдаче предметов.
     *
     * @param parent      родительский экран (экран Armor Poser) для возврата при закрытии
     * @param armorStand  целевая стойка для брони (может быть null)
     */
    public ItemBrowserScreen(Screen parent, ArmorStandEntity armorStand) {
        super(Text.literal("Armor Poser \u2014 Item Giver"));
        this.parent = parent;
        this.targetArmorStand = armorStand;
        // Если передана стойка — значит открыто из Armor Poser, parent = AP screen
        this.armorPoserScreen = (armorStand != null) ? parent : null;
    }

    /**
     * Инициализация виджетов экрана: поля ввода, кнопки навигации и категорий.
     */
    @Override
    protected void init() {
        // --- Поле поиска ---
        searchField = new TextFieldWidget(textRenderer, 10, 26, 150, 20, Text.literal("Поиск"));
        searchField.setPlaceholder(Text.literal("id / название предмета"));
        searchField.setChangedListener(s -> refilter());
        addDrawableChild(searchField);

        // --- Поле количества предметов (1-99) ---
        countField = new TextFieldWidget(textRenderer, 165, 26, 30, 20, Text.literal("Кол-во"));
        countField.setMaxLength(2); // Максимум 2 цифры (1-99)
        countField.setText("1");
        addDrawableChild(countField);

        // --- Поле NBT (без ограничения по символам!) ---
        nbtField = new TextFieldWidget(textRenderer, 200, 26, width - 210, 20, Text.literal("NBT"));
        nbtField.setMaxLength(Integer.MAX_VALUE); // Без ограничения символов
        nbtField.setPlaceholder(Text.literal("{display:{Name:'{\"text\":\"Custom\"}'}}"));
        addDrawableChild(nbtField);

        // --- Кнопки навигации по страницам ---
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> previousPage())
                .position(width - 60, height - 24)
                .size(20, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> nextPage())
                .position(width - 36, height - 24)
                .size(20, 20)
                .build());

        // Сборка данных из реестра предметов
        buildData();
        // Создание кнопок категорий
        buildCategoryButtons();
        // Первичная фильтрация
        refilter();
        // Фокус на поле поиска
        setInitialFocus(searchField);
    }

    /**
     * Сборка полного списка предметов из реестра и определение пространств имён.
     * Предметы "air" и предметы из чёрного списка исключаются.
     */
    private void buildData() {
        ArrayList<Item> temp = new ArrayList<>();
        ArrayList<String> ns = new ArrayList<>();
        ns.add("all");

        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) continue;
            // Пропуск воздуха и предметов из чёрного списка конфига
            if ("air".equals(id.getPath())) continue;
            if (ModConfig.get().isBlacklisted(id)) continue;

            temp.add(item);
            // Добавляем пространство имён, если ещё не в списке
            if (!ns.contains(id.getNamespace())) {
                ns.add(id.getNamespace());
            }
        }

        // Сортировка предметов по полному ID для удобства навигации
        temp.sort(Comparator.comparing(i -> Registries.ITEM.getId(i).toString()));
        // Сортируем пространства имён, но "all" всегда остаётся первым
        ns.remove("all");
        ns.sort(String::compareTo);
        ns.add(0, "all");

        allItems = temp;
        namespaces = ns;
    }

    /**
     * Создание кнопок категорий (пространств имён) в верхней части экрана.
     * Показывает до 8 категорий.
     */
    private void buildCategoryButtons() {
        int x = 10;
        int y = 50;
        int shown = Math.min(8, namespaces.size());

        for (int i = 0; i < shown; i++) {
            String ns = namespaces.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal(ns), b -> {
                activeNamespace = ns;
                page = 0;
                refilter();
            }).position(x, y).size(70, 20).build());
            x += 72;
        }
    }

    /**
     * Фильтрация предметов по текущему поисковому запросу и выбранной категории.
     * Сбрасывает страницу на первую.
     */
    private void refilter() {
        String query = (searchField == null) ? "" : searchField.getText().toLowerCase(Locale.ROOT);
        ArrayList<Item> out = new ArrayList<>();

        for (Item item : allItems) {
            Identifier id = Registries.ITEM.getId(item);

            // Фильтр по пространству имён (категории)
            if (!"all".equals(activeNamespace) && !id.getNamespace().equals(activeNamespace)) {
                continue;
            }

            // Фильтр по поисковому запросу (ID или локализованное название)
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

    /** Переход на предыдущую страницу */
    private void previousPage() {
        if (page > 0) {
            page--;
        }
    }

    /** Переход на следующую страницу */
    private void nextPage() {
        int maxPage = Math.max(0, (filteredItems.size() - 1) / PER_PAGE);
        if (page < maxPage) {
            page++;
        }
    }

    /**
     * Обработка клика мышью.
     * Если клик попадает в ячейку сетки предметов — выдаёт соответствующий предмет.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Сначала проверяем стандартные виджеты (кнопки, поля ввода)
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Проверяем попадание клика в сетку предметов
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

    /**
     * Выдаёт предмет на стойку для брони через Armor Poser.
     * Использует {@link ItemGiveService#give} для установки предмета и синхронизации.
     * Поле NBT-данных применяется без ограничений по длине.
     * Количество берётся из поля countField.
     *
     * @param item предмет для выдачи
     */
    private void give(Item item) {
        ItemStack stack = new ItemStack(item);

        // Получаем SNBT-строку из поля ввода (без ограничения символов)
        String rawSnbt = nbtField.getText().trim();

        // Парсим количество из поля ввода
        int count = 1;
        try {
            count = Integer.parseInt(countField.getText().trim());
        } catch (NumberFormatException ignored) {
            // Некорректное число — используем 1
        }
        count = Math.max(1, Math.min(count, 99));

        // Выдаём предмет через Armor Poser (equipStack + updateEntity)
        boolean ok = ItemGiveService.give(stack, rawSnbt, count, targetArmorStand, armorPoserScreen);

        // Обновляем статусное сообщение
        if (ok) {
            status = Text.literal("Готово: предмет отправлен на стойку (x" + count + ")");
        } else {
            status = Text.literal("Не найдена стойка. Откройте из Armor Poser");
        }
    }

    /**
     * Закрытие экрана — возвращаемся к родительскому экрану.
     */
    @Override
    public void close() {
        client.setScreen(parent);
    }

    /**
     * Отрисовка экрана: заголовок, сетка предметов с иконками,
     * всплывающие подсказки, номер страницы и статус.
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Затемнение фона
        renderBackground(context, mouseX, mouseY, delta);

        // Заголовок экрана
        context.drawText(textRenderer, title, 10, 10, 0xFFFFFF, false);

        // Информация о целевой стойке
        if (targetArmorStand != null) {
            context.drawText(textRenderer,
                    Text.literal("Стойка: " + targetArmorStand.getUuidAsString().substring(0, 8) + "..."),
                    width - 200, 10, 0x88FF88, false);
        }

        // Отрисовка дочерних виджетов (кнопки, поля ввода)
        super.render(context, mouseX, mouseY, delta);

        // --- Отрисовка сетки предметов ---
        int startX = 10;
        int startY = 80;
        int slot = 0;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = startX + c * 20;
                int y = startY + r * 20;

                // Фон ячейки (полупрозрачный чёрный)
                context.fill(x, y, x + 18, y + 18, 0x66000000);

                int index = page * PER_PAGE + slot;
                if (index >= 0 && index < filteredItems.size()) {
                    // Создаём стак для отрисовки иконки
                    ItemStack stack = new ItemStack(filteredItems.get(index));
                    context.drawItem(stack, x + 1, y + 1);

                    // Всплывающая подсказка при наведении
                    if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                        context.drawTooltip(textRenderer, stack.getTooltip(
                                Item.TooltipContext.DEFAULT,
                                client.player,
                                net.minecraft.item.tooltip.TooltipType.BASIC), mouseX, mouseY);
                    }
                }
                slot++;
            }
        }

        // --- Номер страницы ---
        int maxPage = Math.max(0, (filteredItems.size() - 1) / PER_PAGE);
        context.drawText(textRenderer,
                Text.literal("Стр. " + (page + 1) + "/" + (maxPage + 1)),
                width - 120, height - 18, 0xAAAAAA, false);

        // --- Статусное сообщение ---
        context.drawText(textRenderer, status, 10, height - 18, 0x55FF55, false);
    }
}
