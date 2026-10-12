import org.objectweb.asm.*; import org.objectweb.asm.commons.*;
import java.io.*; import java.nio.file.*; import java.util.*; import java.util.zip.*;

public class Remap {
  static Map<String,String> C=new HashMap<>(), M=new HashMap<>(), F=new HashMap<>();
  static Map<String,String> MO=new HashMap<>(), FO=new HashMap<>(); // owner-aware: owner\0name\0desc
  static boolean rev;
  public static void main(String[] a) throws Exception {
    // a: mapfile inJar outJar
    for (String l : Files.readAllLines(Paths.get(a[0]))) {
      String[] p = l.split("\t",-1);
      switch(p[0]){
        case "C": C.put(p[1],p[2]); break;
        case "M": M.put(p[1],p[2]); break;
        case "F": F.put(p[1],p[2]); break;
        case "MO": MO.put(p[1]+"\0"+p[2]+"\0"+p[3], p[4]); break;
        case "FO": FO.put(p[1]+"\0"+p[2]+"\0"+p[3], p[4]); break;
      }
    }
    Remapper r = new Remapper() {
      public String map(String t){ 
        String v=C.get(t); if(v!=null) return v;
        int i=t.indexOf('$');
        if(i>0){ String o=C.get(t.substring(0,i)); if(o!=null) return o+t.substring(i); }
        return t;
      }
      public String mapMethodName(String owner,String name,String desc){
        String v=MO.get(owner+"\0"+name+"\0"+desc); if(v!=null) return v;
        v=M.get(name); return v!=null?v:name;
      }
      public String mapInvokeDynamicMethodName(String name,String desc){
        String v=M.get(name); return v!=null?v:name;
      }
      public String mapFieldName(String owner,String name,String desc){
        String v=FO.get(owner+"\0"+name+"\0"+desc); if(v!=null) return v;
        v=F.get(name); return v!=null?v:name;
      }
      public String mapRecordComponentName(String owner,String name,String desc){ return mapFieldName(owner,name,desc); }
    };
    try (ZipFile in=new ZipFile(a[1]); ZipOutputStream out=new ZipOutputStream(new FileOutputStream(a[2]))) {
      Enumeration<? extends ZipEntry> e=in.entries();
      while(e.hasMoreElements()){
        ZipEntry en=e.nextElement();
        if(en.isDirectory()) continue;
        byte[] data = in.getInputStream(en).readAllBytes();
        String name=en.getName();
        if(name.endsWith(".class")){
          ClassReader cr=new ClassReader(data);
          ClassWriter cw=new ClassWriter(0);
          cr.accept(new ClassRemapper(cw,r), 0);
          data=cw.toByteArray();
          name=r.map(name.substring(0,name.length()-6))+".class";
        }
        out.putNextEntry(new ZipEntry(name));
        out.write(data); out.closeEntry();
      }
    }
    System.out.println("remapped -> "+a[2]);
  }
}
