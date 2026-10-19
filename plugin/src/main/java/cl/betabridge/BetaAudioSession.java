/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.session.GeyserSession;
import net.raphimc.viaproxy.util.logging.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bedrock presentation only. The original Beta server owns records and drops. */
final class BetaAudioSession {
    private final GeyserSession session;
    private final Map<Vector3i, Integer> boxes = new HashMap<>();
    private final BetaJukeboxVoices voices;
    private boolean nativeRecordsCleared;

    BetaAudioSession(GeyserSession session) {
        this.session = session;
        this.voices = new BetaJukeboxVoices(session::sendUpstreamPacket);
        session.executeInEventLoop(this::tick);
    }

    void update(List<BetaJukeboxPackets.Change> changes) {
        if (session.isClosed()) return;
        for (var change : changes) {
            Vector3i pos = Vector3i.from(change.x(), change.y(), change.z());
            Integer old = boxes.get(pos);
            if (change.block() != 84) {
                if (old != null) { boxes.remove(pos); stopRecord(pos); }
                continue;
            }
            int record = change.metadata() == 1 || change.metadata() == 2 ? change.metadata() : 0;
            if (boxes.size() >= 4096 && old == null) continue;
            boxes.put(pos, record);
            if (old == null || old != record) {
                stopRecord(pos);
                if (record > 0 && session.isSpawned() && near(pos)) {
                    if (voices.start(pos, record)) {
                        Logger.LOGGER.info("[BetaBridge] Jukebox {}: metadata {} confirmed by Beta; named Bedrock playback started.", pos, record);
                    }
                } else if (old != null && old > 0) {
                    Logger.LOGGER.info("[BetaBridge] Jukebox {}: Beta confirmed removal; Bedrock playback stopped.", pos);
                }
            }
        }
    }

    void reset() {
        voices.stopAll(); boxes.clear(); nativeRecordsCleared = false;
    }

    private void tick() {
        if (session.isClosed()) { boxes.clear(); return; }
        try {
            if (session.isSpawned()) {
                if (!nativeRecordsCleared) {
                    voices.clearNativeRecords();
                    nativeRecordsCleared = true;
                }
                // The extension maps jukeboxes to a plain custom visual block.
                // Do not send native Jukebox actors: those enable local ejection.
                for (var entry : boxes.entrySet()) {
                    if (entry.getValue() > 0 && near(entry.getKey()) && !voices.contains(entry.getKey())) {
                        Vector3i pos = entry.getKey();
                        voices.start(pos, entry.getValue());
                    }
                }

            }
            session.scheduleInEventLoop(this::tick, 1, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            Logger.LOGGER.warn("[BetaBridge] Audio presentation stopped for this session ({}).", e.getClass().getSimpleName());
        }
    }

    private boolean near(Vector3i pos) {
        return pos.toFloat().distanceSquared(session.getPlayerEntity().position()) <= 64 * 64;
    }

    private void stopRecord(Vector3i pos) {
        voices.stop(pos);
    }

}
