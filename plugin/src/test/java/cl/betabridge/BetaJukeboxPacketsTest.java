package cl.betabridge;

import io.netty.buffer.Unpooled;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.Deflater;

/** Decoder contract against packets serialized by the unchanged original Beta JAR. */
public final class BetaJukeboxPacketsTest {
    public static void main(String[] args) throws Exception {
        try (var original = new URLClassLoader(new java.net.URL[] {Path.of(args[0]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Object single = create(original, "lg", Map.of("a", -17, "b", 65, "c", 33, "d", 84, "e", 2));
            var changes = decode(serialize(original, single));
            check(changes.equals(List.of(new BetaJukeboxPackets.Change(-17,65,33,84,2))), "single update coordinates and Cat metadata");
            Object multi = create(original, "nc", Map.of("a", -2, "b", 3, "c", new short[] {(short)0x1241,(short)0xFE42},
                "d", new byte[] {84,84}, "e", new byte[] {1,0}, "f", 2));
            changes = decode(serialize(original,multi));
            check(changes.equals(List.of(new BetaJukeboxPackets.Change(-31,65,50,84,1),
                new BetaJukeboxPackets.Change(-17,66,62,84,0))), "multi update nibble positions and ejection metadata");
            byte[] blocks = new byte[16*128*16*5/2];
            int a = (2*16+3)*128+64, b=a+1;
            blocks[a]=84;blocks[b]=84;
            blocks[32768+a/2]=(byte)0x21;
            Deflater deflater=new Deflater();deflater.setInput(blocks);deflater.finish();
            byte[] encoded=new byte[blocks.length];int length=deflater.deflate(encoded);deflater.end();
            Object chunk=create(original,"cg",Map.of("a",16,"b",0,"c",-16,"d",16,"e",128,"f",16,
                "g",Arrays.copyOf(encoded,length),"h",length));
            changes=decode(serialize(original,chunk));
            check(changes.equals(List.of(new BetaJukeboxPackets.Change(18,64,-13,84,1),
                new BetaJukeboxPackets.Change(18,65,-13,84,2))), "full chunk metadata, both nibbles, correct axis order");
            check(decode(new byte[] {53,1,2}).isEmpty(), "truncated single update ignored");
            check(decode(new byte[] {52,0,0,0,0,0,0,0,0,(byte)255,(byte)255}).isEmpty(), "impossible multi count ignored");
            check(decode(new byte[] {0}).isEmpty(), "unrelated packets ignored");
        }
        System.out.println("BetaJukeboxPacketsTest passed: original JAR serializations for single/multi/chunk updates, Cat/13/eject metadata; buffers preserved; malformed bounds rejected.");
    }

    private static Object create(ClassLoader loader,String name,Map<String,Object> fields) throws Exception {
        Class<?> type=Class.forName(name,true,loader);Object packet=type.getConstructor().newInstance();
        for(var entry:fields.entrySet()) {var field=type.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(packet,entry.getValue());}
        return packet;
    }
    private static byte[] serialize(ClassLoader loader,Object packet) throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        Class<?> base=Class.forName("fy",true,loader);base.getMethod("a",base,DataOutputStream.class).invoke(null,packet,out);
        return bytes.toByteArray();
    }
    private static List<BetaJukeboxPackets.Change> decode(byte[] bytes) throws Exception {
        var buffer=Unpooled.buffer();buffer.writeByte(99);buffer.writeBytes(bytes);buffer.readerIndex(1);
        int reader=buffer.readerIndex(),writer=buffer.writerIndex();
        try {
            var changes=BetaJukeboxPackets.read(buffer);
            check(buffer.readerIndex()==reader && buffer.writerIndex()==writer,"observer never consumes or rewrites a packet");
            byte[] after=new byte[bytes.length];buffer.getBytes(reader,after);
            check(Arrays.equals(bytes,after),"original ViaLegacy bytes remain intact");return changes;
        } finally {buffer.release();}
    }
    private static void check(boolean value,String message) {if(!value) throw new AssertionError(message);}
}
