package com.dupe.posergive.gui;

import net.minecraft.item.Item;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;

public enum ItemCategory {
    ALL,
    COMBAT,
    TOOLS,
    BLOCKS,
    MISC;

    public boolean matches(Item item) {
        return switch (this) {
            case ALL -> true;
            case COMBAT -> item instanceof ArmorItem || item.getName().getString().toLowerCase().contains("sword");
            case TOOLS -> item instanceof ToolItem;
            case BLOCKS -> item instanceof BlockItem;
            case MISC -> !(item instanceof BlockItem) && !(item instanceof ToolItem) && !(item instanceof ArmorItem);
        };
    }
}
