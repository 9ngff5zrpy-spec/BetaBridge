/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.nostalgia.betabridge;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.viaversion.viaversion.api.connection.UserConnection;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import net.raphimc.vialegacy.protocol.beta.b1_6_0_6tob1_7_0_3.Protocolb1_6_0_6Tob1_7_0_3;

/** Modern controls -> original Beta rider input. The original server moves the hull. */
final class BetaBoatControls {
    private record State(int boat,int tick,Vector2f velocity){}
    private final Map<GeyserSession,State> states=new ConcurrentHashMap<>();
    void leave(GeyserSession session){states.remove(session);}
    void translate(GeyserSession session,PlayerAuthInputPacket packet,PacketTranslator<PlayerAuthInputPacket> original,UserConnection backend){
        var boat=(BoatEntity)session.getPlayerEntity().getVehicle();
        // A Beta backend has no modern MOVE_VEHICLE implementation. Its hull
        // is server-authoritative; do not replace its pose with a phone pose.
        boolean predicted=packet.getInputData().remove(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE);
        try{original.translate(session,packet);}finally{
            if(predicted)packet.getInputData().add(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE);
        }
        if(session.getPlayerEntity().getVehicle()!=boat)return;
        // This upstream switch also gates proxy VehicleComponent simulation.
        // Suppress that second simulator so relative/absolute Java moves apply.
        // The packet's native prediction flag was deliberately NOT forwarded.
        session.setInClientPredictedVehicle(true);
        session.getPlayerEntity().setPosition(boat.position());
        if(backend==null)return;
        int tick=session.getTicks();var previous=states.get(session);
        if(previous!=null&&previous.boat()==boat.javaId()&&previous.tick()==tick)return;
        var input=session.getPlayerEntity().getVehicleInput();float yaw=session.getPlayerEntity().getJavaYaw(),pitch=session.getPlayerEntity().getPitch();
        Vector2f old=previous!=null&&previous.boat()==boat.javaId()?previous.velocity():Vector2f.ZERO;
        var velocity=velocity(old,input,yaw);
        states.put(session,new State(boat.javaId(),tick,velocity));
        backend.getChannel().eventLoop().execute(()->{
            if(!backend.getChannel().isActive())return;
            try{BetaLegacyActions.riderMotion(backend,velocity.getX(),velocity.getY(),yaw,pitch)
                .sendToServer(Protocolb1_6_0_6Tob1_7_0_3.class,true);}
            catch(Exception e){session.getGeyser().getLogger().error("Beta boat rider input failed",e);}
        });
        if(previous==null||previous.boat()!=boat.javaId())session.getGeyser().getLogger().info("Beta boat: original rider motion enabled; modern vehicle packets suppressed; Java server owns hull motion.");
    }
    static Vector2f velocity(Vector2f old,Vector2f input,float yaw){
        float x=input.getX()*.98f,z=input.getY()*.98f;
        if(!Float.isFinite(x)||!Float.isFinite(z)||!Float.isFinite(yaw))return Vector2f.ZERO;
        float length=(float)Math.sqrt(x*x+z*z);if(length<1)length=1;
        float scale=.02f/length,sin=(float)Math.sin(yaw*(float)Math.PI/180f),cos=(float)Math.cos(yaw*(float)Math.PI/180f);
        // Original si.a + lo.a_ water acceleration/drag, used by the old rider.
        float vx=(old.getX()+(x*cos-z*sin)*scale)*.8f;
        float vz=(old.getY()+(z*cos+x*sin)*scale)*.8f;
        if(Math.abs(vx)<1e-5)vx=0;if(Math.abs(vz)<1e-5)vz=0;
        return Vector2f.from(vx,vz);
    }
}
