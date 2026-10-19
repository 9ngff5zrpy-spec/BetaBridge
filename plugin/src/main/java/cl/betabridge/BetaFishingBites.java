/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.entity.type.FishingHookEntity;
import org.geysermc.geyser.session.GeyserSession;

/** A server-confirmed bite, never a timer or a fish grant. */
final class BetaFishingBites {
    static final int EVENT=0x4246;
    record Bite(int hook,int x,int y,int z){}
    static boolean rodUse(ByteBuf bytes){
        int n=bytes.readableBytes(),at=bytes.readerIndex();
        if((n!=13&&n!=16)||bytes.getUnsignedByte(at)!=15||bytes.getUnsignedByte(at+10)!=255)return false;
        int witness=bytes.getShort(at+11);
        // The real ViaVersion route sends a null item witness, because Beta
        // takes the rod from its authoritative selected slot. Diagnostics must
        // not mistake that valid 13-byte use for a missing cast/reel.
        return n==13?witness==-1:witness==346;
    }
    static Bite read(ByteBuf bytes){
        int at=bytes.readerIndex();
        // Packet 61: byte ID, int event, int X, byte Y, int Z, int data.
        if(bytes.readableBytes()!=18||bytes.getUnsignedByte(at)!=61||bytes.getInt(at+1)!=EVENT)return null;
        int y=bytes.getByte(at+9),hook=bytes.getInt(at+14);
        if(y<0||hook<0)return null;
        return new Bite(hook,bytes.getInt(at+5),y,bytes.getInt(at+10));
    }
    static void present(GeyserSession session,Bite bite){
        if(session.isClosed())return;
        var entity=session.getEntityCache().getEntityByJavaId(bite.hook());
        // Signal only the owner of this actual, still-live hook. A delayed
        // packet for an old/retrieved hook cannot create a false new bite.
        if(!(entity instanceof FishingHookEntity hook)||session.getFishingHook()!=hook)return;
        var p=hook.bedrockPosition();
        if(p.distanceSquared(Vector3f.from(bite.x()+.5f,bite.y()+.5f,bite.z()+.5f))>16)return;
        packets(p,session::sendUpstreamPacket);
        session.getGeyser().getLogger().info("Beta fishing: original-server bite signalled for own live hook "+bite.hook());
    }
    static void packets(Vector3f position,Consumer<BedrockPacket> sink){
        var sound=new PlaySoundPacket();sound.setSound("random.splash");sound.setPosition(position);
        sound.setVolume(.25f);sound.setPitch(1);sink.accept(sound);
        // Use the built-in level-event particle route, as Geyser does for
        // vanilla Java particles. A plain object of dotted Molang numbers is
        // NOT the packet's typed Molang schema; sending it crashed the iPhone
        // on the actual bite. No string effect or Molang payload is needed.
        for(float[] d:new float[][]{{-.15f,0},{.15f,0},{0,-.15f},{0,.15f}}){
            var splash=new LevelEventPacket();splash.setType(ParticleType.WATER_SPLASH);
            splash.setPosition(position.add(d[0],.05f,d[1]));splash.setData(0);
            sink.accept(splash);
        }
    }
}
