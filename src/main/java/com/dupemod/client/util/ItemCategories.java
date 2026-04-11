package com.dupemod.client.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Утилитный класс для категоризации предметов.
 * Разделяет все зарегистрированные предметы на категории, аналогично творческому режиму.
 */
public class ItemCategories {
    // Категории предметов
    public static final String CAT_ALL = "Все";
    public static final String CAT_BUILDING = "Строительные";
    public static final String CAT_DECORATION = "Декорации";
    public static final String CAT_REDSTONE = "Редстоун";
    public static final String CAT_TRANSPORT = "Транспорт";
    public static final String CAT_FOOD = "Еда";
    public static final String CAT_TOOLS = "Инструменты";
    public static final String CAT_COMBAT = "Оружие";
    public static final String CAT_BREWING = "Зельеварение";
    public static final String CAT_MATERIALS = "Материалы";
    public static final String CAT_MISC = "Прочее";

    private static Map<String, List<Item>> categorizedItems;

    /**
     * Получить все категории с предметами.
     * Кэширует результат при первом вызове.
     */
    public static Map<String, List<Item>> getCategories() {
        if (categorizedItems == null) {
            categorizedItems = buildCategories();
        }
        return categorizedItems;
    }

    /**
     * Получить все зарегистрированные предметы (включая из модов).
     */
    public static List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item != Items.AIR) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Получить идентификатор предмета в виде строки.
     */
    public static String getItemId(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return id.toString();
    }

    /**
     * Найти предмет по идентификатору.
     */
    public static Item getItemById(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id != null && Registries.ITEM.containsId(id)) {
            return Registries.ITEM.get(id);
        }
        return Items.AIR;
    }

    /**
     * Поиск предметов по имени или идентификатору.
     */
    public static List<Item> searchItems(String query) {
        String lowerQuery = query.toLowerCase();
        List<Item> results = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            String itemId = getItemId(item);
            String itemName = new ItemStack(item).getName().getString().toLowerCase();
            if (itemId.contains(lowerQuery) || itemName.contains(lowerQuery)) {
                results.add(item);
            }
        }
        return results;
    }

    /**
     * Строит категории предметов на основе их свойств.
     */
    private static Map<String, List<Item>> buildCategories() {
        Map<String, List<Item>> categories = new LinkedHashMap<>();
        categories.put(CAT_ALL, new ArrayList<>());
        categories.put(CAT_BUILDING, new ArrayList<>());
        categories.put(CAT_DECORATION, new ArrayList<>());
        categories.put(CAT_REDSTONE, new ArrayList<>());
        categories.put(CAT_TRANSPORT, new ArrayList<>());
        categories.put(CAT_FOOD, new ArrayList<>());
        categories.put(CAT_TOOLS, new ArrayList<>());
        categories.put(CAT_COMBAT, new ArrayList<>());
        categories.put(CAT_BREWING, new ArrayList<>());
        categories.put(CAT_MATERIALS, new ArrayList<>());
        categories.put(CAT_MISC, new ArrayList<>());

        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;

            categories.get(CAT_ALL).add(item);
            String id = getItemId(item);

            // Простая категоризация по идентификаторам и свойствам
            if (isFood(item)) {
                categories.get(CAT_FOOD).add(item);
            } else if (isCombat(id)) {
                categories.get(CAT_COMBAT).add(item);
            } else if (isTool(id)) {
                categories.get(CAT_TOOLS).add(item);
            } else if (isRedstone(id)) {
                categories.get(CAT_REDSTONE).add(item);
            } else if (isBrewing(id)) {
                categories.get(CAT_BREWING).add(item);
            } else if (isTransport(id)) {
                categories.get(CAT_TRANSPORT).add(item);
            } else if (isBuilding(id)) {
                categories.get(CAT_BUILDING).add(item);
            } else if (isDecoration(id)) {
                categories.get(CAT_DECORATION).add(item);
            } else if (isMaterial(id)) {
                categories.get(CAT_MATERIALS).add(item);
            } else {
                categories.get(CAT_MISC).add(item);
            }
        }
        return categories;
    }

    private static boolean isFood(Item item) {
        return new ItemStack(item).contains(DataComponentTypes.FOOD);
    }

    private static boolean isCombat(String id) {
        return id.contains("sword") || id.contains("bow") || id.contains("arrow") ||
               id.contains("crossbow") || id.contains("trident") || id.contains("shield") ||
               id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") ||
               id.contains("boots") || id.contains("mace") || id.contains("_armor");
    }

    private static boolean isTool(String id) {
        return id.contains("pickaxe") || id.contains("axe") || id.contains("shovel") ||
               id.contains("hoe") || id.contains("shears") || id.contains("flint_and_steel") ||
               id.contains("fishing_rod") || id.contains("compass") || id.contains("clock") ||
               id.contains("spyglass") || id.contains("brush") || id.contains("bucket");
    }

    private static boolean isRedstone(String id) {
        return id.contains("redstone") || id.contains("piston") || id.contains("repeater") ||
               id.contains("comparator") || id.contains("lever") || id.contains("button") ||
               id.contains("pressure_plate") || id.contains("tripwire") || id.contains("observer") ||
               id.contains("hopper") || id.contains("dropper") || id.contains("dispenser") ||
               id.contains("daylight") || id.contains("target");
    }

    private static boolean isBrewing(String id) {
        return id.contains("potion") || id.contains("brewing") || id.contains("blaze_powder") ||
               id.contains("nether_wart") || id.contains("glass_bottle") || id.contains("cauldron") ||
               id.contains("fermented") || id.contains("magma_cream") || id.contains("glistering") ||
               id.contains("dragon_breath") || id.contains("rabbit_foot") || id.contains("phantom_membrane");
    }

    private static boolean isTransport(String id) {
        return id.contains("rail") || id.contains("minecart") || id.contains("boat") ||
               id.contains("saddle") || id.contains("elytra") || id.contains("lead");
    }

    private static boolean isBuilding(String id) {
        return id.contains("_block") || id.contains("_bricks") || id.contains("_slab") ||
               id.contains("_stairs") || id.contains("_wall") || id.contains("_planks") ||
               id.contains("_log") || id.contains("_wood") || id.contains("stone") ||
               id.contains("dirt") || id.contains("sand") || id.contains("gravel") ||
               id.contains("concrete") || id.contains("terracotta") || id.contains("glass") ||
               id.contains("_ore") || id.contains("deepslate") || id.contains("copper");
    }

    private static boolean isDecoration(String id) {
        return id.contains("flower") || id.contains("painting") || id.contains("banner") ||
               id.contains("candle") || id.contains("lantern") || id.contains("torch") ||
               id.contains("carpet") || id.contains("pot") || id.contains("sign") ||
               id.contains("frame") || id.contains("head") || id.contains("skull") ||
               id.contains("bell") || id.contains("chain") || id.contains("_bed");
    }

    private static boolean isMaterial(String id) {
        return id.contains("diamond") || id.contains("emerald") || id.contains("gold_ingot") ||
               id.contains("iron_ingot") || id.contains("coal") || id.contains("lapis") ||
               id.contains("netherite") || id.contains("amethyst") || id.contains("quartz") ||
               id.contains("leather") || id.contains("string") || id.contains("feather") ||
               id.contains("stick") || id.contains("bone") || id.contains("ink_sac");
    }
}
