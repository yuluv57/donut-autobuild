import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Members dump JAR PREFIX           -> prints "C class", "F class name desc", "M class name desc" for classes under PREFIX
 * Members link JAR PREFIX           -> checks every field/method reference whose owner starts with PREFIX resolves inside JAR
 * Members calls JAR CLASS METHOD    -> prints invoked owner.name desc inside methods named METHOD of CLASS
 */
public class Members {
    static Map<String, ClassNode> load(String jar) throws IOException {
        Map<String, ClassNode> m = new TreeMap<>();
        try (JarFile j = new JarFile(jar)) {
            for (JarEntry e : Collections.list(j.entries())) {
                if (!e.getName().endsWith(".class")) continue;
                ClassNode c = new ClassNode();
                new ClassReader(j.getInputStream(e).readAllBytes()).accept(c, ClassReader.SKIP_FRAMES);
                m.put(c.name, c);
            }
        }
        return m;
    }

    static boolean hasMethod(Map<String, ClassNode> all, String owner, String name, String desc) {
        for (String o = owner; o != null; ) {
            ClassNode c = all.get(o);
            if (c == null) return true; // leaves the jar (MC/java) -> not our business
            for (MethodNode mn : c.methods) if (mn.name.equals(name) && mn.desc.equals(desc)) return true;
            for (String itf : c.interfaces) if (all.containsKey(itf) && hasMethod(all, itf, name, desc)) return true;
            o = c.superName;
        }
        return false;
    }

    static boolean hasField(Map<String, ClassNode> all, String owner, String name, String desc) {
        for (String o = owner; o != null; ) {
            ClassNode c = all.get(o);
            if (c == null) return true;
            for (FieldNode f : c.fields) if (f.name.equals(name) && f.desc.equals(desc)) return true;
            o = c.superName;
        }
        return false;
    }

    public static void main(String[] a) throws Exception {
        Map<String, ClassNode> all = load(a[1]);
        String mode = a[0];
        if (mode.equals("dump")) {
            for (ClassNode c : all.values()) {
                if (!c.name.startsWith(a[2])) continue;
                System.out.println("C " + c.name);
                for (FieldNode f : c.fields) System.out.println("F " + c.name + " " + f.name + " " + f.desc);
                for (MethodNode mn : c.methods) System.out.println("M " + c.name + " " + mn.name + " " + mn.desc);
            }
        } else if (mode.equals("link")) {
            int bad = 0;
            for (ClassNode c : all.values()) {
                for (MethodNode mn : c.methods) {
                    for (AbstractInsnNode n : mn.instructions.toArray()) {
                        if (n instanceof MethodInsnNode mi && mi.owner.startsWith(a[2]) && !mi.owner.startsWith("[")) {
                            if (!all.containsKey(mi.owner)) { System.out.println("MISSING CLASS " + mi.owner + " from " + c.name + "." + mn.name); bad++; }
                            else if (!hasMethod(all, mi.owner, mi.name, mi.desc)) { System.out.println("MISSING METHOD " + mi.owner + "." + mi.name + mi.desc + " from " + c.name + "." + mn.name); bad++; }
                        } else if (n instanceof FieldInsnNode fi && fi.owner.startsWith(a[2])) {
                            if (!all.containsKey(fi.owner)) { System.out.println("MISSING CLASS " + fi.owner + " from " + c.name + "." + mn.name); bad++; }
                            else if (!hasField(all, fi.owner, fi.name, fi.desc)) { System.out.println("MISSING FIELD " + fi.owner + "." + fi.name + " " + fi.desc + " from " + c.name + "." + mn.name); bad++; }
                        } else if (n instanceof TypeInsnNode ti && ti.desc.startsWith(a[2]) && !all.containsKey(ti.desc)) {
                            System.out.println("MISSING CLASS " + ti.desc + " from " + c.name + "." + mn.name); bad++;
                        }
                    }
                }
            }
            System.out.println("LINK PROBLEMS: " + bad);
        } else if (mode.equals("calls")) {
            ClassNode c = all.get(a[2]);
            for (MethodNode mn : c.methods) {
                if (!mn.name.equals(a[3])) continue;
                System.out.println("== " + mn.name + mn.desc);
                for (AbstractInsnNode n : mn.instructions.toArray()) {
                    if (n instanceof MethodInsnNode mi) System.out.println("  " + mi.owner + "." + mi.name + mi.desc);
                    else if (n instanceof FieldInsnNode fi) System.out.println("  F " + fi.owner + "." + fi.name);
                    else if (n instanceof LdcInsnNode l && l.cst instanceof String s) System.out.println("  \"" + s + "\"");
                }
            }
        }
    }
}
