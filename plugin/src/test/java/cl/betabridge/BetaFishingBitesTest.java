package cl.betabridge;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.packet.*;
public final class BetaFishingBitesTest {
 public static void main(String[] args)throws Exception{
  var actualUse=Unpooled.wrappedBuffer(java.util.HexFormat.of().parseHex("0fffffffffffffffffffffffff"));
  try{check(BetaFishingBites.rodUse(actualUse),"observed real ViaVersion 13-byte null-witness cast/reel recognised");check(actualUse.readerIndex()==0,"diagnostic does not consume rod use");}finally{actualUse.release();}
  try(var loader=new URLClassLoader(new java.net.URL[]{Path.of(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
   Class<?> base=loader.loadClass("gq"),event=loader.loadClass("dq");
   Object marker=event.getConstructor(int.class,int.class,int.class,int.class,int.class).newInstance(BetaFishingBites.EVENT,-25,69,-32,123);
   var output=new ByteArrayOutputStream();base.getMethod("a",base,DataOutputStream.class).invoke(null,marker,new DataOutputStream(output));
   byte[] original=output.toByteArray();check(original.length==18,"original Beta event serialization size");
   var bytes=Unpooled.buffer();bytes.writeByte(99);bytes.writeBytes(original);bytes.readerIndex(1);
   try{
    var bite=BetaFishingBites.read(bytes);check(bite.equals(new BetaFishingBites.Bite(123,-25,69,-32)),"original marker data/negative coordinates");
    check(bytes.readerIndex()==1&&bytes.writerIndex()==19,"observer leaves buffer indices untouched");
    for(int length=0;length<18;length++){var truncated=Unpooled.wrappedBuffer(Arrays.copyOf(original,length));try{check(BetaFishingBites.read(truncated)==null,"truncated notification ignored");}finally{truncated.release();}}
    bytes.setInt(2,1005);check(BetaFishingBites.read(bytes)==null,"jukebox untouched");bytes.setInt(2,BetaFishingBites.EVENT);
    bytes.writeByte(0);check(BetaFishingBites.read(bytes)==null,"coalesced/unexpected bytes not consumed");
   }finally{bytes.release();}
  }
  List<BedrockPacket> packets=new ArrayList<>();var p=Vector3f.from(-25,69,-32);BetaFishingBites.packets(p,packets::add);
  check(packets.size()==5&&packets.get(0) instanceof PlaySoundPacket,"one sound and four splash particles");
  var codec=Bedrock_v2193.CODEC;var helper=codec.createHelper();
  for(var packet:packets){var bytes=Unpooled.buffer();try{
   check(!(packet instanceof SpawnParticleEffectPacket),"regression: never send crash-triggering string/Molang particle payload");
   if(packet instanceof LevelEventPacket effect){
    check(effect.getType()==org.cloudburstmc.protocol.bedrock.data.ParticleType.WATER_SPLASH&&effect.getData()==0,"built-in splash level event, no custom effect parser");
    check(effect.getPosition().distanceSquared(p)<.1,"splash remains at server-confirmed hook");
   }
   codec.tryEncode(helper,bytes,packet);var decoded=codec.tryDecode(helper,bytes,codec.getPacketDefinition(packet.getClass()).getId());
   check(!bytes.isReadable()&&decoded.equals(packet),"actual Bedrock 2193 sound/particle roundtrip");
  }finally{bytes.release();}}
  var sound=(PlaySoundPacket)packets.get(0);check(sound.getSound().equals("random.splash")&&sound.getVolume()==.25f&&sound.getPosition().equals(p),"Beta splash sound at actual hook");
  System.out.println("PASS: original Beta event marker parsing, malformed/unrelated guards, preserved buffers; original splash sound + four built-in level-event particles on actual Bedrock 2193 wire; no string particle/Molang payload. No fish grant or local bite timer. Client stability still needs phone verification.");
 }
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
