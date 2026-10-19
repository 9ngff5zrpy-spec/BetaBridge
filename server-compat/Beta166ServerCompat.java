/* SPDX-License-Identifier: GPL-3.0-or-later */
import java.lang.instrument.*;
import java.security.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Packet compatibility and configured spawn protection; original world mechanics stay authoritative. */
public final class Beta166ServerCompat implements ClassFileTransformer {
    static final String SERVER="af36c9c708ec9b322378acb319b0da8a1d76ac2185c595a077d0c069835b8916";
    static final Map<String,String> HASHES=new HashMap<>();
    final int radius;
    Beta166ServerCompat(int radius){this.radius=radius;}
    static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    public static void premain(String args, Instrumentation instrumentation)throws Exception {
        Path jar=Path.of(System.getProperty("java.class.path")).toRealPath();
        if(!hash(Files.readAllBytes(jar)).equals(SERVER))throw new IllegalStateException("Unrecognized original Beta server");
        Properties properties=new Properties();try(var in=Files.newInputStream(Path.of("server.properties"))){properties.load(in);}
        int configured=Integer.parseInt(properties.getProperty("spawn-protection","16"));
        if(configured<0 || configured>256)throw new IllegalArgumentException("Invalid spawn-protection");
        Beta166ServerCompat adapter=new Beta166ServerCompat(configured==0?-1:configured);
        try(ZipFile zip=new ZipFile(jar.toFile())){
            for(String name:List.of("gx","dn","hn")){
                byte[] original=zip.getInputStream(zip.getEntry(name+".class")).readAllBytes();
                HASHES.put(name,hash(original));adapter.patch(name,original); // Verify every site before enabling.
            }
        }
        instrumentation.addTransformer(adapter);
        System.out.println("Beta166ServerCompat: spawn-protection="+configured+"; 3 original guards validated; authoritative held-slot response after each use; original fishing bite notification; operator permissions unchanged.");
    }
    byte[] patch(String name,byte[] bytes)throws Exception {
        if(!hash(bytes).equals(HASHES.get(name)))throw new IllegalStateException("Unexpected "+name+" bytes");
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int changed=0,slotSites=0,biteSites=0;
        for(MethodNode method:node.methods){
            if(name.equals("hn")&&method.name.equals("o_")&&method.desc.equals("()V")){
                // Notify immediately AFTER the original splash call, when k was
                // already assigned. Do not touch RNG, catch duration or drops.
                for(AbstractInsnNode insn:method.instructions.toArray()){
                    if(insn instanceof MethodInsnNode invoke&&invoke.getOpcode()==Opcodes.INVOKEVIRTUAL
                            &&invoke.owner.equals("dh")&&invoke.name.equals("a")&&invoke.desc.equals("(Llm;Ljava/lang/String;FF)V")){
                        InsnList signal=new InsnList();signal.add(new VarInsnNode(Opcodes.ALOAD,0));
                        signal.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"Beta166ServerCompat","fishingBite","(Ljava/lang/Object;)V",false));
                        method.instructions.insert(insn,signal);biteSites++;
                    }
                }
            }
            boolean target=method.name.equals("a") && (name.equals("gx") ?
                method.desc.equals("(Lgj;)V")||method.desc.equals("(Lem;)V") : method.desc.equals("(Lek;III)Z"));
            if(!target)continue;
            int sites=0;
            for(AbstractInsnNode insn:method.instructions.toArray()){
                if(!(insn instanceof IntInsnNode constant) || constant.getOpcode()!=Opcodes.BIPUSH || constant.operand!=16)continue;
                AbstractInsnNode next=insn.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
                if(next==null || (next.getOpcode()!=Opcodes.IF_ICMPGT && next.getOpcode()!=Opcodes.IF_ICMPLE))continue;
                method.instructions.set(insn,new LdcInsnNode(radius));sites++;changed++;
            }
            if(sites!=1)throw new IllegalStateException("Unexpected spawn guard count in "+name+method.desc+": "+sites);
            if(name.equals("gx")&&method.desc.equals("(Lem;)V")){
                for(AbstractInsnNode insn:method.instructions.toArray()){
                    if(insn instanceof MethodInsnNode invoke&&invoke.getOpcode()==Opcodes.INVOKESTATIC
                            &&invoke.owner.equals("fw")&&invoke.name.equals("a")&&invoke.desc.equals("(Lfw;Lfw;)Z")){
                        // Beta's Java client predicts consumption. Modern Geyser
                        // needs the final server stack even when its witness matches.
                        // Keep the original serialization, including a null last stack.
                        InsnList forceResponse=new InsnList();forceResponse.add(new InsnNode(Opcodes.POP));
                        forceResponse.add(new InsnNode(Opcodes.ICONST_0));method.instructions.insert(insn,forceResponse);slotSites++;
                    }
                }
            }
        }
        if(changed!=(name.equals("gx")?2:name.equals("dn")?1:0))throw new IllegalStateException("Missing original spawn guards");
        if(slotSites!=(name.equals("gx")?1:0))throw new IllegalStateException("Missing original held-slot confirmation site");
        if(biteSites!=(name.equals("hn")?1:0))throw new IllegalStateException("Missing unique original fishing bite splash site");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static volatile boolean fishingWarning;
    /** Original packet 61; unknown level-event IDs are ignored by the Beta client. */
    public static void fishingBite(Object hook){
        try{
            Class<?> type=hook.getClass();if(!type.getName().equals("hn"))return;
            var timer=type.getDeclaredField("k");timer.setAccessible(true);if(timer.getInt(hook)<=0)return;
            if(type.getField("b").get(hook)==null)return;
            Object world=type.getField("aL").get(hook);if(world==null)return;
            int x=(int)Math.floor(type.getField("aP").getDouble(hook));
            int y=(int)Math.floor(type.getField("aQ").getDouble(hook));
            int z=(int)Math.floor(type.getField("aR").getDouble(hook));
            if(y<0||y>=128)return;
            Class<?> player=Class.forName("ek",false,type.getClassLoader());
            // dk broadcasts this original event packet within 64 blocks. The
            // proxy consumes our marker only on authenticated Bedrock sessions.
            world.getClass().getMethod("a",player,int.class,int.class,int.class,int.class,int.class)
                .invoke(world,null,0x4246,x,y,z,type.getField("aG").getInt(hook));
        }catch(ReflectiveOperationException|RuntimeException e){
            if(!fishingWarning){fishingWarning=true;System.err.println("Beta fishing notification unavailable: "+e.getClass().getSimpleName());}
        }
    }
    @Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){
        if(!HASHES.containsKey(name))return null;
        try{return patch(name,bytes);}catch(Exception e){throw new IllegalStateException("Beta spawn compatibility failed",e);}
    }
}
