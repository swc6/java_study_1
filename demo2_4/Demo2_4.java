import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Demo 2.4 — 字节码与 Instrumentation 实战演示
 *
 * 涵盖内容：
 *   2.4.1 .class 文件结构解析（魔数/版本/常量池/字段/方法/Code）
 *   2.4.2 字节码指令概览（指令分类 + javap 用法）
 *   2.4.3 Instrumentation / Java Agent 原理（premain/agentmain/ClassFileTransformer）
 *
 * 编译：javac Demo2_4.java
 * 运行：java Demo2_4
 */
public class Demo2_4 {

    // ============================================================
    // 演示1：.class 文件结构解析 —— 解析自身的 Demo2_4.class
    // ============================================================
    static class ClassFileParser {

        private DataInputStream in;
        private int[] tag;       // 常量池 tag
        private String[] utf8;   // Utf8 条目原文
        private int[] r1, r2;    // 引用类条目的索引1/2（或 Integer/Float 原始值用 r1 存）
        private long[] lval;     // Long/Double 原始比特

        public static void run() throws Exception {
            System.out.println("===== 演示1：.class 文件结构解析 =====");

            byte[] data = readOwnBytes("Demo2_4.class");
            System.out.println("  解析目标: Demo2_4.class（字节数 " + data.length + "）");

            ClassFileParser p = new ClassFileParser();
            p.parse(data);
        }

        private void parse(byte[] data) throws Exception {
            in = new DataInputStream(new ByteArrayInputStream(data));

            // --- 魔数 ---
            int magic = in.readInt();
            System.out.println("  魔数 magic: 0x" + Integer.toHexString(magic).toUpperCase()
                    + (magic == 0xCAFEBABE ? " (CAFEBABE ✓)" : " (异常!)"));

            // --- 版本号 ---
            int minor = in.readUnsignedShort();
            int major = in.readUnsignedShort();
            System.out.println("  版本: major=" + major + " minor=" + minor
                    + " → 编译于 " + versionName(major));

            // --- 常量池 ---
            int cpCount = in.readUnsignedShort();
            System.out.println("  常量池数量: " + cpCount + "（有效条目 " + (cpCount - 1) + "）");
            tag = new int[cpCount];
            utf8 = new String[cpCount];
            r1 = new int[cpCount];
            r2 = new int[cpCount];
            lval = new long[cpCount];
            for (int i = 1; i < cpCount; i++) {
                readEntry(i);
                if (tag[i] == 5 || tag[i] == 6) {
                    i++; // Long/Double 占用 2 个槽位
                }
            }
            // 打印前若干条常量池项
            int shown = Math.min(cpCount - 1, 10);
            System.out.println("  常量池(前 " + shown + " 项):");
            for (int i = 1; i <= shown; i++) {
                System.out.println("    #" + i + " = " + resolve(i));
            }

            // --- 访问标志 ---
            int access = in.readUnsignedShort();
            System.out.println("  访问标志: 0x" + hex4(access) + " " + decodeClassAccess(access));

            // --- this / super ---
            int thisClass = in.readUnsignedShort();
            int superClass = in.readUnsignedShort();
            System.out.println("  this_class:  #" + thisClass + " → " + resolve(thisClass));
            System.out.println("  super_class: #" + superClass + " → "
                    + (superClass == 0 ? "(无, 即 Object)" : resolve(superClass)));

            // --- 接口 ---
            int ifaces = in.readUnsignedShort();
            System.out.println("  接口数: " + ifaces);
            for (int i = 0; i < ifaces; i++) {
                int idx = in.readUnsignedShort();
                System.out.println("    implements #" + idx + " → " + resolve(idx));
            }

            // --- 字段 ---
            int fields = in.readUnsignedShort();
            System.out.println("  字段数: " + fields);
            for (int i = 0; i < fields; i++) {
                int facc = in.readUnsignedShort();
                int nIdx = in.readUnsignedShort();
                int dIdx = in.readUnsignedShort();
                int attrCount = in.readUnsignedShort();
                skipAttributes(attrCount);
                System.out.println("    " + decodeMemberAccess(facc) + " " + resolve(dIdx) + " " + resolve(nIdx));
            }

            // --- 方法 ---
            int methods = in.readUnsignedShort();
            System.out.println("  方法数: " + methods);
            byte[] mainCode = null;
            for (int i = 0; i < methods; i++) {
                int macc = in.readUnsignedShort();
                int nIdx = in.readUnsignedShort();
                int dIdx = in.readUnsignedShort();
                int attrCount = in.readUnsignedShort();
                int codeLen = -1;
                byte[] code = null;
                for (int a = 0; a < attrCount; a++) {
                    int nameIdx = in.readUnsignedShort();
                    long alen = in.readInt() & 0xFFFFFFFFL;
                    String an = resolve(nameIdx);
                    if ("Code".equals(an)) {
                        code = parseCodeBody();
                        codeLen = code.length;
                    } else {
                        skipFully(alen);
                    }
                }
                System.out.println("    " + decodeMemberAccess(macc) + " " + resolve(nIdx) + resolve(dIdx)
                        + (codeLen >= 0 ? "  [Code 长度=" + codeLen + "B]" : ""));
                if ("main".equals(resolve(nIdx))) {
                    mainCode = code;
                }
            }

            // --- 类级属性（跳过） ---
            int classAttr = in.readUnsignedShort();
            skipAttributes(classAttr);

            // --- main 方法的字节码（十六进制） ---
            if (mainCode != null) {
                System.out.println("  main 方法字节码(十六进制, 前 48 字节):");
                StringBuilder sb = new StringBuilder("    ");
                int limit = Math.min(mainCode.length, 48);
                for (int i = 0; i < limit; i++) {
                    sb.append(String.format("%02x ", mainCode[i] & 0xFF));
                    if ((i + 1) % 16 == 0) {
                        System.out.println(sb.toString().trim());
                        sb.setLength(0);
                        sb.append("    ");
                    }
                }
                if (sb.length() > 4) System.out.println(sb.toString().trim());
                System.out.println("  [说明] 这些字节即方法体编译后的栈机指令，可用 javap -c Demo2_4 反汇编");
            }
        }

        private byte[] parseCodeBody() throws IOException {
            in.readUnsignedShort(); // max_stack
            in.readUnsignedShort(); // max_locals
            int codeLength = in.readInt();
            byte[] code = new byte[codeLength];
            in.readFully(code); // 字节码
            int exLen = in.readUnsignedShort();
            for (int e = 0; e < exLen; e++) {
                in.readUnsignedShort(); in.readUnsignedShort();
                in.readUnsignedShort(); in.readUnsignedShort();
            }
            int subAttr = in.readUnsignedShort();
            skipAttributes(subAttr);
            return code;
        }

        private void skipAttributes(int count) throws IOException {
            for (int a = 0; a < count; a++) {
                in.readUnsignedShort();
                long len = in.readInt() & 0xFFFFFFFFL;
                skipFully(len);
            }
        }

        private void skipFully(long len) throws IOException {
            long remaining = len;
            byte[] buf = new byte[8192];
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                in.readFully(buf, 0, toRead);
                remaining -= toRead;
            }
        }

        // 读取单个常量池条目
        private void readEntry(int i) throws IOException {
            int t = in.readUnsignedByte();
            tag[i] = t;
            switch (t) {
                case 1: utf8[i] = in.readUTF(); break;                          // Utf8
                case 3: r1[i] = in.readInt(); break;                          // Integer
                case 4: r1[i] = in.readInt(); break;                          // Float
                case 5: lval[i] = in.readLong(); break;                       // Long
                case 6: lval[i] = in.readLong(); break;                       // Double
                case 7: r1[i] = in.readUnsignedShort(); break;                // Class
                case 8: r1[i] = in.readUnsignedShort(); break;                // String
                case 9: case 10: case 11: case 12:                           // ref ref
                case 17: case 18:
                    r1[i] = in.readUnsignedShort();
                    r2[i] = in.readUnsignedShort();
                    break;
                case 15: // MethodHandle: u1 kind + u2 index
                    r1[i] = in.readUnsignedByte();
                    r2[i] = in.readUnsignedShort();
                    break;
                case 16: r1[i] = in.readUnsignedShort(); break;              // MethodType
                case 19: case 20: r1[i] = in.readUnsignedShort(); break;      // Module/Package
                default:
                    throw new IOException("未知的常量池 tag: " + t + " (索引 " + i + ")");
            }
        }

        // 把常量池索引解析为可读字符串
        private String resolve(int index) {
            if (index <= 0 || index >= tag.length) return "#<越界 " + index + ">";
            switch (tag[index]) {
                case 1: return utf8[index];
                case 3: return "Integer " + r1[index];
                case 4: return "Float " + Float.intBitsToFloat(r1[index]);
                case 5: return "Long " + lval[index];
                case 6: return "Double " + Double.longBitsToDouble(lval[index]);
                case 7: return resolve(r1[index]);                       // Class → 类名
                case 8: return "String " + resolve(r1[index]);
                case 9: return "Fieldref " + resolve(r1[index]) + "." + resolve(r2[index]);
                case 10: return "Methodref " + resolve(r1[index]) + "." + resolve(r2[index]);
                case 11: return "InterfaceMethodref " + resolve(r1[index]) + "." + resolve(r2[index]);
                case 12: return resolve(r1[index]) + ":" + resolve(r2[index]);
                case 15: return "MethodHandle kind=" + r1[index] + " ref=" + resolve(r2[index]);
                case 16: return "MethodType " + resolve(r1[index]);
                case 17: return "Dynamic " + resolve(r1[index]) + ":" + resolve(r2[index]);
                case 18: return "InvokeDynamic " + resolve(r1[index]) + ":" + resolve(r2[index]);
                case 19: return "Module " + resolve(r1[index]);
                case 20: return "Package " + resolve(r1[index]);
                default: return "#<" + tag[index] + ">";
            }
        }

        private static String versionName(int major) {
            if (major >= 49) return "JDK " + (major - 44);
            return "JDK 1.x (早期版本, major=" + major + ")";
        }

        private String decodeClassAccess(int flags) {
            return decode(flags, new String[][]{
                    {"public", "0x0001"}, {"final", "0x0010"}, {"super", "0x0020"},
                    {"interface", "0x0200"}, {"abstract", "0x0400"},
                    {"synthetic", "0x1000"}, {"annotation", "0x2000"},
                    {"enum", "0x4000"}, {"module", "0x8000"}
            });
        }

        private String decodeMemberAccess(int flags) {
            return decode(flags, new String[][]{
                    {"public", "0x0001"}, {"private", "0x0002"}, {"protected", "0x0004"},
                    {"static", "0x0008"}, {"final", "0x0010"}, {"synchronized", "0x0020"},
                    {"bridge", "0x0040"}, {"varargs", "0x0080"}, {"native", "0x0100"},
                    {"abstract", "0x0400"}, {"strict", "0x0800"}, {"synthetic", "0x1000"}
            });
        }

        private String decode(int flags, String[][] table) {
            StringBuilder sb = new StringBuilder();
            for (String[] e : table) {
                int v = Integer.decode(e[1]);
                if ((flags & v) != 0) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(e[0]);
                }
            }
            return sb.toString();
        }

        private static String hex4(int v) {
            return String.format("%04x", v).toUpperCase();
        }
    }

    // ============================================================
    // 演示2：字节码指令概览
    // ============================================================
    static class BytecodeOverviewDemo {

        public static void run() {
            System.out.println("\n===== 演示2：字节码指令概览 =====");

            System.out.println("  字节码是面向栈的指令集（每条指令 1 字节操作码 + 操作数）");
            System.out.println("  常见指令分类：");
            String[][] cats = {
                    {"加载/存储", "aload iload astore istore iconst_* bipush sipush ldc"},
                    {"运算", "iadd isub imul idiv irem iinc ladd dmul"},
                    {"类型转换", "i2l i2d l2i f2d i2b checkcast"},
                    {"对象/字段", "new getfield putfield getstatic putstatic"},
                    {"方法调用", "invokestatic invokevirtual invokespecial invokeinterface invokedynamic"},
                    {"控制转移", "ifeq if_icmpne goto tableswitch lookupswitch ireturn return"},
                    {"异常处理", "athrow athrow + 异常表"},
                    {"同步", "monitorenter monitorexit"}
            };
            for (String[] c : cats) {
                System.out.println("    " + pad(c[0], 12) + " : " + c[1]);
            }
            System.out.println();
            System.out.println("  查看 Demo2_4 完整反汇编：");
            System.out.println("    javap -v  Demo2_4   (含常量池/行号)");
            System.out.println("    javap -c  Demo2_4   (仅字节码)");
            System.out.println("    javap -p  Demo2_4   (含私有成员)");
            System.out.println("  [说明] 演示1 中解析出的 main 字节码十六进制即这些操作码+操作数。");
        }

        private static String pad(String s, int w) {
            StringBuilder sb = new StringBuilder(s);
            int v = 0;
            for (char c : s.toCharArray()) v += (c > 127) ? 2 : 1;
            while (v++ < w) sb.append(' ');
            return sb.toString();
        }
    }

    // ============================================================
    // 演示3：Instrumentation / Java Agent 原理
    // ============================================================
    static class InstrumentationDemo {

        public static void run() {
            System.out.println("\n===== 演示3：Instrumentation / Java Agent 原理 =====");

            System.out.println("  Java Agent 允许在类加载时介入字节码，实现 APM、热替换、Mock 等。");
            System.out.println();
            System.out.println("  [1] 启动时挂载：premain（在 main 之前运行）");
            System.out.println("    运行：java -javaagent:agent.jar YourApp");
            System.out.println("    MANIFEST.MF:  Premain-Class: xxx.MyAgent  Can-Redefine-Classes: true");
            System.out.println("    代码模式：");
            System.out.println("      public static void premain(String args, Instrumentation inst){");
            System.out.println("          inst.addTransformer(new ClassFileTransformer(){");
            System.out.println("              public byte[] transform(ClassLoader loader, String name,");
            System.out.println("                      Class<?> c, ProtectionDomain pd, byte[] code){");
            System.out.println("                  // 用 ASM/Javassist 改写 code 并返回；返回 null 表示不改");
            System.out.println("                  return null;");
            System.out.println("              }");
            System.out.println("          });");
            System.out.println("      }");
            System.out.println();
            System.out.println("  [2] 运行时挂载：agentmain（通过 Attach API 动态附加到已运行进程）");
            System.out.println("    VirtualMachine vm = VirtualMachine.attach(PID);");
            System.out.println("    vm.loadAgent(\"agent.jar\");");
            System.out.println("    vm.detach();");
            System.out.println("    代码模式：");
            System.out.println("      public static void agentmain(String args, Instrumentation inst){");
            System.out.println("          inst.retransformClasses(TargetClass.class); // 重新转换已加载的类");
            System.out.println("      }");
            System.out.println();
            System.out.println("  [3] 常见应用");
            System.out.println("    - SkyWalking / Pinpoint：无侵入链路追踪（改写方法插入埋点）");
            System.out.println("    - Arthas / BTrace：线上诊断与热更新");
            System.out.println("    - JRebel：热部署");
            System.out.println("    - Mock 框架：运行时替换字节码");
            System.out.println("  [说明] 本演示为原理说明，需打成 agent.jar 并用 -javaagent 加载才能真正生效。");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 2.4 — 字节码与 Instrumentation 实战 ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        ClassFileParser.run();        // 演示1：.class 文件结构解析
        BytecodeOverviewDemo.run();  // 演示2：字节码指令概览
        InstrumentationDemo.run();   // 演示3：Java Agent 原理

        System.out.println("\n===== 全部演示结束 =====");
    }

    // 读取自身 Demo2_4.class 字节码
    private static byte[] readOwnBytes(String resource) throws IOException {
        try (InputStream in = Demo2_4.class.getResourceAsStream("/" + resource)) {
            if (in != null) return readAll(in);
        } catch (IOException ignore) {
        }
        File f = new File(resource);
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                return readAll(in);
            }
        }
        throw new IOException("找不到 " + resource + "，请在 demo2_4 目录下运行");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
