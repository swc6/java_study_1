import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Demo 1.3 — CAS 与原子类 实战演示
 *
 * 涵盖内容：
 *   1.3.1 CAS 原理（手动模拟）
 *   1.3.2 原子类基本操作
 *   1.3.3 ABA 问题演示与解决
 *   1.3.4 LongAdder vs AtomicLong 性能对比
 *
 * 编译：javac Demo1_3.java
 * 运行：java Demo1_3
 */
public class Demo1_3 {

    // ============================================================
    // 演示1：CAS 原理模拟（手动实现 compareAndSet）
    // ============================================================
    static class CASPrincipleDemo {

        // 模拟 AtomicInteger 的核心逻辑
        private volatile int value;

        public CASPrincipleDemo(int initialValue) {
            this.value = initialValue;
        }

        public int get() {
            return value;
        }

        /**
         * 模拟 CAS 操作（实际 AtomicInteger 使用 Unsafe.compareAndSwapInt）
         * 这里用 synchronized 模拟原子性，仅用于教学演示
         */
        public synchronized boolean compareAndSet(int expect, int update) {
            if (value == expect) {   // 比较：当前值是否等于期望值
                value = update;       // 交换：更新为新值
                return true;
            }
            return false;
        }

        /**
         * 模拟 incrementAndGet：CAS 自旋
         */
        public int incrementAndGet() {
            int oldValue;
            int newValue;
            do {
                oldValue = get();        // 步骤1：读取当前值
                newValue = oldValue + 1;  // 步骤2：计算新值
            } while (!compareAndSet(oldValue, newValue)); // 步骤3：CAS，失败则重试
            return newValue;
        }

        public static void run() throws InterruptedException {
            System.out.println("===== 演示1：CAS 原理模拟 =====");

            CASPrincipleDemo cas = new CASPrincipleDemo(0);
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    for (int j = 0; j < 10000; j++) {
                        cas.incrementAndGet();
                    }
                    latch.countDown();
                }).start();
            }

            latch.await();

            int expected = threadCount * 10000;
            System.out.println("  期望值: " + expected);
            System.out.println("  实际值: " + cas.get());
            System.out.println("  [原理] CAS = 比较(expect) + 交换(update)，失败则自旋重试");
        }
    }

    // ============================================================
    // 演示2：原子类基本操作
    // ============================================================
    static class AtomicClassDemo {

        public static void run() {
            System.out.println("\n===== 演示2：原子类基本操作 =====");

            // AtomicInteger
            AtomicInteger ai = new AtomicInteger(10);
            System.out.println("  AtomicInteger 初始值: " + ai.get());
            System.out.println("  getAndIncrement(): " + ai.getAndIncrement() + " (返回旧值)");
            System.out.println("  incrementAndGet(): " + ai.incrementAndGet() + " (返回新值)");
            System.out.println("  getAndAdd(5): " + ai.getAndAdd(5) + " (返回旧值)");
            System.out.println("  addAndGet(3): " + ai.addAndGet(3) + " (返回新值)");
            System.out.println("  compareAndSet(20, 100): " + ai.compareAndSet(20, 100));
            System.out.println("  compareAndSet(" + ai.get() + ", 100): " + ai.compareAndSet(ai.get(), 100));
            System.out.println("  当前值: " + ai.get());

            // AtomicReference
            AtomicReference<String> ref = new AtomicReference<>("hello");
            System.out.println("\n  AtomicReference 初始值: " + ref.get());
            ref.compareAndSet("hello", "world");
            System.out.println("  CAS(hello→world)后: " + ref.get());
            ref.set("java");
            System.out.println("  set(\"java\"): " + ref.get());

            // AtomicBoolean
            java.util.concurrent.atomic.AtomicBoolean ab =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            System.out.println("\n  AtomicBoolean: " + ab.get());
            System.out.println("  compareAndSet(false, true): " + ab.compareAndSet(false, true));
            System.out.println("  当前值: " + ab.get());
        }
    }

    // ============================================================
    // 演示3：ABA 问题演示与解决
    // ============================================================
    static class ABAProblemDemo {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 景示3：ABA 问题演示与解决 =====");

            // --- 模拟 ABA 问题 ---
            System.out.println("\n  --- ABA 问题：AtomicInteger 无法检测 A→B→A ---");

            AtomicInteger atomicInt = new AtomicInteger(100);

            // 线程1：准备将 100 改为 200
            Thread t1 = new Thread(() -> {
                try {
                    Thread.sleep(100); // 等线程2先改
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                boolean success = atomicInt.compareAndSet(100, 200);
                System.out.println("  线程1: CAS(100→200) = " + success
                        + "，当前值=" + atomicInt.get());
                System.out.println("  [问题] 线程1以为值没变过，实际已经被线程2改过 A→B→A");
            }, "线程1");

            // 线程2：将 100→110→100（制造 ABA）
            Thread t2 = new Thread(() -> {
                atomicInt.compareAndSet(100, 110);
                System.out.println("  线程2: CAS(100→110)，当前值=" + atomicInt.get());
                atomicInt.compareAndSet(110, 100);
                System.out.println("  线程2: CAS(110→100)，当前值=" + atomicInt.get());
            }, "线程2");

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            // --- 使用 AtomicStampedReference 解决 ABA ---
            System.out.println("\n  --- 解决方案：AtomicStampedReference（带版本号） ---");

            AtomicStampedReference<Integer> stampedRef =
                    new AtomicStampedReference<>(100, 0); // 初始值100，版本号0

            int stamp1 = stampedRef.getStamp();
            Integer ref1 = stampedRef.getReference();
            System.out.println("  初始: 值=" + ref1 + ", 版本号=" + stamp1);

            // 模拟 A→B→A，每次修改版本号递增
            stampedRef.compareAndSet(100, 110, stamp1, stamp1 + 1);
            System.out.println("  修改1: 值=" + stampedRef.getReference()
                    + ", 版本号=" + stampedRef.getStamp());

            int stamp2 = stampedRef.getStamp();
            stampedRef.compareAndSet(110, 100, stamp2, stamp2 + 1);
            System.out.println("  修改2: 值=" + stampedRef.getReference()
                    + ", 版本号=" + stampedRef.getStamp());

            // 此时值回到100，但版本号已经是2，CAS 会失败
            boolean success = stampedRef.compareAndSet(100, 200, stamp1, stamp1 + 1);
            System.out.println("  用旧版本号 CAS(100→200) = " + success
                    + "（版本号不匹配，CAS 失败，检测到了 ABA）");

            // 用正确版本号才能成功
            int currentStamp = stampedRef.getStamp();
            success = stampedRef.compareAndSet(100, 200, currentStamp, currentStamp + 1);
            System.out.println("  用正确版本号 CAS(100→200) = " + success);
        }
    }

    // ============================================================
    // 演示4：LongAdder vs AtomicLong 性能对比
    // ============================================================
    static class LongAdderVsAtomicLong {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示4：LongAdder vs AtomicLong 性能对比 =====");

            int threadCount = 10;
            int incrementsPerThread = 1000000;

            // --- AtomicLong ---
            AtomicLong atomicLong = new AtomicLong(0);
            CountDownLatch latch1 = new CountDownLatch(threadCount);
            long start1 = System.nanoTime();

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        atomicLong.incrementAndGet();
                    }
                    latch1.countDown();
                }).start();
            }
            latch1.await();
            long time1 = (System.nanoTime() - start1) / 1_000_000;

            // --- LongAdder ---
            LongAdder longAdder = new LongAdder();
            CountDownLatch latch2 = new CountDownLatch(threadCount);
            long start2 = System.nanoTime();

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        longAdder.increment();
                    }
                    latch2.countDown();
                }).start();
            }
            latch2.await();
            long time2 = (System.nanoTime() - start2) / 1_000_000;

            long expected = (long) threadCount * incrementsPerThread;
            System.out.println("  线程数: " + threadCount + "，每线程累加: " + incrementsPerThread);
            System.out.println("  期望值: " + expected);
            System.out.println("  AtomicLong: 值=" + atomicLong.get() + ", 耗时=" + time1 + "ms");
            System.out.println("  LongAdder:  值=" + longAdder.sum() + ", 耗时=" + time2 + "ms");
            System.out.println("  [结论] LongAdder 通过分段CAS减少竞争，高并发下性能更优");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 1.3 — CAS 与原子类 实战演示          ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        CASPrincipleDemo.run();        // 演示1：CAS 原理模拟
        AtomicClassDemo.run();         // 演示2：原子类基本操作
        ABAProblemDemo.run();          // 演示3：ABA 问题
        LongAdderVsAtomicLong.run();   // 演示4：性能对比

        System.out.println("\n===== 全部演示结束 =====");
    }
}
