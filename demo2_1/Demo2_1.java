import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo 2.1 — JVM 内存结构 实战演示
 *
 * 涵盖内容：
 *   2.1.1 运行时数据区（堆、方法区/Metaspace、虚拟机栈、本地方法栈、程序计数器）
 *   2.1.2 对象内存布局（Mark Word / Klass Pointer / 实例数据 / 对齐填充）
 *   2.1.3 内存溢出演示（Heap OOM）
 *
 * 编译：javac Demo2_1.java
 * 运行：java Demo2_1
 *   （查看对象头实时读取可加：--add-opens java.base/sun.misc=ALL-UNNAMED）
 */
public class Demo2_1 {

    // ============================================================
    // 演示1：运行时数据区 —— 用 JMX 展示堆 / 非堆 / 各内存池 / 线程
    // ============================================================
    static class RuntimeDataAreaDemo {

        public static void run() {
            System.out.println("===== 演示1：运行时数据区（JMX 监控） =====");

            MemoryMXBean mem = ManagementFactory.getMemoryMXBean();

            // --- 堆（Heap）：线程共享，存放对象实例与数组，GC 主战场 ---
            MemoryUsage heap = mem.getHeapMemoryUsage();
            System.out.println("  [堆 Heap]（线程共享）");
            System.out.println("    已用/初始/已提交/最大 = "
                    + mb(heap.getUsed()) + " / " + mb(heap.getInit())
                    + " / " + mb(heap.getCommitted()) + " / " + mb(heap.getMax()) + " MB");

            // --- 非堆（Non-Heap）：方法区/Metaspace、Code Cache 等归类于此 ---
            MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();
            System.out.println("  [非堆 Non-Heap]（含方法区/Metaspace）");
            System.out.println("    已用/已提交/最大 = "
                    + mb(nonHeap.getUsed()) + " / " + mb(nonHeap.getCommitted())
                    + " / " + (nonHeap.getMax() == -1 ? "未限制" : mb(nonHeap.getMax()) + " MB") + "");

            // --- 各内存池：把运行时数据区落到具体区域 ---
            System.out.println("  [内存池明细]（每个池对应一块运行时数据区）");
            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean p : pools) {
                MemoryUsage u = p.getUsage();
                if (u == null || u.getMax() == -1) {
                    continue; // 跳过没有上限或未启用的池
                }
                String region = classify(p.getName(), p.getType());
                System.out.println("    " + pad(p.getName(), 26)
                        + " 类型=" + (p.getType() == MemoryType.HEAP ? "堆  " : "非堆")
                        + " 区域=" + region
                        + " 用量=" + mb(u.getUsed()) + "/" + mb(u.getMax()) + "MB");
            }

            // --- 虚拟机栈 / 本地方法栈 / 程序计数器：线程私有 ---
            ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            System.out.println("  [线程私有区]（虚拟机栈 / 本地方法栈 / 程序计数器）");
            System.out.println("    活跃线程数=" + threads.getThreadCount()
                    + "  峰值线程数=" + threads.getPeakThreadCount());
            System.out.println("    程序计数器：唯一不会 OOM 的区域（仅记录字节码行号）");

            System.out.println("  [结论] 堆/方法区为线程共享，栈/PC/本地方法栈为线程私有；");
            System.out.println("         堆存放对象，方法区(Metaspace)存放类元数据。");
        }

        // 把内存池名映射到运行时数据区的概念区域
        private static String classify(String name, MemoryType type) {
            String n = name.toLowerCase();
            if (n.contains("eden")) return "新生代-Eden";
            if (n.contains("survivor")) return "新生代-Survivor";
            if (n.contains("old") || n.contains("tenured")) return "老年代";
            if (n.contains("metaspace")) return "方法区(Metaspace)";
            if (n.contains("code")) return "代码缓存";
            if (n.contains("compressed")) return "压缩类空间";
            return type == MemoryType.HEAP ? "堆" : "非堆";
        }

        private static long mb(long bytes) {
            return bytes / (1024 * 1024);
        }

        private static String pad(String s, int w) {
            StringBuilder sb = new StringBuilder(s);
            // 中文按两个宽度近似
            int visual = 0;
            for (char c : s.toCharArray()) {
                visual += (c > 127) ? 2 : 1;
            }
            while (visual < w) {
                sb.append(' ');
                visual++;
            }
            return sb.toString();
        }
    }

    // ============================================================
    // 演示2：对象内存布局 —— 经验式测量 + Mark Word 实读（尽力而为）
    // ============================================================
    static class ObjectLayoutDemo {

        // 普通对象：Mark Word(8) + Klass Pointer(4, 压缩) + 实例数据 + 对齐填充(8字节对齐)
        static class Empty {}                         // 预测 16B（12 头 + 4 填充）
        static class Small { int a; long b; }         // 预测 24B（12 头 + 4 + 8）
        static class Big { long l1, l2, l3, l4; }      // 预测 48B（12 头 + 32 = 44，对齐到 48）

        private static final int N1 = 1_000_000;
        private static final int N2 = 2_000_000;

        public static void run() {
            System.out.println("\n===== 演示2：对象内存布局 =====");

            // --- 经验式测量：用堆增量 / 对象数 估算单个对象大小（版本无关） ---
            System.out.println("  [经验式测量] 分配大量对象，用堆增量/对象数 估算大小");
            measure("Object (new Object())", 16, () -> new Object());
            measure("Empty (空类)", 16, () -> new Empty());
            measure("Small {int a; long b;}", 24, () -> new Small());
            measure("Big {4 个 long}", 48, () -> new Big());
            measure("int[10] (数组)", 56, () -> new int[10]);
            System.out.println("    (实测为堆增量近似，受 TLAB 对齐/分配粒度影响，存在 ±几字节波动)");

            System.out.println("  [对象头结构]");
            System.out.println("    Mark Word(8B)：哈希码、GC 年龄、锁状态、偏向线程ID");
            System.out.println("    Klass Pointer(4B 压缩)：指向方法区中的类元数据");
            System.out.println("    实例数据：各字段值（按类型宽度排列）");
            System.out.println("    对齐填充：补齐到 8 字节的整数倍");

            // --- Mark Word 实读：尝试通过 Unsafe 读取对象头，观察哈希码写入 ---
            readMarkWordBestEffort();
        }

        private static void readMarkWordBestEffort() {
            System.out.println("\n  [Mark Word 实读] 尝试通过 sun.misc.Unsafe 读取对象头");
            try {
                Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
                Field f = unsafeCls.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                Object unsafe = f.get(null);
                Method getLong = unsafeCls.getMethod("getLong", Object.class, long.class);

                Object o = new Object();
                long m0 = (long) getLong.invoke(unsafe, o, 0L);   // Mark Word（偏移0）
                int hash = System.identityHashCode(o);             // 计算哈希码 → 写入 Mark Word
                long m1 = (long) getLong.invoke(unsafe, o, 0L);

                System.out.println("    Mark Word(计算哈希前) = 0x" + Long.toHexString(m0));
                System.out.println("    identityHashCode(o)   = " + hash);
                System.out.println("    Mark Word(计算哈希后) = 0x" + Long.toHexString(m1));
                System.out.println("    [结果] Mark Word 发生变化 —— 哈希码被写入对象头");

                // 数组头：arrayBaseOffset = 数组对象头大小，arrayIndexScale = 元素宽度
                Method baseOff = unsafeCls.getMethod("arrayBaseOffset", Class.class);
                Method idxScale = unsafeCls.getMethod("arrayIndexScale", Class.class);
                int arrHeader = (int) baseOff.invoke(unsafe, int[].class);
                int arrScale = (int) idxScale.invoke(unsafe, int[].class);
                System.out.println("    int[] 数组头长度=" + arrHeader + "B，元素宽度=" + arrScale + "B"
                        + " → int[10] = " + (arrHeader + 10 * arrScale) + "B");
            } catch (Throwable t) {
                // JDK 16+ 默认禁止反射访问 sun.misc.Unsafe，属正常
                System.out.println("    [跳过实读] 无法访问 sun.misc.Unsafe：" + shortName(t));
                System.out.println("    如需实读请加 JVM 参数：--add-opens java.base/sun.misc=ALL-UNNAMED");
                System.out.println("    Mark Word 状态（概念）：");
                System.out.println("      无锁: hash码/00 | 轻量级: 栈锁指针/00 | 重量级: Monitor指针/10");
            }
        }

        // 测量单个对象大小：先分配满容量数组并预热填充，再用两段差值法抵消数组自身开销
        // 两阶段之间触发一次 GC：让 phase1 对象稳定下来，phase2 的增量更接近真实对象大小
        private static void measure(String label, int predicted, Factory f) {
            System.gc(); // 先清理上一次 measure 产生的垃圾
            Object[] holder = new Object[N2];
            for (int i = 0; i < N1; i++) holder[i] = f.create(); // 预热填充
            System.gc(); // 引用仍在 holder 中不会被回收，仅稳定基线
            long m1 = usedHeap();
            for (int i = N1; i < N2; i++) holder[i] = f.create(); // 只新增 (N2-N1) 个对象
            long m2 = usedHeap();
            long per = (m2 - m1) / (N2 - N1);
            System.out.println("    " + pad(label, 28) + " 预测=" + predicted + "B"
                    + "  实测≈" + per + "B" + match(per, predicted));
        }

        // 容差判定：受 TLAB 对齐/分配粒度影响，实测可能有 ±几字节波动
        private static String match(long real, int pred) {
            return Math.abs(real - pred) <= 4 ? " ✓" : " (波动较大)";
        }

        private static long usedHeap() {
            return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        }

        private static String pad(String s, int w) {
            StringBuilder sb = new StringBuilder(s);
            int visual = 0;
            for (char c : s.toCharArray()) visual += (c > 127) ? 2 : 1;
            while (visual++ < w) sb.append(' ');
            return sb.toString();
        }

        private static String shortName(Throwable t) {
            String n = t.getClass().getName();
            int dot = n.lastIndexOf('.');
            return (dot >= 0 ? n.substring(dot + 1) : n) + (t.getMessage() == null ? "" : ": " + t.getMessage());
        }

        @FunctionalInterface
        interface Factory { Object create(); }
    }

    // ============================================================
    // 演示3：堆内存溢出 —— 持续分配直到抛出 OutOfMemoryError
    // ============================================================
    static class HeapOOMDemo {

        public static void run() {
            System.out.println("\n===== 演示3：堆内存溢出（OutOfMemoryError） =====");

            List<byte[]> list = new ArrayList<>();
            int chunkMB = 8;        // 每次分配 8MB
            int cap = 256;          // 最多分配 256 块（2GB）以避免在大堆 JVM 上耗时过久
            int count = 0;
            try {
                while (count < cap) {
                    list.add(new byte[chunkMB * 1024 * 1024]);
                    count++;
                }
                System.out.println("  分配 " + (count * chunkMB) + "MB 后未触发 OOM（堆较大）");
                System.out.println("  [提示] 用 -Xmx128m 运行可稳定复现堆 OOM：java -Xmx128m Demo2_1");
            } catch (OutOfMemoryError e) {
                System.out.println("  成功分配 ≈ " + (count * chunkMB) + "MB 后堆耗尽");
                System.out.println("  捕获到异常：" + e.getClass().getSimpleName() + ": " + e.getMessage());
                System.out.println("  [结论] 堆无法继续扩展时抛出 OutOfMemoryError；");
                System.out.println("         可通过 -Xmx / -Xms 调控堆大小，-XX:+HeapDumpOnOutOfMemoryError 导出 dump");
            } finally {
                list.clear(); // 释放内存
            }
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 2.1 — JVM 内存结构 实战演示          ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        RuntimeDataAreaDemo.run(); // 演示1：运行时数据区
        ObjectLayoutDemo.run();    // 演示2：对象内存布局
        HeapOOMDemo.run();         // 演示3：堆 OOM

        System.out.println("\n===== 全部演示结束 =====");
    }
}
