package cl.nostalgia.betabridge;

import java.util.HashMap;
import java.util.Map;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;

/** Exact legacy ID and metadata mappings for the original personal-inventory recipes. */
final class BetaLegacyItems {
    private record Key(int id, int metadata) { }
    private static final Map<Key, Item> BY_LEGACY_ID = new HashMap<>();
    private static final Map<Integer, Key> TO_LEGACY_ID = new HashMap<>();

    private BetaLegacyItems() { }

    static void initialize() {
        add(1, Items.STONE); add(4, Items.COBBLESTONE); add(5, Items.OAK_PLANKS);
        add(12, Items.SAND); add(17, Items.OAK_LOG); add(20, Items.GLASS);
        add(17, 1, Items.SPRUCE_LOG); add(17, 2, Items.BIRCH_LOG);
        add(22, Items.LAPIS_BLOCK); add(24, Items.SANDSTONE); add(35, Items.WHITE_WOOL);
        add(37, Items.DANDELION); add(38, Items.POPPY);
        add(39, Items.BROWN_MUSHROOM); add(40, Items.RED_MUSHROOM);
        add(41, Items.GOLD_BLOCK); add(42, Items.IRON_BLOCK); add(45, Items.BRICKS); add(50, Items.TORCH);
        add(54, Items.CHEST); add(61, Items.FURNACE);
        add(57, Items.DIAMOND_BLOCK); add(58, Items.CRAFTING_TABLE);
        add(69, Items.LEVER); add(70, Items.STONE_PRESSURE_PLATE);
        add(72, Items.OAK_PRESSURE_PLATE); add(76, Items.REDSTONE_TORCH);
        add(77, Items.STONE_BUTTON); add(80, Items.SNOW_BLOCK); add(82, Items.CLAY);
        add(86, Items.PUMPKIN); add(91, Items.JACK_O_LANTERN);
        add(259, Items.FLINT_AND_STEEL); add(263, Items.COAL); add(263, 1, Items.CHARCOAL);
        add(264, Items.DIAMOND); add(265, Items.IRON_INGOT); add(266, Items.GOLD_INGOT);
        add(280, Items.STICK); add(281, Items.BOWL); add(282, Items.MUSHROOM_STEW);
        add(287, Items.STRING); add(289, Items.GUNPOWDER); add(318, Items.FLINT);
        add(328, Items.MINECART); add(331, Items.REDSTONE); add(332, Items.SNOWBALL);
        add(336, Items.BRICK); add(337, Items.CLAY_BALL);
        add(338, Items.SUGAR_CANE); add(339, Items.PAPER); add(340, Items.BOOK);
        add(342, Items.CHEST_MINECART); add(343, Items.FURNACE_MINECART);
        add(351, Items.INK_SAC); add(352, Items.BONE); add(353, Items.SUGAR);
        add(27, Items.POWERED_RAIL); add(28, Items.DETECTOR_RAIL);
        add(96, Items.OAK_TRAPDOOR); add(358, Items.FILLED_MAP);
        add(23, Items.DISPENSER); add(25, Items.NOTE_BLOCK);
        add(44, Items.SMOOTH_STONE_SLAB); add(44, 1, Items.SANDSTONE_SLAB);
        add(44, 2, Items.PETRIFIED_OAK_SLAB); add(44, 3, Items.COBBLESTONE_SLAB);
        add(46, Items.TNT); add(47, Items.BOOKSHELF); add(53, Items.OAK_STAIRS);
        add(65, Items.LADDER); add(66, Items.RAIL); add(67, Items.COBBLESTONE_STAIRS);
        add(84, Items.JUKEBOX); add(85, Items.OAK_FENCE); add(89, Items.GLOWSTONE);
        add(256, Items.IRON_SHOVEL); add(257, Items.IRON_PICKAXE); add(258, Items.IRON_AXE);
        add(260, Items.APPLE); add(261, Items.BOW); add(262, Items.ARROW); add(267, Items.IRON_SWORD);
        add(268, Items.WOODEN_SWORD); add(269, Items.WOODEN_SHOVEL); add(270, Items.WOODEN_PICKAXE);
        add(271, Items.WOODEN_AXE); add(272, Items.STONE_SWORD); add(273, Items.STONE_SHOVEL);
        add(274, Items.STONE_PICKAXE); add(275, Items.STONE_AXE); add(276, Items.DIAMOND_SWORD);
        add(277, Items.DIAMOND_SHOVEL); add(278, Items.DIAMOND_PICKAXE); add(279, Items.DIAMOND_AXE);
        add(283, Items.GOLDEN_SWORD); add(284, Items.GOLDEN_SHOVEL); add(285, Items.GOLDEN_PICKAXE);
        add(286, Items.GOLDEN_AXE); add(288, Items.FEATHER); add(290, Items.WOODEN_HOE);
        add(291, Items.STONE_HOE); add(292, Items.IRON_HOE); add(293, Items.DIAMOND_HOE);
        add(294, Items.GOLDEN_HOE); add(296, Items.WHEAT); add(297, Items.BREAD);
        add(298, Items.LEATHER_HELMET); add(299, Items.LEATHER_CHESTPLATE);
        add(300, Items.LEATHER_LEGGINGS); add(301, Items.LEATHER_BOOTS);
        add(302, Items.CHAINMAIL_HELMET); add(303, Items.CHAINMAIL_CHESTPLATE);
        add(304, Items.CHAINMAIL_LEGGINGS); add(305, Items.CHAINMAIL_BOOTS);
        add(306, Items.IRON_HELMET); add(307, Items.IRON_CHESTPLATE); add(308, Items.IRON_LEGGINGS);
        add(309, Items.IRON_BOOTS); add(310, Items.DIAMOND_HELMET); add(311, Items.DIAMOND_CHESTPLATE);
        add(312, Items.DIAMOND_LEGGINGS); add(313, Items.DIAMOND_BOOTS);
        add(314, Items.GOLDEN_HELMET); add(315, Items.GOLDEN_CHESTPLATE);
        add(316, Items.GOLDEN_LEGGINGS); add(317, Items.GOLDEN_BOOTS);
        add(321, Items.PAINTING); add(322, Items.GOLDEN_APPLE); add(323, Items.OAK_SIGN);
        add(324, Items.OAK_DOOR); add(325, Items.BUCKET); add(330, Items.IRON_DOOR);
        add(333, Items.OAK_BOAT); add(334, Items.LEATHER); add(335, Items.MILK_BUCKET);
        add(344, Items.EGG); add(345, Items.COMPASS); add(346, Items.FISHING_ROD);
        add(347, Items.CLOCK); add(348, Items.GLOWSTONE_DUST);
        add(354, Items.CAKE); add(355, Items.RED_BED); add(356, Items.REPEATER); add(357, Items.COOKIE);
        Item[] wool = {Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL,
            Items.LIGHT_BLUE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL,
            Items.GRAY_WOOL, Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL,
            Items.BLUE_WOOL, Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL};
        // Beta's dyes included ink, cocoa, lapis and bone meal as distinct metadata.
        Item[] dyes = {Items.INK_SAC, Items.RED_DYE, Items.GREEN_DYE, Items.COCOA_BEANS,
            Items.LAPIS_LAZULI, Items.PURPLE_DYE, Items.CYAN_DYE, Items.LIGHT_GRAY_DYE,
            Items.GRAY_DYE, Items.PINK_DYE, Items.LIME_DYE, Items.YELLOW_DYE,
            Items.LIGHT_BLUE_DYE, Items.MAGENTA_DYE, Items.ORANGE_DYE, Items.BONE_MEAL};
        for (int metadata = 0; metadata < 16; metadata++) {
            add(35, metadata, wool[metadata]);
            add(351, metadata, dyes[metadata]);
        }
    }

    private static void add(int legacyId, Item item) {
        add(legacyId, 0, item);
    }

    private static void add(int legacyId, int metadata, Item item) {
        Key key = new Key(legacyId, metadata);
        BY_LEGACY_ID.put(key, item);
        TO_LEGACY_ID.put(item.javaId(), key);
    }

    static BetaCraftingRecipes.Stack ingredient(GeyserItemStack stack) {
        if (stack.isEmpty()) return null;
        Key key = TO_LEGACY_ID.get(stack.getJavaId());
        if (key == null || stack.getDamage() != 0) {
            // Preserve occupancy for unsupported items so they cannot look like empty slots.
            return new BetaCraftingRecipes.Stack(Integer.MAX_VALUE, 0, stack.getAmount());
        }
        return new BetaCraftingRecipes.Stack(key.id(), key.metadata(), stack.getAmount());
    }

    static Item output(BetaCraftingRecipes.Stack result) {
        return result == null ? null : BY_LEGACY_ID.get(new Key(result.id(), result.metadata()));
    }

    static void verifyCatalog(BetaCraftingRecipes.Catalog catalog) {
        for (var recipe : catalog.recipes()) {
            if (output(recipe.result()) == null) throw new IllegalStateException("Unmapped Beta recipe output");
            for (var input : recipe.ingredients()) {
                // Four original chainmail recipes require the unobtainable fire item.
                // Keep those historical facts in the catalog without substituting a modern item.
                if (input != null && input.id() != 51 && !BY_LEGACY_ID.containsKey(new Key(input.id(), Math.max(0, input.metadata())))) {
                    throw new IllegalStateException("Unmapped Beta recipe ingredient");
                }
            }
        }
    }
}
