import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 2.2 — 垃圾回收 实战演示
 *
 * 涵盖内容：
 *   2.2.1 判断对象存活（可达性分析 + Cleaner 验证回收）
 *   2.2.2 四种引用类型（强/软/弱/虚）
 *   2.2.3 垃圾回收算法模拟（标记-清除 / 复制 / 标记-整理）
 *   2.2.4 GC 统计与内存监控
 *
 * 编译：javac Demo2_2.java
 * 运行：java Demo2_2
 */
public class Demo2_2 {

    // ============================================================
    // 演示1：对象存活判断 —— 可达性分析（不可达对象被回收）
    // ============================================================
    static class ReachabilityDemo {

        public static void run() throws InterruptedException {
            System.out.println("===== 演示1：判断对象存活（可达性分析） =====");

            // 用 Cleaner（JDK 9+）在对象被 GC 回收时收到通知，验证"不可达即回收"
            Cleaner cleaner = Cleaner.create();
            AtomicInteger reclaimed = new AtomicInteger(0);

            final int TOTAL = 50_000;
            for (int i = 0; i < TOTAL; i++) {
                Object o = new Object();
                cleaner.register(o, reclaimed::incrementAndGet); // 注册清理动作
                // o 在循环结束后不再可达（无引用指向它）
            }

            System.out.println("  创建 " + TOTAL + " 个对象并立即丢弃（不可达）");
            System.out.println("  触发 GC 等待 Cleaner 回收 ...");

            // 多次触发 GC 并等待 Cleaner 守护线程处理
            int before = reclaimed.get();
            for (int k = 0; k < 5; k++) {
                System.gc();
                Thread.sleep(200);
            }
            int after = reclaimed.get();

            System.out.println("  GC 前 Cleaner 回收数: " + before);
            System.out.println("  GC 后 Cleaner 回收数: " + after + " / " + TOTAL);
            System.out.println("  [结论] 从 GC Roots 出发不可达的对象被回收；");
            System.out.println("         GC Roots = 栈中局部变量、静态变量、常量、JNI 引用等");
            if (after < TOTAL) {
                System.out.println("  [说明] 未全部回收属正常：GC 与 Cleaner 异步，可能尚未处理完。");
            }
        }
    }

    // ============================================================
    // 演示2：四种引用类型 —— 强 / 软 / 弱 / 虚
    // ============================================================
    static class ReferenceTypeDemo {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示2：四种引用类型 =====");

            // --- 强引用：只要还在引用就不会被回收 ---
            Object strong = new Object();
            System.out.println("  强引用 new Object() : " + strong + " (永不回收，除非置 null)");

            // --- 软引用：内存不足时才回收 ---
            SoftReference<byte[]> soft = new SoftReference<>(new byte[10 * 1024 * 1024]); // 10MB
            System.out.println("  软引用(10MB) 初始 get(): " + (soft.get() != null ? "存在" : "null"));

            // --- 弱引用：下一次 GC 即回收 ---
            WeakReference<Object> weak = new WeakReference<>(new Object());
            System.out.println("  弱引用 初始 get(): " + (weak.get() != null ? "存在" : "null"));

            // --- 虚引用：get() 永远返回 null，唯一作用是收到回收通知 ---
            ReferenceQueue<Object> queue = new ReferenceQueue<>();
            PhantomReference<Object> phantom = new PhantomReference<>(new Object(), queue);
            System.out.println("  虚引用 get(): " + phantom.get() + " (总是 null，配合 ReferenceQueue 用)");

            // 触发 GC，观察软/弱/虚引用的变化
            System.out.println("  --- 触发 GC ---");
            // 释放强引用，便于观察
            strong = null;
            System.gc();
            Thread.sleep(300);

            System.out.println("  GC 后 弱引用 get(): " + (weak.get() != null ? "仍存在" : "已被回收")
                    + " (只要 GC 就回收)");
            System.out.println("  GC 后 软引用 get(): " + (soft.get() != null ? "仍存在(内存充足)" : "已被回收(内存不足时)"));

            Reference<?> enqueued = queue.poll();
            System.out.println("  GC 后 虚引用是否入队: " + (enqueued != null ? "已入队 → 对象已被回收" : "暂未入队")
                    + " (虚引用对象回收后会入 ReferenceQueue)");
        }
    }

    // ============================================================
    // 演示3：垃圾回收算法模拟（标记-清除 / 复制 / 标记-整理）
    // ============================================================
    static class GCAlgorithmDemo {

        // 用布尔数组模拟堆：true=存活对象，false=空闲
        // roots 指定哪些槽位是 GC Roots 直达的存活对象
        public static void run() {
            System.out.println("\n===== 演示3：垃圾回收算法模拟 =====");

            int size = 24;
            Random rnd = new Random(0xC0FFEE);
            boolean[] heap = new boolean[size];
            // 随机标记部分对象为存活
            for (int i = 0; i < size; i++) {
                heap[i] = rnd.nextInt(100) < 40; // 约 40% 存活
            }
            System.out.println("  初始堆(■存活 □空闲): " + visualize(heap));

            // --- 标记-清除 ---
            boolean[] ms = markSweep(heap);
            System.out.println("  标记-清除后        : " + visualize(ms) + " (空闲就地置空，有碎片)");

            // --- 复制算法（存活对象复制到前半区） ---
            boolean[] copy = copying(heap);
            System.out.println("  复制算法后        : " + visualize(copy) + " (存活对象紧凑到一端，无碎片)");

            // --- 标记-整理（存活对象前移，保留相对顺序） ---
            boolean[] compact = markCompact(heap);
            System.out.println("  标记-整理后        : " + visualize(compact) + " (前移并保留顺序，无碎片)");

            System.out.println("  [结论] 新生代用复制算法(Eden+S0+S1)，老年代用标记-清除/标记-整理");
        }

        // 标记-清除：标记存活对象后，把死亡对象直接就地置空（false）
        // 结果：存活对象位置不变，产生空闲碎片
        private static boolean[] markSweep(boolean[] src) {
            return src.clone(); // 本模拟中 false 即"已清除的空洞"，就地保留 → 有碎片
        }

        // 复制算法：存活对象复制到数组前段，后段清空
        private static boolean[] copying(boolean[] src) {
            boolean[] r = new boolean[src.length];
            int w = 0;
            for (boolean b : src) {
                if (b) r[w++] = true;
            }
            return r;
        }

        // 标记-整理：存活对象前移，保持顺序，后面补 false
        private static boolean[] markCompact(boolean[] src) {
            boolean[] r = new boolean[src.length];
            int w = 0;
            for (boolean b : src) {
                if (b) r[w++] = true;
            }
            return r; // 与 copying 在本模拟中等价，强调"保留相对顺序、无碎片"
        }

        private static String visualize(boolean[] arr) {
            StringBuilder sb = new StringBuilder();
            for (boolean b : arr) sb.append(b ? '■' : '□');
            return sb.toString();
        }
    }

    // ============================================================
    // 演示4：GC 统计与内存监控
    // ============================================================
    static class GCMonitorDemo {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示4：GC 统计与内存监控 =====");

            List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
            System.out.println("  [GC 收集器]");
            for (GarbageCollectorMXBean g : gcs) {
                System.out.println("    " + pad(g.getName(), 28)
                        + " 收集次数=" + g.getCollectionCount()
                        + "  累计耗时=" + g.getCollectionTime() + "ms");
            }

            MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
            MemoryUsage h1 = mem.getHeapMemoryUsage();
            System.out.println("  [GC 前] 堆已用: " + mb(h1.getUsed()) + "MB / " + mb(h1.getCommitted()) + "MB");

            // 制造一批垃圾
            List<byte[]> junk = new ArrayList<>();
            for (int i = 0; i < 200; i++) junk.add(new byte[1024 * 1024]); // 200MB
            junk = null; // 立即丢弃，使其不可达

            System.out.println("  触发 System.gc() ...");
            System.gc();
            Thread.sleep(200);

            MemoryUsage h2 = mem.getHeapMemoryUsage();
            System.out.println("  [GC 后] 堆已用: " + mb(h2.getUsed()) + "MB / " + mb(h2.getCommitted()) + "MB");

            // 各代使用量变化
            System.out.println("  [各内存池 GC 后用量]");
            for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
                MemoryUsage u = p.getUsage();
                if (u != null && u.getMax() != -1) {
                    System.out.println("    " + pad(p.getName(), 26)
                            + mb(u.getUsed()) + "/" + mb(u.getMax()) + "MB");
                }
            }

            // 统计 GC 次数变化
            for (GarbageCollectorMXBean g : gcs) {
                System.out.println("    " + g.getName() + " 当前次数=" + g.getCollectionCount());
            }
            System.out.println("  [说明] 可用 -Xlog:gc* 查看 GC 日志；-XX:+UseG1GC 切换收集器");
        }

        private static long mb(long bytes) { return bytes / (1024 * 1024); }

        private static String pad(String s, int w) {
            StringBuilder sb = new StringBuilder(s);
            int v = 0;
            for (char c : s.toCharArray()) v += (c > 127) ? 2 : 1;
            while (v++ < w) sb.append(' ');
            return sb.toString();
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 2.2 — 垃圾回收 实战演示              ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        ReachabilityDemo.run();    // 演示1：对象存活判断
        ReferenceTypeDemo.run();   // 演示2：四种引用类型
        GCAlgorithmDemo.run();     // 演示3：GC 算法模拟
        GCMonitorDemo.run();       // 演示4：GC 监控

        System.out.println("\n===== 全部演示结束 =====");
    }
}
