package cl.betabridge;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Checks actual emitted play/stop packets, multi-box isolation and the pack contract. */
public final class BetaJukeboxVoicesTest {
    public static void main(String[] args) throws Exception {
        List<BedrockPacket> sent = new ArrayList<>();
        var voices = new BetaJukeboxVoices(sent::add);
        Vector3i a = Vector3i.from(-41,66,-25), b = a.add(2,0,0);
        check(voices.start(a,1), "13 starts");
        var first = (PlaySoundPacket) sent.get(0);
        check(first.getServerSoundHandle() == null && first.getLoopCount() == 0, "named single playback, no numeric handle");
        int before = sent.size();
        check(voices.start(a,1) && sent.size() == before, "duplicate block update does not replay a disc");
        check(voices.start(b,1), "second jukebox starts the same disc");
        var second = (PlaySoundPacket) sent.get(1);
        check(!first.getSound().equals(second.getSound()), "same disc in two jukeboxes has independent stop names");
        voices.stop(a);
        var stop = (StopSoundPacket) sent.get(2);
        check(stop.getSoundName().equals(first.getSound()), "ejection stops exactly the event originally played");
        check(voices.contains(b) && !voices.contains(a), "other jukebox remains active");
        before=sent.size();voices.stop(a);
        check(sent.size()==before, "repeated empty-box update sends no new playback");
        before=sent.size();voices.start(b,2);
        check(sent.get(before) instanceof StopSoundPacket old && old.getSoundName().equals(second.getSound()), "changing disc stops old track first");
        check(sent.get(before+1) instanceof PlaySoundPacket next && next.getSound().contains(".cat."), "then Cat starts");
        voices.stopAll();check(!voices.contains(b), "dimension reset stops all owned jukebox voices");
        voices.clearNativeRecords();
        check(((StopSoundPacket)sent.get(sent.size()-2)).getSoundName().equals("record.13"), "old native 13 cleared");
        check(((StopSoundPacket)sent.get(sent.size()-1)).getSoundName().equals("record.cat"), "old native Cat cleared");
        for (BedrockPacket packet : sent) roundTrip(packet);

        sent.clear();voices=new BetaJukeboxVoices(sent::add);
        for(int i=0;i<BetaJukeboxVoices.CHANNELS;i++) check(voices.start(Vector3i.from(i,64,0),2),"available channel");
        before=sent.size();
        check(!voices.start(Vector3i.from(99,64,0),2) && sent.size()==before, "channel limit cannot steal or overlap another box");
        voices.stop(Vector3i.from(0,64,0));
        check(voices.start(Vector3i.from(99,64,0),2), "released channel can be reused after its stop");

        Path pack=Path.of(args[0]);
        var definitions=JsonParser.parseString(Files.readString(pack.resolve("sounds/sound_definitions.json"))).getAsJsonObject().getAsJsonObject("sound_definitions");
        Set<String> audio=new HashSet<>();
        for(String disc:List.of("13","cat")) {
            check(definitions.getAsJsonObject("record."+disc).getAsJsonArray("sounds").isEmpty(), "native prediction is silent");
            for(int i=0;i<BetaJukeboxVoices.CHANNELS;i++) {
                String event=BetaJukeboxVoices.event(disc.equals("13")?1:2,i);
                var definition=definitions.getAsJsonObject(event);
                check(definition.get("category").getAsString().equals("record"), "disc uses record volume category");
                var sound=definition.getAsJsonArray("sounds").get(0).getAsJsonObject();
                String path=sound.get("name").getAsString();
                check(sound.get("stream").getAsBoolean() && Files.isRegularFile(pack.resolve(path+".ogg")), "channel references a streamed existing OGG");
                audio.add(path);
            }
        }
        check(audio.size()==2, "all named channels reuse the same two disc files");
        check(!Files.exists(pack.resolve("sounds/music_definitions.json")), "background music scheduler unchanged");
        System.out.println("BetaJukeboxVoicesTest passed: matching start/stop on Bedrock 2193 wire, repeated updates, 13/Cat changes, independent same-disc boxes, reset, capacity and pack channels reusing two OGGs.");
    }

    private static void roundTrip(BedrockPacket packet) {
        var codec=Bedrock_v2193.CODEC;var helper=codec.createHelper();var buffer=Unpooled.buffer();
        try {
            codec.tryEncode(helper,buffer,packet);
            int id=codec.getPacketDefinition(packet.getClass()).getId();
            BedrockPacket decoded=codec.tryDecode(helper,buffer,id);
            check(!buffer.isReadable(), "packet decodes without trailing fields");
            if(packet instanceof PlaySoundPacket before) {
                var after=(PlaySoundPacket)decoded;
                check(after.getSound().equals(before.getSound()) && after.getPosition().equals(before.getPosition()), "play identity and position survive actual codec");
                check(after.getServerSoundHandle()==null && after.getLoopCount()==0, "play keeps named stoppable mode");
            } else {
                var after=(StopSoundPacket)decoded;var before=(StopSoundPacket)packet;
                check(after.getSoundName().equals(before.getSoundName()), "stop name survives actual codec");
                check(!after.isStoppingAllSound() && !after.isStopMusicLegacy(), "stop leaves ambient music and other sounds alone");
            }
        } finally {buffer.release();}
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
