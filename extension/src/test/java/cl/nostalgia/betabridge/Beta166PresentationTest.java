package cl.nostalgia.betabridge;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
public final class Beta166PresentationTest {
 public static void main(String[] args){
  int count=0;
  for(int x:new int[]{-100000,-105,-1,0,1,105,100000})for(int z:new int[]{-100000,-6,0,6,100000})for(int direction=0;direction<4;direction++){
   float ox=.5f,oz=.5f;if(direction==0)oz=.9f;if(direction==1)ox=.1f;if(direction==2)oz=.1f;if(direction==3)ox=.9f;
   Vector3i head=Vector3i.from(x,65,z);Vector3d position=Vector3d.from((double)((float)x+ox),65.9375,(double)((float)z+oz));
   if(!Beta166Presentation.betaBedTeleport(head,position))throw new AssertionError("original float sleep offset");
   if(Beta166Presentation.betaBedTeleport(head,position.add(0,1,0)))throw new AssertionError("unrelated teleport");count++;
  }
  if(Beta166Presentation.betaBedTeleport(Vector3i.ZERO,Vector3d.from(.5,.6875,.5)))throw new AssertionError("modern centre is upstream responsibility");
  if(Beta166Presentation.disc("minecraft:music_disc.13")!=1||Beta166Presentation.disc("minecraft:music_disc.cat")!=2||Beta166Presentation.disc("minecraft:music_disc.pigstep")!=0)throw new AssertionError("only Beta C418 records");
  System.out.println("PASS: "+count+" real Beta bed offsets, far-coordinate rounding, unrelated/modern teleport guards and C418 disc identity.");
 }
}
