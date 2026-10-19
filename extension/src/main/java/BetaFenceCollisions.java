/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.nostalgia.betabridge;

import java.util.ArrayList;
import java.util.Collections;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.translator.collision.BlockCollision;
import org.geysermc.geyser.translator.collision.OtherCollision;

/** Original gk.d collision: full X/Z block, 1.5 blocks high, independent of connections. */
final class BetaFenceCollisions {
    static BlockCollision shape() {
        return new OtherCollision(new BoundingBox[]{new BoundingBox(.5,.75,.5,1,1.5,1)});
    }
    static int install() {
        if (!BetaCraftingRecipes.beta166()) return 0;
        if (!"1262".equals(org.geysermc.geyser.GeyserImpl.BUILD_NUMBER))
            throw new IllegalStateException("Fence adapter requires pinned Geyser 1262");
        var collisions = new ArrayList<BlockCollision>(BlockRegistries.COLLISIONS.get());
        BlockCollision shape = shape();
        int changed = 0;
        for (var state : BlockRegistries.BLOCK_STATES) {
            if (state.is(Blocks.OAK_FENCE)) {
                collisions.set(state.javaId(), shape);
                changed++;
            }
        }
        if (changed != 32) throw new IllegalStateException("Expected 32 oak fence states, got " + changed);
        // Only this dedicated Beta process uses this table. Do not alter global
        // blocks or original server physics; keep other entries identical.
        BlockRegistries.COLLISIONS.set(Collections.unmodifiableList(collisions));
        return changed;
    }
}
