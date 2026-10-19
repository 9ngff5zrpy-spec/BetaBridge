package cl.nostalgia.betabridge;
import java.net.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
/** Compare cube faces against the unmodified Beta client's neighbour-aware chest method. */
public final class BetaChestBlockTest {
 public static void main(String[] args)throws Exception{
  try(var loader=new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
   Class<?> block=loader.loadClass("un"),world=loader.loadClass("xg");Object chest=block.getField("av").get(null);
   var original=chest.getClass().getMethod("a",world,int.class,int.class,int.class,int.class);
   var vectors=Map.of("north",new int[]{0,-1},"south",new int[]{0,1},"west",new int[]{-1,0},"east",new int[]{1,0});
   String[] faces={"down","up","north","south","west","east"};int count=0;
   for(String facing:vectors.keySet())for(String type:new String[]{"single","left","right"}){
    int[] f=vectors.get(facing);Map<String,Integer> blocks=new HashMap<>();blocks.put("0,0",54);
    int side=type.equals("left")?1:-1,px=-f[1]*side,pz=f[0]*side;
    if(!type.equals("single"))blocks.put(px+","+pz,54);
    // A solid row behind the chest makes original Beta select this facing.
    blocks.put((-f[0])+","+(-f[1]),1);
    if(!type.equals("single"))blocks.put((px-f[0])+","+(pz-f[1]),1);
    Object region=java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{world},(o,m,a)->{
     if(m.getName().equals("a")&&m.getParameterCount()==3)return blocks.getOrDefault(a[0]+","+a[2],0);
     throw new AssertionError("Unexpected original world query: "+m);
    });
    for(int face=0;face<6;face++){
     int expected=(int)original.invoke(chest,region,0,64,0,face),actual=BetaChestBlock.texture(facing,type,faces[face]);
     if(expected!=actual)throw new AssertionError(facing+" "+type+" "+faces[face]+": "+expected+" != "+actual);
     count++;
    }
   }
   System.out.println("PASS: "+count+" chest faces agree with original Beta e.a, all orientations and single/left/right joins.");
  }
 }
}
