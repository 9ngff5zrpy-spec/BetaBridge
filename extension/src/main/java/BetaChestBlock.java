package cl.nostalgia.betabridge;

import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.*;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;

/** Beta chests are full, static cubes. Inventory remains an original Java container. */
final class BetaChestBlock {
    static void register(GeyserDefineCustomBlocksEvent event){
        if(!BetaCraftingRecipes.beta166())return;
        for(String facing:new String[]{"north","south","west","east"})
            for(String type:new String[]{"single","left","right"}){
                var components=CustomBlockComponents.builder().displayName("tile.chest.name").unitCube(true)
                    .collisionBox(BoxComponent.fullBox()).selectionBox(BoxComponent.fullBox())
                    .destructibleByMining(2.5f).friction(.6f).lightDampening(15);
                for(String face:new String[]{"down","up","north","south","west","east"})
                    components.materialInstance(face,MaterialInstance.builder()
                        .texture("betabridge_chest_"+texture(facing,type,face)).renderMethod("opaque")
                        .faceDimming(true).ambientOcclusion(true).build());
                var block=CustomBlockData.builder().name("betabridge_chest_"+facing+"_"+type)
                    .components(components.build()).includedInCreativeInventory(false).build();
                event.register(block);
                for(boolean water:new boolean[]{false,true})event.registerOverride(
                    "minecraft:chest[facing="+facing+",type="+type+",waterlogged="+water+"]",block.defaultBlockState());
                if(facing.equals("north")&&type.equals("single"))event.registerItemOverride("minecraft:chest",block);
            }
    }
    static int texture(String facing,String type,String face){
        if(face.equals("up")||face.equals("down"))return 25;
        boolean front=face.equals(facing),back=face.equals(switch(facing){
            case "north"->"south";case "south"->"north";case "west"->"east";case "east"->"west";
            default->throw new IllegalArgumentException("Chest facing");});
        return switch(type){
            case "single"->front?27:26;
            // Original e.a uses front 41/42 and rear 57/58 on connected cubes.
            case "left"->front?42:back?57:26;
            case "right"->front?41:back?58:26;
            default->throw new IllegalArgumentException("Chest type");
        };
    }
}
