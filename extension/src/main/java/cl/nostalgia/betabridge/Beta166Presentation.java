/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.nostalgia.betabridge;

import cl.betabridge.BetaJukeboxVoices;
import java.util.*;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.java.JavaRegistries;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

/** Beta-specific presentation; original server owns sleep and record state. */
final class Beta166Presentation {
    private final Map<GeyserSession,BetaJukeboxVoices> voices=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<GeyserSession,Long> boatDismounts=new java.util.concurrent.ConcurrentHashMap<>();
    void join(GeyserSession session){voices.put(session,new BetaJukeboxVoices(session::sendUpstreamPacket));}
    void leave(GeyserSession session){boatDismounts.remove(session);var active=voices.remove(session);if(active!=null&&!session.isClosed())active.stopAll();}
    void dismount(GeyserSession session){if(BetaCraftingRecipes.beta166())boatDismounts.put(session,System.nanoTime());}
    @SuppressWarnings("unchecked")
    void install(){
        if(!BetaCraftingRecipes.beta166())return;
        var registry=Registries.JAVA_PACKET_TRANSLATORS;
        var position=(PacketTranslator<ClientboundPlayerPositionPacket>)registry.get(ClientboundPlayerPositionPacket.class);
        var sound=(PacketTranslator<ClientboundLevelEventPacket>)registry.get(ClientboundLevelEventPacket.class);
        var respawn=(PacketTranslator<ClientboundRespawnPacket>)registry.get(ClientboundRespawnPacket.class);
        if(position==null||sound==null||respawn==null)throw new IllegalStateException("Pinned Geyser translators unavailable");
        registry.register(ClientboundPlayerPositionPacket.class,new PacketTranslator<ClientboundPlayerPositionPacket>(){
            public void translate(GeyserSession session,ClientboundPlayerPositionPacket packet){
                var entity=session.getPlayerEntity();var pending=entity.getPendingBedInteraction();
                boolean riding=entity.getVehicle()!=null;
                if(voices.containsKey(session)&&session.isSpawned()&&(riding||pending!=null)
                        && !packet.getRelatives().contains(PositionElement.X)
                        && !packet.getRelatives().contains(PositionElement.Y)
                        && !packet.getRelatives().contains(PositionElement.Z)
                        && (riding||betaBedTeleport(pending.headPosition(),packet.getPosition()))){
                    // Beta teleports to .1/.9 along the bed and Y+.9375, whereas
                    // upstream recognises only modern centre +.6875. Forwarding
                    // this teleport to Bedrock wakes the sleeping client.
                    var p=packet.getPosition();
                    float yaw=packet.getYRot()+(packet.getRelatives().contains(PositionElement.Y_ROT)?entity.getJavaYaw():0);
                    float pitch=packet.getXRot()+(packet.getRelatives().contains(PositionElement.X_ROT)?entity.getPitch():0);
                    entity.setPendingBedInteraction(null);
                    session.sendDownstreamGamePacket(new ServerboundAcceptTeleportationPacket(packet.getId(),p.getX(),p.getY(),p.getZ(),yaw,pitch));
                    // Beta waits for an exact feet-position acknowledgement before
                    // accepting the next action. Keep that state without a client teleport.
                    session.sendDownstreamGamePacket(new ServerboundMovePlayerPosRotPacket(true,false,p.getX(),p.getY(),p.getZ(),yaw,pitch));
                    entity.setPosition(p.toFloat());session.setUnconfirmedTeleport(null);
                    session.getGeyser().getLogger().info(riding
                        ? "Beta vehicle: exact server mount teleport acknowledged; block interaction unlocked."
                        : "Beta bed: server sleep teleport acknowledged without waking Bedrock.");
                    return;
                }
                position.translate(session,packet);
                Long leaving=boatDismounts.remove(session);
                if(leaving!=null&&System.nanoTime()-leaving<2_000_000_000L
                        && !packet.getRelatives().contains(PositionElement.X)
                        && !packet.getRelatives().contains(PositionElement.Y)
                        && !packet.getRelatives().contains(PositionElement.Z)){
                    // Beta exits onto its own box and waits for an exact movement
                    // ACK. Confirm that server pose before collision-adjusted input.
                    var p=packet.getPosition();
                    session.sendDownstreamGamePacket(new ServerboundMovePlayerPosRotPacket(true,false,
                        p.getX(),p.getY(),p.getZ(),entity.getJavaYaw(),entity.getPitch()));
                    session.getGeyser().getLogger().info("Beta boat: exact dismount pose acknowledged after server passenger removal: "+p);
                }
            }
        });
        registry.register(ClientboundLevelEventPacket.class,new PacketTranslator<ClientboundLevelEventPacket>(){
            public void translate(GeyserSession session,ClientboundLevelEventPacket packet){
                var active=voices.get(session);
                if(active!=null&&packet.getEvent()==LevelEventType.SOUND_STOP_JUKEBOX_SONG){
                    active.stop(packet.getPosition());
                    session.getGeyser().getLogger().info("Beta jukebox: original server stop event applied.");return;
                }
                if(active!=null&&packet.getEvent()==LevelEventType.SOUND_PLAY_JUKEBOX_SONG
                        &&packet.getData() instanceof RecordEventData record){
                    var song=JavaRegistries.JUKEBOX_SONG.get(session.javaRegistries(),record.getRecordId());
                    int disc=song.map(s->disc(s.soundEvent())).orElse(0);
                    if(disc!=0){
                        active.start(packet.getPosition(),disc);
                        session.getGeyser().getLogger().info("Beta jukebox: original server record event applied ("+(disc==1?"13":"Cat")+").");return;
                    }
                }
                sound.translate(session,packet);
            }
        });
        registry.register(ClientboundRespawnPacket.class,new PacketTranslator<ClientboundRespawnPacket>(){
            public void translate(GeyserSession session,ClientboundRespawnPacket packet){
                var active=voices.get(session);if(active!=null)active.stopAll();respawn.translate(session,packet);
            }
        });
    }
    static int disc(String event){return event.equals("minecraft:music_disc.13")||event.equals("music_disc.13")?1:
        event.equals("minecraft:music_disc.cat")||event.equals("music_disc.cat")?2:0;}
    static boolean betaBedTeleport(Vector3i head,Vector3d position){
        // Match the original float addition, including rounding far from spawn.
        double y=(double)((float)head.getY()+.9375f);
        double centreX=(double)((float)head.getX()+.5f),centreZ=(double)((float)head.getZ()+.5f);
        return near(position.getY(),y) && ((near(position.getX(),centreX)
            && (near(position.getZ(),(double)((float)head.getZ()+.1f))||near(position.getZ(),(double)((float)head.getZ()+.9f))))
            || (near(position.getZ(),centreZ)
            && (near(position.getX(),(double)((float)head.getX()+.1f))||near(position.getX(),(double)((float)head.getX()+.9f)))));
    }
    private static boolean near(double a,double b){return Math.abs(a-b)<1e-6;}
}
