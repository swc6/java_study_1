import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 1.1 — Java 内存模型 (JMM) 实战演示
 *
 * 涵盖内容：
 *   1.1.1 三大特性：原子性、可见性、有序性
 *   1.1.2 happens-before 原则
 *   1.1.3 volatile 的实现原理
 *
 * 编译：javac Demo1_1.java
 * 运行：java Demo1_1
 */
public class Demo1_1 {

    // ============================================================
    // 演示1：可见性问题 —— 没有 volatile 时线程看不到另一个线程的修改
    // ============================================================
    static class VisibilityDemo {

        // 不加 volatile：子线程可能永远看不到 flag 变成 false
        // 加上 volatile：子线程能立即感知到主线程的修改
        private static volatile boolean flag = true;

        public static void run() throws InterruptedException {
            System.out.println("===== 演示1：可见性 (volatile) =====");

            Thread reader = new Thread(() -> {
                long count = 0;
                // 当 flag 为 true 时持续空转
                while (flag) {
                    count++; // 空循环，不做IO，JIT可能优化掉对flag的读取
                }
                System.out.println("  reader 线程退出，循环次数: " + count);
            }, "reader");

            reader.start();

            Thread.sleep(1000); // 让 reader 先跑一会儿
            System.out.println("  主线程将 flag 设为 false ...");
            flag = false; // 通知 reader 退出

            reader.join(2000);
            if (reader.isAlive()) {
                System.out.println("  [结果] reader 仍在运行 —— 可见性缺失！");
                reader.interrupt();
            } else {
                System.out.println("  [结果] reader 已退出 —— volatile 保证了可见性");
            }
        }
    }

    // ============================================================
    // 演示2：原子性问题 —— volatile 不保证复合操作的原子性
    // ============================================================
    static class AtomicityDemo {

        // volatile 只保证可见性，不保证 count++ 的原子性
        private static volatile int count = 0;

        // AtomicInteger 通过 CAS 保证原子性
        private static final AtomicInteger atomicCount = new AtomicInteger(0);

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示2：原子性 (volatile vs AtomicInteger) =====");

            int threadCount = 10;
            int incrementsPerThread = 10000;
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                Thread t = new Thread(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        count++;                        // 非原子：读→加→写 三步
                        atomicCount.incrementAndGet();  // 原子：CAS
                    }
                    latch.countDown();
                }, "worker-" + i);
                t.start();
            }

            latch.await();

            int expected = threadCount * incrementsPerThread;
            System.out.println("  期望值:           " + expected);
            System.out.println("  volatile count:   " + count + " (通常 < 期望值，说明非原子)");
            System.out.println("  AtomicInteger:    " + atomicCount.get() + " (始终 = 期望值)");
        }
    }

    // ============================================================
    // 演示3：有序性 —— 指令重排序可能导致意外结果
    // ============================================================
    static class OrderingDemo {

        // 不加 volatile 可能发生指令重排：x=1 和 y=1 被重排
        private static int x = 0, y = 0;
        private static int a = 0, b = 0;

        // 加 volatile 禁止重排序
        private static volatile int x2 = 0, y2 = 0;
        private static volatile int a2 = 0, b2 = 0;

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示3：有序性 (指令重排序) =====");

            // --- 测试1：不使用 volatile ---
            int reorderingFound = 0;
            for (int i = 0; i < 100000; i++) {
                x = 0; y = 0; a = 0; b = 0;

                Thread t1 = new Thread(() -> {
                    a = 1;  // 先写 a
                    x = b;  // 再读 b
                });

                Thread t2 = new Thread(() -> {
                    b = 1;  // 先写 b
                    y = a;  // 再读 a
                });

                t1.start(); t2.start();
                t1.join();  t2.join();

                // 如果没有重排序，不可能出现 x==0 且 y==0
                if (x == 0 && y == 0) {
                    reorderingFound++;
                }
            }
            System.out.println("  [无volatile] 检测到指令重排序次数: " + reorderingFound
                    + " / 100000 (x==0 && y==0 说明 a=1/b=1 被重排了)");

            // --- 测试2：使用 volatile 禁止重排序 ---
            int reorderingFoundVolatile = 0;
            for (int i = 0; i < 100000; i++) {
                x2 = 0; y2 = 0; a2 = 0; b2 = 0;

                Thread t1 = new Thread(() -> {
                    a2 = 1;  // volatile 写，前面操作不会被重排到后面
                    x2 = b2;
                });

                Thread t2 = new Thread(() -> {
                    b2 = 1;
                    y2 = a2;
                });

                t1.start(); t2.start();
                t1.join();  t2.join();

                if (x2 == 0 && y2 == 0) {
                    reorderingFoundVolatile++;
                }
            }
            System.out.println("  [有volatile] 检测到指令重排序次数: " + reorderingFoundVolatile
                    + " / 100000 (volatile 禁止了重排序)");
        }
    }

    // ============================================================
    // 演示4：happens-before 规则验证
    // ============================================================
    static class HappensBeforeDemo {

        // volatile 变量写 happens-before 后续对该变量的读
        private static volatile boolean ready = false;
        private static int number = 0; // 普通变量

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示4：happens-before 规则 =====");

            // 场景：主线程先写 number，再写 volatile ready=true
            //       reader 线程先读 volatile ready，再读 number
            // 根据 happens-before 的 volatile 规则 + 传递性：
            //   number=42 happens-before ready=true (程序顺序规则)
            //   ready=true happens-before reader读到ready=true (volatile规则)
            //   => number=42 happens-before reader读number (传递性)
            // 所以 reader 读到的 number 一定是 42，不会是 0

            Thread reader = new Thread(() -> {
                while (!ready) {
                    // 等待 ready 变为 true
                    Thread.yield();
                }
                // 如果没有 happens-before 保证，这里可能读到 0
                System.out.println("  reader 读到 number = " + number
                        + " (happens-before 保证一定是 42)");
            }, "reader");

            reader.start();

            Thread.sleep(500);

            number = 42;        // 1. 写普通变量
            ready = true;       // 2. 写 volatile 变量（建立 happens-before 关系）

            reader.join();
            System.out.println("  [结果] volatile 写之前的所有写操作对读 volatile 后的读操作可见");
        }
    }

    // ============================================================
    // 演示5：volatile 不保证原子性的原理可视化
    // ============================================================
    static class VolatilePrincipleDemo {

        public static void run() {
            System.out.println("\n===== 演示5：volatile 实现原理说明 =====");

            System.out.println("  volatile 通过内存屏障(Memory Barrier)实现：");
            System.out.println("    1. Store-Barrier (写屏障): volatile写后，强制刷新到主内存");
            System.out.println("    2. Load-Barrier  (读屏障): volatile读前，强制从主内存重新加载");
            System.out.println("    3. 禁止指令重排序: 内存屏障作为重排边界");
            System.out.println();
            System.out.println("  count++ 不是原子操作，分3步：");
            System.out.println("    步骤1: 读取 count 的值到工作内存");
            System.out.println("    步骤2: 执行 count + 1");
            System.out.println("    步骤3: 将结果写回 count");
            System.out.println("  volatile 只保证步骤1读到最新值、步骤3立刻可见，");
            System.out.println("  但步骤1-2-3之间可能被其他线程插入，所以非原子。");
            System.out.println();
            System.out.println("  CAS (Compare-And-Swap) 保证原子性：");
            System.out.println("    while (!compareAndSet(oldValue, newValue)) { 重试 }");
            System.out.println("    CPU 指令 cmpxchg 保证比较和交换是一个不可分割的原子操作。");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 1.1 — Java 内存模型 (JMM) 实战演示   ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        VisibilityDemo.run();        // 演示1：可见性
        AtomicityDemo.run();         // 演示2：原子性
        OrderingDemo.run();          // 演示3：有序性
        HappensBeforeDemo.run();     // 演示4：happens-before
        VolatilePrincipleDemo.run(); // 演示5：原理说明

        System.out.println("\n===== 全部演示结束 =====");
    }
}
