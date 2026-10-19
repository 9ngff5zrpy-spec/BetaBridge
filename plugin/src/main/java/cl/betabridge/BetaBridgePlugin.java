/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * BetaBridge is distributed under the GNU GPL v3 or later; see LICENSE.
 */
package cl.betabridge;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.buffer.ByteBuf;
import com.viaversion.viaversion.platform.ViaCodecHandler;
import io.netty.util.AttributeKey;
import net.lenni0451.lambdaevents.EventHandler;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.plugins.events.Client2ProxyChannelInitializeEvent;
import net.raphimc.viaproxy.plugins.events.FillPlayerDataEvent;
import net.raphimc.viaproxy.plugins.events.Proxy2ServerChannelInitializeEvent;
import net.raphimc.viaproxy.plugins.events.types.ITyped;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.proxy.util.CloseAndReturn;
import net.raphimc.viaproxy.util.logging.Logger;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.session.GeyserSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First milestone: preserve a Bedrock player's Beta playerfile through gamertag changes.
 * Geyser performs Xbox authentication; this plugin never skips authentication events.
 * Requires the proxy's TCP listener and Beta backend to be IPv4 loopback only.
 */
public final class BetaBridgePlugin extends ViaProxyPlugin {
    private static final AttributeKey<SocketAddress> ORIGINAL_REMOTE =
        AttributeKey.valueOf("betabridge.originalRemote");
    private static final AttributeKey<GeyserSession> GEYSER_SESSION =
        AttributeKey.valueOf("betabridge.geyserSession");

    private static final boolean B166="b1.6.6".equals(System.getProperty("betabridge.profile","b1.4_01"));
    private BridgeConfig config;
    private LegacyPlayerNames playerNames;
    private final ConcurrentHashMap<String, Channel> activeAccounts = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        try {
            config = BridgeConfig.load(getDataFolder().toPath().resolve("beta-bridge.properties"));
            playerNames = new LegacyPlayerNames(getDataFolder().toPath().resolve("player-names.properties"),
                config.playerDirectory());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load BetaBridge's configuration", e);
        }
        ViaProxy.EVENT_MANAGER.register(this);
        Logger.LOGGER.info("[BetaBridge] Stable Bedrock legacy identities enabled; local pinned Beta target required.");
    }

    @Override
    public void onDisable() {
        ViaProxy.EVENT_MANAGER.unregister(this);
    }

    @EventHandler
    public void onChannelInitialize(Client2ProxyChannelInitializeEvent event) {
        if (event.getType() != ITyped.Type.PRE || event.isLegacyPassthrough()) return;
        Channel channel = event.getChannel();
        // Geyser's own POST listener may replace remoteAddress with the Bedrock IP.
        // Capture the original TCP tuple before any IP passthrough takes place.
        SocketAddress remote = channel.remoteAddress();
        channel.attr(ORIGINAL_REMOTE).set(remote);
        GeyserSession session = findGeyserSession(remote);
        if (session != null) channel.attr(GEYSER_SESSION).set(session);
    }

    @EventHandler
    public void onBackendInitialize(Proxy2ServerChannelInitializeEvent event) {
        if (event.getType() != ITyped.Type.POST || event.isLegacyPassthrough()) return;
        ProxyConnection connection = ProxyConnection.fromChannel(event.getChannel());
        if (!(B166?LegacyProtocolVersion.b1_6tob1_6_6:LegacyProtocolVersion.b1_4tob1_4_1).equals(connection.getServerVersion())) return;
        GeyserSession session = connection.getC2P().attr(GEYSER_SESSION).get();
        if (session == null) session = findGeyserSession(connection.getC2P().attr(ORIGINAL_REMOTE).get());
        if (session == null || session.isClosed() || session.getAuthData() == null) return;
        if(B166)attachFishingObserver(event.getChannel(),session);
        else attachJukeboxObserver(event.getChannel(), session);
    }

    private void attachFishingObserver(Channel backend,GeyserSession bedrock){
        if(!backend.eventLoop().inEventLoop()){
            backend.eventLoop().execute(()->attachFishingObserver(backend,bedrock));return;
        }
        if(backend.pipeline().get("betabridge-fishing-bite")!=null
                ||backend.pipeline().get(ViaCodecHandler.NAME)==null||bedrock.isClosed())return;
        backend.pipeline().addBefore(ViaCodecHandler.NAME,"betabridge-fishing-bite",new io.netty.channel.ChannelDuplexHandler(){
            private int rodActions;
            public void channelRead(ChannelHandlerContext ctx,Object message)throws Exception{
                var bite=message instanceof ByteBuf buffer?BetaFishingBites.read(buffer):null;
                if(bite!=null){
                    // Only our precisely identified notification is consumed;
                    // normal Beta packets keep their original bytes and route.
                    io.netty.util.ReferenceCountUtil.release(message);
                    bedrock.executeInEventLoop(()->BetaFishingBites.present(bedrock,bite));return;
                }
                ctx.fireChannelRead(message);
            }
            public void write(ChannelHandlerContext ctx,Object message,io.netty.channel.ChannelPromise promise)throws Exception{
                if(message instanceof ByteBuf b&&BetaFishingBites.rodUse(b)){
                    bedrock.executeInEventLoop(()->{
                     if(bedrock.getPlayerInventory().getItemInHand().is(org.geysermc.geyser.item.Items.FISHING_ROD)){
                        int count=++rodActions;
                        bedrock.getGeyser().getLogger().info("Beta fishing: original rod action "+count+" forwarded to backend.");
                     }
                    });
                }
                ctx.write(message,promise);
            }
        });
        Logger.LOGGER.info("[BetaBridge] Original-server fishing bite observer attached to authenticated Bedrock backend.");
    }

    private void attachJukeboxObserver(Channel backend, GeyserSession session) {
        if(B166)return; // 1.6.6 jukebox uses translated level events in the extension.
        if (!backend.eventLoop().inEventLoop()) {
            backend.eventLoop().execute(() -> attachJukeboxObserver(backend, session));
            return;
        }
        if (backend.pipeline().get("betabridge-jukebox-observer") != null
            || backend.pipeline().get(ViaCodecHandler.NAME) == null || session.isClosed()) return;
        final GeyserSession bedrock = session;
        final BetaAudioSession audio = new BetaAudioSession(session);
        backend.pipeline().addBefore(ViaCodecHandler.NAME, "betabridge-jukebox-observer", new ChannelInboundHandlerAdapter() {
            private boolean warned;
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
                java.util.List<BetaJukeboxPackets.Change> changes = java.util.List.of();
                boolean reset = false;
                try {
                    if (message instanceof ByteBuf buffer && buffer.isReadable()) {
                        reset = buffer.getUnsignedByte(buffer.readerIndex()) == 9;
                        changes = BetaJukeboxPackets.read(buffer);
                    }
                } catch (Exception e) {
                    if (!warned) {
                        warned = true;
                        Logger.LOGGER.warn("[BetaBridge] Could not observe an optional jukebox update ({}).", e.getClass().getSimpleName());
                    }
                }
                // Preserve the original buffer, indexes and normal ViaLegacy route.
                ctx.fireChannelRead(message);
                if (reset) bedrock.executeInEventLoop(audio::reset);
                if (!changes.isEmpty()) {
                    var confirmed = changes;
                    bedrock.executeInEventLoop(() -> audio.update(confirmed));
                }
            }
        });
        Logger.LOGGER.info("[BetaBridge] Read-only Beta jukebox observer and disc playback attached.");
    }

    @EventHandler
    public void onFillPlayerData(FillPlayerDataEvent event) {
        ProxyConnection connection = event.getProxyConnection();
        Channel channel = connection.getC2P();
        GeyserSession session = channel.attr(GEYSER_SESSION).get();
        if (session == null) {
            // Resolve again for an accepted TCP channel that raced Geyser's bookkeeping.
            session = findGeyserSession(channel.attr(ORIGINAL_REMOTE).get());
        }
        if (session == null) return; // Java loopback clients keep ViaProxy's normal auth flow.

        try {
            applyIdentity(connection, session);
        } catch (CloseAndReturn expectedKick) {
            throw expectedKick;
        } catch (RuntimeException | LinkageError e) {
            // LambdaEvents reports listener exceptions and continues. Never allow a known
            // Bedrock session through with the wrong playerfile if our integration fails.
            Logger.LOGGER.error("[BetaBridge] Identity integration failed ({}); rejected login.",
                e.getClass().getSimpleName());
            connection.kickClient("BetaBridge: identity integration unavailable; contact the server owner.");
        }
    }

    private void applyIdentity(ProxyConnection connection, GeyserSession session) {
        String error = checkEnvironment(connection, session);
        if (error != null) {
            Logger.LOGGER.warn("[BetaBridge] Rejected a Bedrock login: {}", error);
            connection.kickClient("BetaBridge: " + error);
            return;
        }
        final LegacyIdentity identity;
        try {
            identity = LegacyIdentity.fromXuid(session.xuid());
        } catch (IllegalArgumentException e) {
            Logger.LOGGER.warn("[BetaBridge] Rejected a Bedrock login without a valid verified identity.");
            connection.kickClient("BetaBridge: authenticated Xbox identity is required.");
            return;
        }
        String xuid = session.xuid();
        Channel channel = connection.getC2P();
        Channel existing = activeAccounts.putIfAbsent(xuid, channel);
        if (existing != null && existing != channel) {
            if (!existing.isActive()) activeAccounts.remove(xuid, existing);
            else {
                connection.kickClient("BetaBridge: this Xbox account is already connected.");
                return;
            }
            if (activeAccounts.putIfAbsent(xuid, channel) != null) {
                connection.kickClient("BetaBridge: reconnect after the previous session closes.");
                return;
            }
        }
        channel.closeFuture().addListener(ignored -> activeAccounts.remove(xuid, channel));
        final String visibleName;
        try {
            visibleName = playerNames.resolve(xuid, session.getAuthData().name());
        } catch (IOException e) {
            connection.kickClient("BetaBridge: could not safely load the legacy player identity.");
            return;
        }
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + visibleName).getBytes(StandardCharsets.UTF_8));
        // Beta's visible login name is also its player-file key. The registry migrates
        // existing BE_ data before login, preserving the authenticated account's inventory.
        connection.setGameProfile(new GameProfile(offlineUuid, visibleName));
        try {
            BedrockSkinCache.Result skin = BedrockSkinCache.export(config.skinCacheDirectory(), visibleName, session);
            Logger.LOGGER.info("[BetaBridge] Local LegacyFix skin cache: {}.", skin.status());
        } catch (IOException | RuntimeException e) {
            // Cosmetic failure must not reject a verified account or change its player data.
            Logger.LOGGER.warn("[BetaBridge] Could not prepare the local skin cache ({}).", e.getClass().getSimpleName());
        }
        // Avoid logging XUIDs, aliases (which encode XUIDs), gamertags, or login tokens.
        Logger.LOGGER.info("[BetaBridge] Applied authenticated Bedrock gamertag and preserved legacy player data.");
        connection.getC2P().attr(GEYSER_SESSION).set(session);
        try {
            if (connection.getUserConnection() != null) {
                attachJukeboxObserver(connection.getUserConnection().getChannel(), session);
            }
        } catch (RuntimeException | LinkageError e) {
            Logger.LOGGER.warn("[BetaBridge] Optional jukebox presentation unavailable ({}).", e.getClass().getSimpleName());
        }
    }

    private String checkEnvironment(ProxyConnection connection, GeyserSession session) {
        if (!isIpv4Loopback(ViaProxy.getConfig().getBindAddress(), config.proxyPort())
            || !isIpv4Loopback(ViaProxy.getConfig().getTargetAddress(), config.backendPort())
            || !isIpv4Loopback(connection.getServerAddress(), config.backendPort())) {
            return "this prototype requires local-only proxy and backend TCP listeners.";
        }
        if (!(B166 ? LegacyProtocolVersion.b1_6tob1_6_6 : LegacyProtocolVersion.b1_4tob1_4_1).equals(connection.getServerVersion())) {
            return "the backend must match the explicitly selected Beta profile.";
        }
        GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser == null || !geyser.config().advanced().bedrock().validateBedrockLogin()
            || geyser.config().advanced().bedrock().useWaterdogpeForwarding()) {
            return "direct Xbox authentication must remain enabled in Geyser.";
        }
        if (geyser.config().java().authType() != AuthType.OFFLINE) {
            return "this prototype expects Geyser's private offline Java transport.";
        }
        if (session.isClosed() || session.getAuthData() == null) {
            return "the authenticated Bedrock session is no longer available.";
        }
        String expectedName = session.getAuthData().name().replace(' ', '_');
        if (connection.getGameProfile() == null
            || !expectedName.equals(connection.getGameProfile().getName())) {
            return "the Java transport does not match its authenticated Bedrock session.";
        }
        return null;
    }

    private static boolean isIpv4Loopback(SocketAddress address, int port) {
        return address instanceof InetSocketAddress inet && inet.getPort() == port
            && inet.getAddress() != null && "127.0.0.1".equals(inet.getAddress().getHostAddress());
    }

    private static GeyserSession findGeyserSession(SocketAddress address) {
        GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser == null || address == null) return null;
        // Same socket tuple comparison used by GeyserViaProxyPlugin's IP passthrough.
        return geyser.onlineConnections().stream()
            .filter(session -> session.getDownstream() != null)
            .filter(session -> address.equals(session.getDownstream().getSession().getLocalAddress()))
            .findFirst().orElse(null);
    }
}
