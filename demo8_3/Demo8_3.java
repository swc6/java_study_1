import java.lang.ref.WeakReference;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demo 8.3 — 监控与诊断 实战演示
 *
 * 涵盖内容：
 *   8.3.1 方法耗时监控（手动计时 / 调用计数器）
 *   8.3.2 内存泄漏排查（强引用泄漏 / WeakReference / WeakHashMap）
 *   8.3.3 线程堆栈与死锁检测（ThreadMXBean）
 *
 * 说明：纯 JDK 实现，演示排查思路（AOP/Micrometer 等需第三方依赖）
 *
 * 编译：javac Demo8_3.java
 * 运行：java Demo8_3
 */
public class Demo8_3 {

    // ============================================================
    // 演示1：方法耗时监控（手动计时 + 调用统计）
    // ============================================================
    static class TimingDemo {

        // 简易计时器：记录每个方法的调用次数与累计耗时
        static class TimingRegistry {
            private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();
            private final Map<String, AtomicLong> totals = new ConcurrentHashMap<>();

            public void record(String method, long costNs) {
                counts.computeIfAbsent(method, k -> new AtomicLong()).incrementAndGet();
                totals.computeIfAbsent(method, k -> new AtomicLong()).addAndGet(costNs);
            }

            public void report() {
                System.out.println("  方法耗时统计:");
                System.out.printf("    %-20s %-10s %-12s %-10s%n",
                        "method", "calls", "total(ms)", "avg(ms)");
                for (String m : counts.keySet()) {
                    long c = counts.get(m).get();
                    long t = totals.get(m).get();
                    System.out.printf("    %-20s %-10d %-12.2f %-10.3f%n",
                            m, c, t / 1_000_000.0, (t / 1_000_000.0) / c);
                }
            }
        }

        static TimingRegistry registry = new TimingRegistry();

        static String bizMethod(String input) {
            long start = System.nanoTime();
            try {
                // 模拟业务：拼接 + sleep
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 1000; i++) sb.append(input);
                Thread.sleep(2);
                return sb.toString().length() + " chars";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } finally {
                registry.record("bizMethod", System.nanoTime() - start);
            }
        }

        public static void run() throws InterruptedException {
            System.out.println("===== 漓示1：方法耗时监控 =====");

            for (int i = 0; i < 50; i++) bizMethod("data" + i);
            registry.report();

            System.out.println("  [结论]");
            System.out.println("    - 生产用 AOP（Spring 切面）自动织入计时逻辑");
            System.out.println("    - 指标系统：Micrometer + Prometheus（采集 QPS/RT/p99）");
            System.out.println("    - 阿里 Arthas：trace/watch/monitor 命令可在线诊断");
        }
    }

    // ============================================================
    // 漓示2：内存泄漏排查
    // ============================================================
    static class LeakDemo {

        // 反例：静态 Map 持有对象引用，造成泄漏
        static class BuggyCache {
            private static final Map<String, byte[]> CACHE = new HashMap<>();
            public static void put(String k, byte[] v) { CACHE.put(k, v); }
            public static int size() { return CACHE.size(); }
        }

        // 正例1：WeakReference 包装 value，GC 时自动回收
        static class WeakCache {
            private final Map<String, WeakReference<byte[]>> cache = new HashMap<>();
            public void put(String k, byte[] v) { cache.put(k, new WeakReference<>(v)); }
            public byte[] get(String k) {
                WeakReference<byte[]> r = cache.get(k);
                return r == null ? null : r.get();
            }
            public int size() { return cache.size(); }
        }

        // 正例2：WeakHashMap，key 弱引用，key 被 GC 时整个 entry 移除
        static class WeakKeyCache {
            private final Map<Object, String> cache = new WeakHashMap<>();
            public void put(Object k, String v) { cache.put(k, v); }
            public int size() { return cache.size(); }
        }

        public static void run() throws InterruptedException {
            System.out.println("\n===== 漓示2：内存泄漏排查 =====");

            Runtime rt = Runtime.getRuntime();

            // 反例
            for (int i = 0; i < 1000; i++) BuggyCache.put("k" + i, new byte[1024]);
            System.out.println("  BuggyCache 强引用 size=" + BuggyCache.size()
                    + " 堆已用=" + mb(rt) + " MB");
            // 即使切断了外部引用，静态 Map 仍持有对象 → 泄漏

            // 正例1：弱引用 value
            WeakCache wc = new WeakCache();
            byte[] big = new byte[10 * 1024 * 1024]; // 10MB
            wc.put("big", big);
            System.out.println("  WeakCache GC 前: get=" + (wc.get("big") != null) + " size=" + wc.size());
            big = null; // 切断外部强引用
            System.gc(); Thread.sleep(200);
            System.out.println("  WeakCache GC 后: get=" + (wc.get("big") != null) + " size=" + wc.size());

            // 正例2：WeakHashMap
            WeakKeyCache wkc = new WeakKeyCache();
            Object key = new Object();
            wkc.put(key, "value");
            System.out.println("  WeakKeyCache GC 前: size=" + wkc.size());
            key = null;
            System.gc(); Thread.sleep(200);
            System.out.println("  WeakKeyCache GC 后: size=" + wkc.size());

            System.out.println("  [结论]");
            System.out.println("    - 泄漏常见原因：静态集合/未注销监听器/未关闭资源/缓存无淘汰");
            System.out.println("    - 弱引用/虚引用 可作为缓存键/值，让 GC 决定回收");
            System.out.println("    - 排查工具：jmap dump → MAT 分析支配树 / 引用链");
        }

        private static long mb(Runtime rt) {
            return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        }
    }

    // ============================================================
    // 漓示3：线程堆栈与死锁检测
    // ============================================================
    static class ThreadDiagDemo {

        final Object lock1 = new Object();
        final Object lock2 = new Object();

        void deadlockedTask() {
            // 故意制造死锁：t1 持 lock1 等 lock2，t2 持 lock2 等 lock1
            Thread t1 = new Thread(() -> {
                synchronized (lock1) {
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    synchronized (lock2) { System.out.println("    t1 acquired both"); }
                }
            }, "deadlock-t1");

            Thread t2 = new Thread(() -> {
                synchronized (lock2) {
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    synchronized (lock1) { System.out.println("    t2 acquired both"); }
                }
            }, "deadlock-t2");

            t1.start(); t2.start();
            try {
                Thread.sleep(500); // 等死锁形成
            } catch (InterruptedException ignored) {}

            ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
            long[] ids = tmx.findDeadlockedThreads();
            System.out.println("  发现死锁线程: " + (ids == null ? "无" : ids.length + " 个"));
            if (ids != null) {
                ThreadInfo[] infos = tmx.getThreadInfo(ids, 1);
                for (ThreadInfo info : infos) {
                    System.out.println("    [" + info.getThreadName() + "] "
                            + info.getLockName());
                }
            }

            // 演示普通线程堆栈
            System.out.println("  当前活动线程数: " + tmx.getThreadCount());
            System.out.println("  峰值线程数   : " + tmx.getPeakThreadCount());

            System.out.println("  [结论]");
            System.out.println("    - ThreadMXBean.findDeadlockedThreads() 自动检测死锁");
            System.out.println("    - 命令行：jstack -l <pid> 输出全部线程堆栈 + 锁信息");
            System.out.println("    - 死锁典型：嵌套 synchronized 顺序不一致");
        }

        public static void run() {
            System.out.println("\n===== 漓示3：线程堆栈与死锁检测 =====");
            new ThreadDiagDemo().deadlockedTask();
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 8.3 — 监控与诊断 实战演示             ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        TimingDemo.run();       // 漓示1：方法耗时
        LeakDemo.run();        // 漓示2：内存泄漏
        ThreadDiagDemo.run();  // 漓示3：线程诊断

        System.out.println("\n===== 全部演示结束 =====");
    }
}
