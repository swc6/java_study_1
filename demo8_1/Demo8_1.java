import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/**
 * Demo 8.1 — JVM 调优 实战演示
 *
 * 涵盖内容：
 *   8.1.1 运行时查看 JVM 参数（堆/Metaspace/GC 策略/线程）
 *   8.1.2 堆内存监控（Eden/Survivor/Old/Metaspace 实时占用）
 *
 * 说明：
 *   - 不依赖第三方库，全部使用 JDK 内置 java.lang.management API
 *   - JVM 启动参数需在外部用 -Xms/-Xmx/-XX 等指定，本 demo 仅读取
 *
 * 编译：javac Demo8_1.java
 * 运行：java Demo8_1
 *       （建议：java -Xms256m -Xmx512m -XX:+UseG1GC Demo8_1）
 */
public class Demo8_1 {

    // ============================================================
    // 演示1：运行时查看 JVM 参数
    // ============================================================
    static class RuntimeParamsDemo {

        public static void run() {
            System.out.println("===== 演示1：运行时 JVM 参数 =====");

            Runtime rt = Runtime.getRuntime();
            System.out.println("  可用 CPU 核数        : " + rt.availableProcessors());
            System.out.println("  JVM 最大堆(try)     : " + mb(rt.maxMemory()) + " MB");
            System.out.println("  当前已分配堆(total) : " + mb(rt.totalMemory()) + " MB");
            System.out.println("  当前空闲堆(free)    : " + mb(rt.freeMemory()) + " MB");
            System.out.println("  当前已用堆          : " + mb(rt.totalMemory() - rt.freeMemory()) + " MB");

            // 获取 JVM 启动参数（-Xmx / -XX:+... 等）
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            System.out.println("  JVM 启动参数        :");
            for (String a : args) {
                System.out.println("    " + a);
            }

            System.out.println("  JVM 名称/版本       : "
                    + ManagementFactory.getRuntimeMXBean().getName());
            System.out.println("  Java 版本          : "
                    + System.getProperty("java.version"));

            System.out.println("  [结论]");
            System.out.println("    - -Xms/-Xmx 控制堆大小，-Xmn 控制新生代");
            System.out.println("    - -XX:+UseG1GC / UseZGC 选择 GC 算法");
            System.out.println("    - -XX:+HeapDumpOnOutOfMemoryError 可在 OOM 时自动 dump");
            System.out.println("    - 同样参数可用 jcmd <pid> VM.flags 查看");
        }

        private static long mb(long bytes) { return bytes / (1024 * 1024); }
    }

    // ============================================================
    // 演示2：堆内存监控（各内存池占用）
    // ============================================================
    static class MemoryMonitorDemo {

        public static void run() {
            System.out.println("\n===== 漓示2：堆内存监控 =====");

            MemoryMXBean mem = ManagementFactory.getMemoryMXBean();

            System.out.println("  堆内存使用:");
            printUsage("    堆(Heap)", mem.getHeapMemoryUsage());
            System.out.println("  非堆内存使用:");
            printUsage("    非堆(NonHeap)", mem.getNonHeapMemoryUsage());

            System.out.println("  内存池明细:");
            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean p : pools) {
                MemoryUsage u = p.getUsage();
                System.out.printf("    %-22s 已用=%7d KB 已分配=%7d KB 最大=%7s%n",
                        p.getName(),
                        u.getUsed() / 1024,
                        u.getCommitted() / 1024,
                        u.getMax() == -1 ? "未限制" : (u.getMax() / 1024) + " KB");
            }

            System.out.println("  [结论]");
            System.out.println("    - 堆分为新生代（Eden + 2 Survivor）与老年代");
            System.out.println("    - Metaspace 存放类元数据（替代旧版永久代 PermGen）");
            System.out.println("    - 监控 GC：jstat -gcutil <pid> 1000 每秒输出");
            System.out.println("    - OOM dump：jmap -dump:format=b,file=heap.hprof <pid>");
        }

        private static void printUsage(String label, MemoryUsage u) {
            System.out.printf("%s 已用=%d KB 已分配=%d KB 最大=%s%n",
                    label,
                    u.getUsed() / 1024,
                    u.getCommitted() / 1024,
                    u.getMax() == -1 ? "未限制" : (u.getMax() / 1024) + " KB");
        }
    }

    // ============================================================
    // 演示3：手动触发 GC，观察内存变化
    // ============================================================
    static class GcObserveDemo {

        public static void run() {
            System.out.println("\n===== 漓示3：手动触发 GC 观察 =====");

            MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
            long before = mem.getHeapMemoryUsage().getUsed();
            System.out.println("  GC 前堆已用: " + (before / 1024) + " KB");

            // 制造 100MB 临时垃圾
            byte[][] junk = new byte[100][];
            for (int i = 0; i < junk.length; i++) {
                junk[i] = new byte[1024 * 1024]; // 1MB
            }
            long allocated = mem.getHeapMemoryUsage().getUsed();
            System.out.println("  分配 100MB 后: " + (allocated / 1024) + " KB");

            // 切断引用并 GC
            for (int i = 0; i < junk.length; i++) junk[i] = null;
            System.gc();
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            long after = mem.getHeapMemoryUsage().getUsed();
            System.out.println("  GC 后堆已用: " + (after / 1024) + " KB");
            System.out.println("  回收量: " + ((allocated - after) / 1024) + " KB");

            System.out.println("  [结论]");
            System.out.println("    - System.gc() 只是建议，JVM 可选择忽略，生产慎用");
            System.out.println("    - 想要强制 dump：jcmd <pid> GC.run");
            System.out.println("    - getUsed() 统计“已占用”，含未回收的可达对象");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 8.1 — JVM 调优 实战演示              ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        RuntimeParamsDemo.run();  // 演示1：运行时参数
        MemoryMonitorDemo.run();  // 演示2：堆内存监控
        GcObserveDemo.run();       // 演示3：GC 观察

        System.out.println("\n===== 全部演示结束 =====");
    }
}
