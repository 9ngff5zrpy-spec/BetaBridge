/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.nostalgia.betabridge;

import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.*;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;

/** Share the Beta fence hitbox with Bedrock prediction as well as bridge validation. */
final class BetaFenceBlock {
    static void register(GeyserDefineCustomBlocksEvent event) {
        if (!BetaCraftingRecipes.beta166()) return;
        for (int mask=0; mask<16; mask++) {
            var components=CustomBlockComponents.builder()
                .geometry(GeometryComponent.builder().identifier("geometry.betabridge_fence_"+mask).build())
                .collisionBox(new BoxComponent(-8,0,-8,16,24,16))
                .selectionBox(BoxComponent.fullBox())
                .materialInstance("*",MaterialInstance.builder().texture("planks").renderMethod("opaque")
                    .faceDimming(true).ambientOcclusion(true).build())
                .destructibleByMining(2f).friction(.6f).lightDampening(0).build();
            var block=CustomBlockData.builder().name("betabridge_fence_"+mask).components(components)
                .includedInCreativeInventory(false).build();
            event.register(block);
            for (boolean water : new boolean[]{false,true}) {
                String state="minecraft:oak_fence[east="+((mask&1)!=0)+",north="+((mask&2)!=0)
                    +",south="+((mask&4)!=0)+",waterlogged="+water+",west="+((mask&8)!=0)+"]";
                event.registerOverride(state,block.defaultBlockState());
            }
        }
    }
}
