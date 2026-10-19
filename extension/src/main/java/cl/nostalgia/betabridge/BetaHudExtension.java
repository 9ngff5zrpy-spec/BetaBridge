package cl.nostalgia.betabridge;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.bedrock.camera.GuiElement;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.event.java.ServerUpdateEntityPassengersEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.session.GeyserSession;
import java.io.IOException;
import java.util.UUID;

/**
 * Applies the Beta 1.4_01 HUD profile to Bedrock connections on this dedicated bridge.
 *
 * The HUD profile does not change hunger values, sprinting,
 * swimming, crawling, movement or collisions, combat, food consumption,
 * authentication, or world and player data. The crafting adapters supply
 * the client-side result prediction omitted by Beta's protocol; Beta validates crafts.
 */
public final class BetaHudExtension implements Extension {
    private BetaCraftingRecipes recipes;
    private BetaCraftingRecipes tableRecipes;
    private boolean betaVisualPack;
    private final BetaInteractions interactions = new BetaInteractions();
    private final Beta166Presentation presentation = new Beta166Presentation();
    private final BetaBoatAppearance boatAppearance = new BetaBoatAppearance();

    @Subscribe public void onDefineEntities(GeyserDefineEntitiesEvent event){
        boatAppearance.define(event);
        if(BetaCraftingRecipes.beta166())logger().info(BetaBoatAppearance.nativeBoats()
            ?"Beta boat: native Bedrock visual; original Java Beta server controls hull movement."
            :"Beta boat: preserved classic custom hull registered through Geyser entity API; original Java Beta server controls movement.");
    }
    @Subscribe public void onSpawnEntity(ServerSpawnEntityEvent event){boatAppearance.spawn(event,betaVisualPack);}

    @Subscribe
    public void onCustomBlocks(GeyserDefineCustomBlocksEvent event) {
        BetaJukeboxBlock.register(event);
        BetaFenceBlock.register(event);
        BetaChestBlock.register(event);
    }

    @Subscribe
    public void onDismount(ServerUpdateEntityPassengersEvent.Dismount event) {
        if (event.connection() instanceof GeyserSession session && event.removedPassenger() == session.getPlayerEntity()) {
            interactions.confirmedDismount(session);
            if(event.vehicle() instanceof org.geysermc.geyser.entity.type.BoatEntity)presentation.dismount(session);
        }
    }

    @Subscribe
    public void onResourcePacks(GeyserDefineResourcePacksEvent event) {
        UUID betaPack = UUID.fromString("93317e0c-2a88-4433-95e8-4d931cfb1814");
        betaVisualPack = event.resourcePacks().stream().anyMatch(pack -> betaPack.equals(pack.uuid())
            && pack.manifest().header().version().major() == 0 && pack.manifest().header().version().minor() >= 2);
        if (betaVisualPack) {
            logger().info("Beta " + System.getProperty("betabridge.profile", "b1.4_01") + ": paquete de texturas y sonidos registrado para Bedrock.");
        } else {
            logger().warning("Beta " + System.getProperty("betabridge.profile", "b1.4_01") + ": no se encontró el paquete visual en la lista de Geyser.");
        }
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        interactions.install();
        if (BetaCraftingRecipes.beta166()) {
            boatAppearance.install();
            presentation.install();
            logger().info("Beta 1.6.6: Beta bed teleports and server-confirmed jukebox events enabled.");
            int states=BetaFenceCollisions.install();
            logger().info("Beta 1.6.6: original fence collision applied to " + states + " bridge states.");
        }
        try {
            recipes = BetaCraftingRecipes.load();
            BetaLegacyItems.initialize();
            BetaLegacyItems.verifyCatalog(recipes.catalog());
            logger().info("Beta " + System.getProperty("betabridge.profile", "b1.4_01") + " inventory crafting bridge loaded (" + recipes.size() + " recipes).");
        } catch (IOException | RuntimeException e) {
            recipes = null;
            logger().error("No se pudo cargar el adaptador de crafteo Beta: " + e.getClass().getSimpleName());
        }
        try {
            tableRecipes = BetaCraftingRecipes.loadTable();
            BetaLegacyItems.verifyCatalog(tableRecipes.catalog());
            logger().info("Beta " + System.getProperty("betabridge.profile", "b1.4_01") + " table crafting bridge loaded (" + tableRecipes.size() + " recipes).");
        } catch (IOException | RuntimeException e) {
            tableRecipes = null;
            logger().error("No se pudo cargar el adaptador de crafteo Beta 3x3: " + e.getClass().getSimpleName());
        }
    }

    @Subscribe
    public void onDisconnect(SessionDisconnectEvent event) {
        if(event.connection() instanceof GeyserSession session){presentation.leave(session);boatAppearance.leave(session);interactions.leave(session);}
    }

    @Subscribe
    public void onSessionJoin(SessionJoinEvent event) {
        if (event.connection() instanceof GeyserSession session) {
            interactions.enable(session);
            if(BetaCraftingRecipes.beta166())presentation.join(session);
        }
        event.connection().camera().hideElement(GuiElement.FOOD_BAR, GuiElement.PROGRESS_BAR);
        logger().info("Beta " + System.getProperty("betabridge.profile", "b1.4_01") + ": HUD sin hambre ni experiencia aplicado.");
        if (betaVisualPack && event.connection() instanceof GeyserSession session) {
            BetaAtmosphere.start(session, message -> logger().warning(message));
            logger().info("Beta " + System.getProperty("betabridge.profile", "b1.4_01") + ": perfil de niebla diurna/nocturna activado.");
        }
        if (recipes != null && event.connection() instanceof GeyserSession session) {
            try {
                BetaPlayerInventoryTranslator.install(session, recipes);
                logger().info("Beta " + System.getProperty("betabridge.profile", "b1.4_01") + ": predicción de crafteo 2x2 activada.");
            } catch (ReflectiveOperationException | RuntimeException e) {
                logger().error("Crafteo Beta no disponible: " + e.getClass().getSimpleName());
            }
        }
        if (tableRecipes != null && event.connection() instanceof GeyserSession session) {
            try {
                BetaCraftingTableInventoryTranslator.register(session, tableRecipes, message -> logger().error(message));
                logger().info("Beta " + System.getProperty("betabridge.profile", "b1.4_01") + ": predicción de crafteo 3x3 preparada para esta sesión.");
            } catch (RuntimeException e) {
                logger().error("Crafteo Beta 3x3 no disponible: " + e.getClass().getSimpleName());
            }
        }
    }
}
