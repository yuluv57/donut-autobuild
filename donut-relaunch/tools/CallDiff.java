import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class CallDiff {
    static Map<String, List<String>> calls(String jar, String cls) throws Exception {
        Map<String, List<String>> r = new TreeMap<>();
        try (JarFile j = new JarFile(jar)) {
            ClassNode c = new ClassNode();
            new ClassReader(j.getInputStream(j.getJarEntry(cls + ".class")).readAllBytes()).accept(c, 0);
            for (MethodNode m : c.methods) {
                if (m.name.startsWith("lambda$")) continue;
                List<String> l = new ArrayList<>();
                for (AbstractInsnNode n : m.instructions.toArray()) {
                    if (n instanceof MethodInsnNode mi && !mi.name.startsWith("lambda$") && !mi.owner.equals("java/lang/StringBuilder")) l.add(mi.owner.replaceAll(".*/", "") + "." + mi.name);
                    else if (n instanceof LdcInsnNode ld && ld.cst instanceof String s) l.add("\"" + s.replace("\u0001", "") + "\"");
                    else if (n instanceof InvokeDynamicInsnNode id && id.name.equals("makeConcatWithConstants")) l.add("\"" + String.valueOf(id.bsmArgs[0]).replace("\u0001", "") + "\"");
                }
                Collections.sort(l);
                r.put(m.name + m.desc, l);
            }
        }
        return r;
    }
    public static void main(String[] a) throws Exception {
        Map<String, List<String>> x = calls(a[0], a[2]), y = calls(a[1], a[2]);
        int diff = 0;
        for (String k : x.keySet()) {
            List<String> p = x.get(k), q = y.get(k);
            if (q == null) { System.out.println("MISSING " + k); diff++; continue; }
            if (!p.equals(q)) {
                diff++;
                List<String> onlyA = new ArrayList<>(p), onlyB = new ArrayList<>(q);
                for (String s : q) onlyA.remove(s);
                for (String s : p) onlyB.remove(s);
                System.out.println("DIFF " + k + "\n   only120: " + onlyA + "\n   onlyNew: " + onlyB);
            }
        }
        System.out.println("methods differing: " + diff + " / " + x.size());
    }
}
