/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.cloudburstmc.protocol.bedrock.packet.StopSoundPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** One named sound per jukebox, so a stop also reaches the exact sound started. */
public final class BetaJukeboxVoices {
    static final int CHANNELS = 16;
    record Voice(int record, int channel, String event) { }
    private final Map<Vector3i, Voice> active = new HashMap<>();
    private final Consumer<BedrockPacket> send;

    public BetaJukeboxVoices(Consumer<BedrockPacket> send) {
        this.send = send;
    }

    boolean contains(Vector3i pos) { return active.containsKey(pos); }

    public boolean start(Vector3i pos, int record) {
        if (record != 1 && record != 2) throw new IllegalArgumentException("Unknown Beta record");
        Voice previous = active.get(pos);
        if (previous != null && previous.record() == record) return true;
        stop(pos);
        boolean[] occupied = new boolean[CHANNELS];
        for (Voice voice : active.values()) occupied[voice.channel()] = true;
        int channel = 0;
        while (channel < CHANNELS && occupied[channel]) channel++;
        if (channel == CHANNELS) return false;
        Voice voice = new Voice(record, channel, event(record, channel));
        var packet = new PlaySoundPacket();
        packet.setSound(voice.event());
        packet.setPosition(pos.toFloat().add(.5f, .5f, .5f));
        packet.setVolume(4f);
        packet.setPitch(1f);
        // This uses the named playsound/stopsound path. No numeric handle is
        // attached, and no client-predicted record shares this custom event.
        send.accept(packet);
        active.put(pos, voice);
        return true;
    }

    public void stop(Vector3i pos) {
        Voice voice = active.remove(pos);
        if (voice != null) stopEvent(voice.event());
    }

    public void stopAll() {
        for (Voice voice : active.values()) stopEvent(voice.event());
        active.clear();
    }

    void clearNativeRecords() {
        stopEvent("record.13");
        stopEvent("record.cat");
    }

    private void stopEvent(String event) {
        var packet = new StopSoundPacket();
        packet.setSoundName(event);
        packet.setStoppingAllSound(false);
        packet.setStopMusicLegacy(false);
        send.accept(packet);
    }

    static String event(int record, int channel) {
        if ((record != 1 && record != 2) || channel < 0 || channel >= CHANNELS) {
            throw new IllegalArgumentException("Invalid Beta jukebox channel");
        }
        return "betabridge.jukebox." + (record == 1 ? "13" : "cat") + "." + channel;
    }
}
