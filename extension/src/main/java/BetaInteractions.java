/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.nostalgia.betabridge;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.beta.b1_4_0_1tob1_5_0_2.Protocolb1_4_0_1Tob1_5_0_2;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import io.netty.util.AttributeKey;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.bedrock.BedrockInventoryTransactionTranslator;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;

import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import java.util.Map;
import java.util.WeakHashMap;

/** Pinned, session-gated adapters. All other packets keep their upstream translator. */
final class BetaInteractions {
    private static final boolean B166="b1.6.6".equals(System.getProperty("betabridge.profile","b1.4_01"));
    private static final Class<? extends com.viaversion.viaversion.api.protocol.Protocol> LEGACY_PROTOCOL = B166
        ? net.raphimc.vialegacy.protocol.beta.b1_6_0_6tob1_7_0_3.Protocolb1_6_0_6Tob1_7_0_3.class : Protocolb1_4_0_1Tob1_5_0_2.class;
    private final Map<GeyserSession, Pending> sessions = new WeakHashMap<>();
    private final BetaBoatControls boatControls=new BetaBoatControls();
    private record Pending(Vector3i block, long click, int boat, long leave) { }

    void enable(GeyserSession session) {
        synchronized (sessions) {
            if (sessions.putIfAbsent(session, new Pending(null, 0, -1, 0)) != null) return;
        }
        session.getDownstream().getSession().addListener(new SessionAdapter() {
            @Override public void packetSending(PacketSendingEvent event) {
                if(B166&&session.getPlayerEntity().getVehicle() instanceof BoatEntity
                    &&(event.getPacket() instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket
                    ||event.getPacket() instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPaddleBoatPacket
                    ||event.getPacket() instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket
                    ||event.getPacket() instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket)){
                    event.setCancelled(true);return;
                }
                if (B166 && event.getPacket() instanceof ServerboundUseItemPacket use
                        && use.getHand() == Hand.MAIN_HAND && session.getPlayerInventory().getItemInHand().is(Items.OAK_BOAT)) {
                    // Keep the original server authoritative. Never subtract a predicted
                    // boat locally: its SetSlot response confirms successful consumption.
                    event.setCancelled(true);
                    placeBoat(session, use);
                    return;
                }
                if (event.getPacket() instanceof ServerboundPlayerInputPacket input && input.isShift()
                        && session.getPlayerEntity().getVehicle() instanceof BoatEntity) {
                    // ViaLegacy can also turn modern shift input into right-click.
                    // Use exactly one path; otherwise two toggles can remount us.
                    event.setPacket(input.withShift(false));
                    session.executeInEventLoop(() -> requestLeaveBoat(session));
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    void install() {
        if (!"1262".equals(GeyserImpl.BUILD_NUMBER)) throw new IllegalStateException("Interaction adapter requires Geyser 1262");
        var registry = Registries.BEDROCK_PACKET_TRANSLATORS;
        var use = (PacketTranslator<InventoryTransactionPacket>) registry.get(InventoryTransactionPacket.class);
        var interact = (PacketTranslator<InteractPacket>) registry.get(InteractPacket.class);
        var input=(PacketTranslator<org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket>)registry.get(org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket.class);
        if (use == null || interact == null) throw new IllegalStateException("Missing upstream interaction translator");
        registry.register(InventoryTransactionPacket.class, new PacketTranslator<InventoryTransactionPacket>() {
            @Override public void translate(GeyserSession session, InventoryTransactionPacket packet) {
                if (!control(session,packet) && !jukebox(session, packet)) use.translate(session, packet);
            }
        });
        registry.register(InteractPacket.class, new PacketTranslator<InteractPacket>() {
            @Override public void translate(GeyserSession session, InteractPacket packet) {
                if (!leaveBoat(session, packet)) interact.translate(session, packet);
            }
        });
        registry.register(org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket.class,new PacketTranslator<org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket>(){
            public void translate(GeyserSession session,org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket packet){
                if(B166&&pending(session)!=null&&session.getPlayerEntity().getVehicle() instanceof BoatEntity)
                    boatControls.translate(session,packet,input,connection(session));
                else input.translate(session,packet);
            }
        });
    }
    void leave(GeyserSession session){boatControls.leave(session);synchronized(sessions){sessions.remove(session);}}

    private Pending pending(GeyserSession session) {
        synchronized (sessions) { return sessions.get(session); }
    }

    private void pending(GeyserSession session, Pending value) {
        synchronized (sessions) { sessions.put(session, value); }
    }

    void confirmedDismount(GeyserSession session) {
        boatControls.leave(session);
        Pending old = pending(session);
        if (old != null) pending(session, new Pending(old.block(), old.click(), -1, 0));
    }

    private boolean control(GeyserSession session,InventoryTransactionPacket packet) {
        Pending old=pending(session);
        if(!B166||old==null||packet.getTransactionType()!=InventoryTransactionType.ITEM_USE||packet.getActionType()!=0)return false;
        var pos=packet.getBlockPosition();if(pos==null)return false;
        var block=session.getGeyser().getWorldManager().blockAt(session,pos).block();
        if(block!=Blocks.LEVER&&block!=Blocks.STONE_BUTTON&&block!=Blocks.REPEATER)return false;
        int slot=packet.getHotbarSlot(),face=packet.getBlockFace();
        if(slot<0||slot>8||face<0||face>5||pos.getY()<0||pos.getY()>=128)return true;
        UserConnection user=connection(session);if(user==null)return false;
        long now=System.nanoTime();
        if(pos.equals(old.block())&&now-old.click()<110_000_000L)return true;
        pending(session,new Pending(pos,now,old.boat(),old.leave()));
        session.getPlayerInventory().setHeldItemSlot(slot);
        session.setLastInteractionBlockPosition(pos);session.setLastInteractionTime(System.currentTimeMillis());session.setInteracting(true);
        user.getChannel().eventLoop().execute(()->{
            try {
                BetaLegacyActions.select(user,slot).sendToServer(LEGACY_PROTOCOL,true);
                BetaLegacyActions.control(user,pos.getX(),pos.getY(),pos.getZ(),face).sendToServer(LEGACY_PROTOCOL,true);
                session.getGeyser().getLogger().info("Beta control: one original-server interaction sent; awaiting authoritative state.");
            }catch(Exception e){session.getGeyser().getLogger().error("Beta control interaction failed",e);}
        });
        return true;
    }

    private boolean jukebox(GeyserSession session, InventoryTransactionPacket packet) {
        Pending old = pending(session);
        if (old == null || packet.getTransactionType() != InventoryTransactionType.ITEM_USE || packet.getActionType() != 0) return false;
        Vector3i pos = packet.getBlockPosition();
        if (pos == null || session.getGeyser().getWorldManager().blockAt(session, pos).block() != Blocks.JUKEBOX) return false;
        // Never fall through to native jukebox prediction for a registered Beta session.
        int slot = packet.getHotbarSlot(), face = packet.getBlockFace();
        if (slot < 0 || slot > 8 || face < 0 || face > 5 || pos.getY() < 0 || pos.getY() >= 128) return true;
        if (!BedrockInventoryTransactionTranslator.canInteractWithBlock(session,
                session.getPlayerEntity().position().up(session.getEyeHeight()), pos)) return true;
        UserConnection user = connection(session);
        if (user == null) return true;
        long now = System.nanoTime();
        if (pos.equals(old.block()) && now - old.click() < 300_000_000L) return true;
        var inventory = session.getPlayerInventory();
        var held = inventory.getItem(inventory.getOffsetForHotbar(slot));
        if (packet.getItemInHand() != null && session.getItemMappings().getMapping(packet.getItemInHand()).getJavaItem().javaId() != held.getJavaId()) {
            session.getPlayerInventoryHolder().updateInventory();
            return true;
        }
        int record = held.is(Items.MUSIC_DISC_13) ? 2256 : held.is(Items.MUSIC_DISC_CAT) ? 2257 : 0;
        inventory.setHeldItemSlot(slot);
        pending(session, new Pending(pos, now, old.boat(), old.leave()));
        user.getChannel().eventLoop().execute(() -> {
            try {
                // A selected-slot update and one use, in order on the Beta connection.
                BetaLegacyActions.select(user, slot).sendToServer(LEGACY_PROTOCOL, true);
                BetaLegacyActions.jukebox(user, pos.getX(), pos.getY(), pos.getZ(), face, record, held.getAmount())
                    .sendToServer(LEGACY_PROTOCOL, true);
                session.getGeyser().getLogger().info("Beta jukebox: one original-server interaction sent; awaiting authoritative metadata.");
            } catch (Exception e) {
                session.getGeyser().getLogger().error("Beta jukebox interaction failed", e);
            }
        });
        return true;
    }

    private void placeBoat(GeyserSession session, ServerboundUseItemPacket use) {
        UserConnection user = connection(session);
        if (user == null) return;
        var inventory = session.getPlayerInventory();
        int count = inventory.getItemInHand().getAmount(), slot = inventory.getHeldItemSlot();
        float yaw = use.getYRot(), pitch = use.getXRot();
        float restoreYaw = session.getPlayerEntity().getJavaYaw(), restorePitch = session.getPlayerEntity().getPitch();
        boolean onGround = session.getPlayerEntity().isOnGround();
        if (count < 1 || !Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) return;
        user.getChannel().eventLoop().execute(() -> {
            if (!user.getChannel().isActive()) return;
            try {
                BetaLegacyActions.select(user, slot).sendToServer(LEGACY_PROTOCOL, true);
                BetaLegacyActions.look(user, yaw, pitch, onGround).sendToServer(LEGACY_PROTOCOL, true);
                BetaLegacyActions.boat(user, count).sendToServer(LEGACY_PROTOCOL, true);
                BetaLegacyActions.look(user, restoreYaw, restorePitch, onGround).sendToServer(LEGACY_PROTOCOL, true);
                session.getGeyser().getLogger().info("Beta boat: touch aim/use/restore sent; awaiting original-server spawn and inventory confirmation.");
            } catch (Exception e) {
                session.getGeyser().getLogger().error("Beta boat placement failed", e);
            }
        });
    }

    private boolean leaveBoat(GeyserSession session, InteractPacket packet) {
        if (pending(session) == null || packet.getAction() != InteractPacket.Action.LEAVE_VEHICLE
                || !(session.getPlayerEntity().getVehicle() instanceof BoatEntity)) return false;
        requestLeaveBoat(session);
        return true;
    }

    private void requestLeaveBoat(GeyserSession session) {
        Pending old = pending(session);
        if (old == null || session.isClosed() || !(session.getPlayerEntity().getVehicle() instanceof BoatEntity boat)) return;
        UserConnection user = connection(session);
        if (user == null) return;
        long now = System.nanoTime();
        if (old.boat() == boat.getEntityId() && now - old.leave() < 1_500_000_000L) return;
        pending(session, new Pending(old.block(), old.click(), boat.getEntityId(), now));
        session.setSteeringLeft(false);
        session.setSteeringRight(false);
        session.setShouldSendSneak(false);
        if (session.getMountVehicleScheduledFuture() != null) session.getMountVehicleScheduledFuture().cancel(false);
        user.getChannel().eventLoop().execute(() -> {
            try {
                BetaLegacyActions.dismount(user, session.getPlayerEntity().getEntityId(), boat.getEntityId())
                    .sendToServer(LEGACY_PROTOCOL, true);
                session.getGeyser().getLogger().info("Beta boat: original right-click dismount sent; awaiting server passenger update.");
            } catch (Exception e) {
                session.getGeyser().getLogger().error("Beta boat dismount failed", e);
            }
        });
    }

    private static UserConnection connection(GeyserSession session) {
        if (session.isClosed() || session.getDownstream() == null || session.getDownstream().getSession() == null) return null;
        for (var user : Via.getManager().getConnectionManager().getConnections()) {
            if (user.getChannel() == null || !user.getChannel().isActive()) continue;
            var proxy = ProxyConnection.fromChannel(user.getChannel());
            // Geyser IP passthrough replaces C2P.remoteAddress with the phone's IP.
            // Reuse the session binding verified by BetaBridge's identity plugin.
            if (proxy != null && (B166 ? LegacyProtocolVersion.b1_6tob1_6_6 : LegacyProtocolVersion.b1_4tob1_4_1).equals(proxy.getServerVersion())
                    && proxy.getC2P().attr(AttributeKey.<GeyserSession>valueOf("betabridge.geyserSession")).get() == session) {
                UserConnection backend=BetaBackendRoute.canonical(user,LEGACY_PROTOCOL);
                if(backend!=null)return backend;
            }
        }
        return null;
    }
}
