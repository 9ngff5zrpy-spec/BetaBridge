/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.nostalgia.betabridge;

import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;

/** Plain visual block: Bedrock cannot invent a RecordItem or eject it locally. */
final class BetaJukeboxBlock {
    static void register(GeyserDefineCustomBlocksEvent event) {
        var components = CustomBlockComponents.builder().displayName("tile.jukebox.name").unitCube(true)
            .collisionBox(BoxComponent.fullBox()).selectionBox(BoxComponent.fullBox())
            .destructibleByMining(2f).friction(.6f).lightDampening(15)
            .materialInstance("*", material("jukebox_side"))
            .materialInstance("up", material("jukebox_top"))
            .materialInstance("down", material("planks")).build();
        var block = CustomBlockData.builder().name("betabridge_jukebox").components(components)
            .includedInCreativeInventory(false).build();
        event.register(block);
        for (String record : new String[]{"false", "true"})
            event.registerOverride("minecraft:jukebox[has_record=" + record + "]", block.defaultBlockState());
        event.registerItemOverride("minecraft:jukebox", block);
    }
    private static MaterialInstance material(String texture) {
        return MaterialInstance.builder().texture(texture).renderMethod("opaque")
            .faceDimming(true).ambientOcclusion(true).build();
    }
}
