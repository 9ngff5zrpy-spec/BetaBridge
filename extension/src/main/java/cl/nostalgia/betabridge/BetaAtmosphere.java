package cl.nostalgia.betabridge;

import org.geysermc.geyser.session.GeyserSession;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Selects the matching resource-pack fog through Geyser's existing camera API.
 * Uses the pack's selected classic view distance; it does not replace Bedrock lighting.
 * Must be started once per session after the matching pack was registered.
 */
public final class BetaAtmosphere {
    private static final String PREFIX = "betabridge:beta_";
    private final GeyserSession session;
    private final Consumer<String> warning;
    private String current;

    private BetaAtmosphere(GeyserSession session, Consumer<String> warning) {
        this.session = session;
        this.warning = warning;
    }

    public static void start(GeyserSession session, Consumer<String> warning) {
        BetaAtmosphere atmosphere = new BetaAtmosphere(session, warning);
        session.executeInEventLoop(atmosphere::update);
    }

    private void update() {
        if (session.isClosed()) {
            return;
        }
        try {
            // SessionJoin may precede the Java spawn packet. Waiting also avoids
            // sending a day preset before the server's dimension and time arrive.
            if (session.isSpawned() && session.getDimensionType() != null) {
                String next = namespace(session.getDayTimeTicks(),
                        session.getDimensionType().isNetherLike(),
                        session.getDimensionType().bedrockId());
                if (!java.util.Objects.equals(current, next)) {
                    if (current != null) {
                        session.camera().removeFog(current);
                    }
                    if (next != null) {
                        session.camera().sendFog(next);
                    }
                    current = next;
                }
            }
            // Work stays on this connection's event loop; no shared timer thread
            // or retained global session map is needed after disconnect.
            session.scheduleInEventLoop(this::update, 1, TimeUnit.SECONDS);
        } catch (RuntimeException error) {
            // A missing pack/API change must not disconnect a player or alter
            // another plugin's fog stack. Stop only this optional visual adapter.
            if (current != null) {
                try {
                    session.camera().removeFog(current);
                } catch (RuntimeException ignored) {
                    // The transport may already be closing.
                }
            }
            warning.accept("Niebla Beta desactivada en esta sesión: " + error.getClass().getSimpleName());
        }
    }

    static String namespace(long dayTimeTicks, boolean netherLike, int bedrockDimensionId) {
        if (netherLike) {
            return PREFIX + "nether";
        }
        if (bedrockDimensionId != 0) {
            return null;
        }
        int phase = (int) (Math.floorMod(dayTimeTicks, 24000L) / 500);
        return PREFIX + "overworld_" + (phase < 10 ? "0" : "") + phase;
    }
}
