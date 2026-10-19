/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.nostalgia.betabridge;

import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.geyser.session.cache.TeleportCache;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Keep Geyser's boat/IDs/links; select a generic, data-driven Bedrock visual. */
final class BetaBoatAppearance {
    static final String IDENTIFIER="betabridge:boat";
    static final float WIDTH=1.5f,HEIGHT=.6f,OFFSET=0;
    // ViaLegacy floors center-.3 to a 1/32 position, so restore 10/32.
    static final int MODEL_CENTRE_PIXELS=5;
    private CustomEntityDefinition definition;
    static boolean nativeBoats(){return !"custom".equals(System.getProperty("betabridge.boatRenderer","native"));}
    private record Trace(long started,long next,int samples){}
    private final Map<GeyserSession,Trace> traces=new ConcurrentHashMap<>();
    void leave(GeyserSession session){traces.remove(session);}

    @SuppressWarnings("unchecked")
    void install(){
        if(!BetaCraftingRecipes.beta166())return;
        var registry=Registries.JAVA_PACKET_TRANSLATORS;
        var original=(PacketTranslator<ClientboundSetPassengersPacket>)registry.get(ClientboundSetPassengersPacket.class);
        if(original==null)throw new IllegalStateException("Pinned passenger translator unavailable");
        registry.register(ClientboundSetPassengersPacket.class,new PacketTranslator<ClientboundSetPassengersPacket>(){
            public void translate(GeyserSession session,ClientboundSetPassengersPacket packet){
                var entity=session.getEntityCache().getEntityByJavaId(packet.getEntityId());
                boolean wasRiding=entity!=null&&session.getPlayerEntity().getVehicle()==entity;
                // Keep the official link/event handling and the original server's
                // passenger list. Repair dimensions AFTER both setVehicle calls.
                original.translate(session,packet);
                if(!(entity instanceof BoatEntity boat))return;
                serverControlled(boat);
                boat.updateBedrockMetadata();
                if(custom(boat)){
                 configure(boat);
                 for(var passenger:boat.getPassengers()){
                    if(passenger!=null&&passenger.getEntityType()==org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType.PLAYER){
                        seat(passenger);passenger.updateBedrockMetadata();
                    }
                }
                 boat.updateBedrockMetadata();
                }
                boolean riding=session.getPlayerEntity().getVehicle()==boat;
                if(wasRiding!=riding){
                    long now=System.nanoTime();traces.put(session,new Trace(now,now,0));
                    // BetaBoatControls supplies the old rider packet. Prevent
                    // proxy VehicleComponent physics between mount and first input.
                    session.setInClientPredictedVehicle(riding);
                    if(custom(boat)){
                     session.getPlayerEntity().setVehicleInput(Vector2f.ZERO);
                     session.setSteeringLeft(false);session.setSteeringRight(false);
                     if(!riding){
                        session.setInClientPredictedVehicle(false);
                        session.getPlayerEntity().setRiderSeatPosition(Vector3f.ZERO);
                        session.getPlayerEntity().updateBedrockMetadata();
                     }
                    }
                    session.getGeyser().getLogger().info("Beta boat transition: "+(riding?"mounted":"dismounted")
                        +"; box="+boat.getBoundingBoxWidth()+"x"+boat.getBoundingBoxHeight()
                        +"; actor="+boat.position()+"; player="+session.getPlayerEntity().position());
                }
            }
        });
        var removal=(PacketTranslator<ClientboundRemoveEntitiesPacket>)registry.get(ClientboundRemoveEntitiesPacket.class);
        if(removal==null)throw new IllegalStateException("Pinned entity removal translator unavailable");
        registry.register(ClientboundRemoveEntitiesPacket.class,new PacketTranslator<ClientboundRemoveEntitiesPacket>(){
            public void translate(GeyserSession session,ClientboundRemoveEntitiesPacket packet){
                var vehicle=session.getPlayerEntity().getVehicle();
                BoatEntity removed=vehicle instanceof BoatEntity boat
                    &&java.util.Arrays.stream(packet.getEntityIds()).anyMatch(id->id==boat.javaId())?boat:null;
                // Destruction is not a passenger packet. Upstream clears the
                // vehicle without firing Dismount; Beta sends no exit teleport
                // after a collision destroys its boat (lm.Q/D), either.
                Vector3f anchor=removed==null?null:removed.position();
                removal.translate(session,packet);
                if(removed==null||session.getPlayerEntity().getVehicle()!=null)return;
                session.setInClientPredictedVehicle(false);
                session.setSteeringLeft(false);session.setSteeringRight(false);
                var player=session.getPlayerEntity();
                player.setVehicleInput(Vector2f.ZERO);
                player.setRiderSeatPosition(Vector3f.ZERO);
                player.setRemovedPlayerVehicleId(null);
                player.updateBedrockMetadata();
                // Last server hull position, within one block of its old rider.
                // Never let the phone's stale pre-mount position become a new
                // absolute Java movement after an unannounced boat destruction.
                // This is a local correction, not a synthetic server teleport ACK.
                var target=exitAnchor(anchor);
                session.setUnconfirmedTeleport(new TeleportCache(session,target,player.getPitch(),player.getJavaYaw(),-1));
                player.moveAbsolute(target,player.getJavaYaw(),player.getPitch(),false,true);
                traces.remove(session);
                session.getGeyser().getLogger().info("Beta boat destroyed: local rider corrected to last server hull pose; stale movement gated until phone confirmation: "+target);
            }
        });
        var bedrock=Registries.BEDROCK_PACKET_TRANSLATORS;
        var input=(PacketTranslator<PlayerAuthInputPacket>)bedrock.get(PlayerAuthInputPacket.class);
        if(input==null)throw new IllegalStateException("Pinned player input translator unavailable");
        bedrock.register(PlayerAuthInputPacket.class,new PacketTranslator<PlayerAuthInputPacket>(){
            public void translate(GeyserSession session,PlayerAuthInputPacket packet){
                input.translate(session,packet);
                var trace=traces.get(session);if(trace==null)return;
                long now=System.nanoTime();
                if(now-trace.started()>2_000_000_000L||trace.samples()>=6){traces.remove(session);return;}
                if(now<trace.next())return;
                traces.put(session,new Trace(trace.started(),now+200_000_000L,trace.samples()+1));
                session.getGeyser().getLogger().info("Beta boat transition sample: riding="
                    +(session.getPlayerEntity().getVehicle()!=null)+"; phonePosition="+packet.getPosition()
                    +"; nativePrediction="+packet.getInputData().contains(org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)
                    +"; bridgePosition="+session.getPlayerEntity().position()+"; pendingTeleport="+(session.getUnconfirmedTeleport()!=null));
            }
        });
    }
    static boolean custom(BoatEntity boat){return IDENTIFIER.equals(boat.definition().identifier().toString());}
    static Vector3f exitAnchor(Vector3f hull){
        // Geyser stores native hull positions before adding its .375 render
        // offset, too. Both visual paths therefore share the Java feet anchor.
        // Stay above the removed hull's floor; Beta's next world tick owns fall.
        return hull.up(.35f);
    }
    static Vector3f seatPosition(){
        // dz.f: one seat, .4 forward along the old hull; player's feet at
        // center-.3. The generic visual's origin is center-10/32, not the
        // modern boat's origin assumed by EntityUtils (-.1-.35-.3).
        return Vector3f.from(0,VanillaEntities.PLAYER_ENTITY_OFFSET+MODEL_CENTRE_PIXELS/16f-.3f,-.4f);
    }
    static void seat(Entity passenger){passenger.setRiderSeatPosition(seatPosition());}

    void define(GeyserDefineEntitiesEvent event){
        if(!BetaCraftingRecipes.beta166()||nativeBoats())return;
        definition=CustomEntityDefinition.of(IDENTIFIER);event.register(definition);
    }
    void spawn(ServerSpawnEntityEvent event,boolean visualPack){
        if(!BetaCraftingRecipes.beta166()||!visualPack
                ||!event.entityType().identifier().toString().equals("minecraft:oak_boat"))return;
        if(nativeBoats()){
            event.preSpawnConsumer(entity->serverControlled((BoatEntity)entity));return;
        }
        if(definition==null)return;
        event.definition(definition);
        event.preSpawnConsumer(entity->{
            if(!(entity instanceof BoatEntity boat))throw new IllegalStateException("Pinned boat factory changed");
            configure(boat);
            boat.getSession().getGeyser().getLogger().info("Beta boat: custom actor selected for Java entity "
                +boat.javaId()+"; box="+boat.getBoundingBoxWidth()+"x"+boat.getBoundingBoxHeight()+"; offset="+boat.getOffset());
        });
    }
    static void serverControlled(BoatEntity boat){
        // No client steering seat: the phone sends controls, while original
        // Beta rider packets and server entity updates determine hull movement.
        boat.getMetadata().put(EntityDataTypes.CONTROLLING_RIDER_SEAT_INDEX,(byte)-1);
    }
    static void configure(BoatEntity boat){
            // The generic actor is anchored at feet, unlike Bedrock's native
            // boat renderer. Match the original Beta box instead of 1.6 width.
            // This callback runs on Geyser's event loop before actor spawn.
            boat.setBoundingBoxWidth(WIDTH);
            boat.setBoundingBoxHeight(HEIGHT);
            boat.setOffsetOverride(OFFSET);
            boat.getVehicleComponent().setWidth(WIDTH);
            boat.getVehicleComponent().setHeight(HEIGHT);
            // This flag controls BOTH the phone and VehicleComponent#getGravity.
            // Clearing it removes proxy gravity too and lets upward water motion
            // persist in air. Retain gravity even on the archived custom path.
            boat.setFlag(EntityFlag.HAS_GRAVITY,true);
            boat.setFlag(EntityFlag.HAS_COLLISION,true);
            boat.setFlag(EntityFlag.COLLIDABLE,true);
            // The Java server/Geyser supplies motion. Do not let the phone's
            // modern buoyancy move the actor independently of the Beta pose.
            boat.getMetadata().put(EntityDataTypes.IS_BUOYANT,false);
    }
}
