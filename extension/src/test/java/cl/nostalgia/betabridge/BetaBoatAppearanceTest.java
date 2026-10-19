package cl.nostalgia.betabridge;

import io.netty.buffer.Unpooled;
import java.util.*;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.data.entity.*;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.entity.*;
import org.geysermc.geyser.entity.properties.GeyserEntityProperties;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.util.EntityUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

/** Real pinned BoatEntity and actual Bedrock wire, with no phone/session simulation. */
public final class BetaBoatAppearanceTest {
 public static void main(String[] args)throws Exception{
  if(!org.geysermc.api.Geyser.isRegistered())throw new IllegalStateException("Run with BoatActorAuditAgent inside initialized Geyser; no fake API bootstrap");
  var custom=new CustomBedrockEntityDefinition(new TestIdentifier("betabridge","boat"),new GeyserEntityProperties());
  var type=new EntityTypeDefinition<BoatEntity>(c->new BoatEntity(c,BoatEntity.BoatVariant.OAK),
      new GeyserEntityType(new TestIdentifier("minecraft","oak_boat"),EntityType.OAK_BOAT),1.6f,.6f,.375f,custom,List.of()){
   public boolean is(EntityType t){return t==EntityType.OAK_BOAT;}
  };
  var position=Vector3f.from(-26,69,-32);
  var context=new EntitySpawnContext(null,type,123,UUID.randomUUID(),custom,position,Vector3f.ZERO,0,0,0,987L);
  var boat=new BoatEntity(context,BoatEntity.BoatVariant.OAK);
  BetaBoatAppearance.configure(boat);
  check(boat.javaId()==123&&boat.geyserId()==987,"original IDs retained for actions/links");
  check(boat.definition().identifier().toString().equals("betabridge:boat"),"custom actor is selected, not a native boat renderer");
  check(boat.getOffset()==0&&boat.bedrockPosition().equals(position),"generic actor anchor is feet");
  check(boat.getBoundingBoxWidth()==1.5f&&boat.getBoundingBoxHeight()==.6f,"original collision dimensions");
  check(boat.getVehicleComponent().getBoundingBox().getSizeX()==1.5,"proxy collision also uses original width");
  check(boat.getFlag(EntityFlag.HAS_GRAVITY)&&boat.getFlag(EntityFlag.COLLIDABLE),"gravity retained alongside solid collision");
  var gravity=org.geysermc.geyser.entity.vehicle.VehicleComponent.class.getDeclaredMethod("getGravity");gravity.setAccessible(true);
  boat.setFlag(EntityFlag.HAS_GRAVITY,false);
  check((double)gravity.invoke(boat.getVehicleComponent())==0,"reproduce custom-actor flag disabling proxy gravity");
  BetaBoatAppearance.configure(boat);
  check((double)gravity.invoke(boat.getVehicleComponent())==.04,"custom fallback no longer suppresses proxy gravity");
  var nativeType=VanillaEntities.OAK_BOAT;
  var nativeBoat=new BoatEntity(new EntitySpawnContext(null,nativeType,125,UUID.randomUUID(),
      nativeType.defaultBedrockDefinition(),position,Vector3f.ZERO,0,0,0,989L),BoatEntity.BoatVariant.OAK);
  check(nativeBoat.definition().identifier().toString().equals("minecraft:boat")&&!BetaBoatAppearance.custom(nativeBoat),"actual native boat definition, not generic actor");
  check(nativeBoat.getOffset()==.375f&&nativeBoat.getBoundingBoxWidth()==1.6f,"native origin/dimensions untouched");
  check(nativeBoat.getFlag(EntityFlag.HAS_GRAVITY)&&(double)gravity.invoke(nativeBoat.getVehicleComponent())==.04,"native proxy gravity enabled");
  var nativeMetadata=new EntityDataMap();nativeBoat.getMetadata().apply(nativeMetadata);
  check(nativeMetadata.get(EntityDataTypes.IS_BUOYANT),"original native boat buoyancy retained");
  BetaBoatAppearance.serverControlled(nativeBoat);nativeBoat.getMetadata().apply(nativeMetadata);
  check(nativeMetadata.get(EntityDataTypes.CONTROLLING_RIDER_SEAT_INDEX)==(byte)-1,"local native steering disabled for original-server movement");
  check(.004280189-(double)gravity.invoke(nativeBoat.getVehicleComponent())<0,"observed upward velocity cannot persist in air with native gravity");
  // Exercise the upstream seat conversion on a real, unspawned Geyser entity.
  // No fake phone or session: metadata is inspected before anything is sent.
  var passenger=new Entity(new EntitySpawnContext(null,VanillaEntities.PLAYER,124,UUID.randomUUID(),
      VanillaEntities.PLAYER.defaultBedrockDefinition(),position,Vector3f.ZERO,0,0,0,988L));
  EntityUtils.updateMountOffset(passenger,boat,true,true,0,1);
  var upstream=new EntityDataMap();passenger.getMetadata().apply(upstream);
  var upstreamSeat=upstream.get(EntityDataTypes.SEAT_OFFSET);
  check(Math.abs(upstreamSeat.getY()-(VanillaEntities.PLAYER_ENTITY_OFFSET-.75f))<1e-6,"reproduce native modern seat height on generic actor; actual="+upstreamSeat);
  for(float[] overwritten:new float[][]{{1.375f,.5625f},{1.6f,.6f}}){
   boat.setBoundingBoxWidth(overwritten[0]);boat.setBoundingBoxHeight(overwritten[1]);
   BetaBoatAppearance.configure(boat);BetaBoatAppearance.seat(passenger);
   check(boat.getBoundingBoxWidth()==1.5f&&boat.getBoundingBoxHeight()==.6f,"repair mount and dismount dimension resets");
   var repaired=new EntityDataMap();passenger.getMetadata().apply(repaired);
   var seat=repaired.get(EntityDataTypes.SEAT_OFFSET);
   check(Math.abs(seat.getY()-(VanillaEntities.PLAYER_ENTITY_OFFSET+.0125f))<1e-6&&seat.getZ()==-.4f&&seat.getX()==0,"single Beta seat at original floor/forward offset");
  }
  var packet=new AddEntityPacket();packet.setIdentifier(boat.definition().identifier().toString());
  packet.setRuntimeEntityId(boat.geyserId());packet.setUniqueEntityId(boat.geyserId());packet.setPosition(boat.bedrockPosition());
  packet.setMotion(Vector3f.ZERO);packet.setRotation(org.cloudburstmc.math.vector.Vector2f.ZERO);
  boat.getMetadata().apply(packet.getMetadata());
  var codec=Bedrock_v2193.CODEC;var helper=codec.createHelper();var bytes=Unpooled.buffer();
  try{
   codec.tryEncode(helper,bytes,packet);var decoded=(AddEntityPacket)codec.tryDecode(helper,bytes,codec.getPacketDefinition(AddEntityPacket.class).getId());
   check(!bytes.isReadable()&&decoded.getIdentifier().equals("betabridge:boat"),"custom identifier survives wire");
   check(decoded.getMetadata().get(EntityDataTypes.WIDTH)==1.5f&&decoded.getMetadata().get(EntityDataTypes.HEIGHT)==.6f,"real configured metadata survives wire");
   check(!decoded.getMetadata().get(EntityDataTypes.IS_BUOYANT),"phone does not independently float the actor");
  }finally{bytes.release();}
  System.out.println("PASS: native ID/origin/buoyancy/gravity unchanged; absent-gravity regression reproduced and guarded; preserved custom hull dimensions/seat and real Bedrock codec checked independently of selected renderer. Visuals/mount/dismount require iPhone testing.");
 }
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 record TestIdentifier(String namespace,String path) implements Identifier {
  public String toString(){return namespace+":"+path;}
 }
}
