import org.objectweb.asm.*;
import java.io.*; import java.util.*; import java.util.zip.*;

public class Extract {
  static PrintWriter out;
  public static void main(String[] a) throws Exception {
    out = new PrintWriter(new BufferedWriter(new FileWriter(a[1])));
    ZipFile z = new ZipFile(a[0]);
    String pref = a.length>2 ? a[2] : "com/donutautobuild/";
    Enumeration<? extends ZipEntry> e = z.entries();
    while (e.hasMoreElements()) {
      ZipEntry en = e.nextElement();
      if (!en.getName().endsWith(".class")) continue;
      if (!en.getName().startsWith(pref)) continue;
      new ClassReader(z.getInputStream(en)).accept(new CV(), ClassReader.SKIP_FRAMES);
    }
    out.close();
  }
  static void p(Object... o){ StringBuilder s=new StringBuilder(); for(int i=0;i<o.length;i++){ if(i>0)s.append('\t'); s.append(o[i]); } out.println(s); }
  static class CV extends ClassVisitor {
    String cur;
    CV(){ super(Opcodes.ASM9); }
    public void visit(int v,int acc,String name,String sig,String sup,String[] ifs){
      cur=name; p("C",name,acc,sup,ifs==null?"":String.join(",",ifs),sig==null?"":sig);
    }
    public FieldVisitor visitField(int acc,String n,String d,String sig,Object val){ p("DF",cur,n,d,acc,sig==null?"":sig); return null; }
    public MethodVisitor visitMethod(int acc,String n,String d,String sig,String[] ex){
      p("DM",cur,n,d,acc,sig==null?"":sig,ex==null?"":String.join(",",ex));
      return new MV(cur+"#"+n);
    }
  }
  static class MV extends MethodVisitor {
    String ctx;
    MV(String o){ super(Opcodes.ASM9); ctx=o; }
    public void visitMethodInsn(int op,String o,String n,String d,boolean itf){ p("M",op,o,n,d,itf,ctx); }
    public void visitFieldInsn(int op,String o,String n,String d){ p("F",op,o,n,d,ctx); }
    public void visitTypeInsn(int op,String t){ p("T",op,t); }
    public void visitTryCatchBlock(Label s,Label e,Label h,String t){ if(t!=null) p("T",0,t); }
    public void visitInvokeDynamicInsn(String n,String d,Handle bsm,Object... args){
      p("D",n,d,args.length>0?args[0]:"",args.length>1?args[1]:"",args.length>2?args[2]:"");
    }
    public void visitLdcInsn(Object c){ if(c instanceof Type) p("L",c); }
  }
}
