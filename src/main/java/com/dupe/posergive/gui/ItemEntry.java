package com.dupe.posergive.gui;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public record ItemEntry(Identifier id, Item item) {
    public static ItemEntry of(Identifier id) {
        Item item = Registries.ITEM.get(id);
        return new ItemEntry(id, item);
    }

    public String searchableText() {
        return (id + " " + item.getName().getString()).toLowerCase();
    }
}
