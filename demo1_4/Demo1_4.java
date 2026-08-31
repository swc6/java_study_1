import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Demo 1.4 — AQS 原理实战演示
 *
 * 涵盖内容：
 *   1.4.1 Semaphore 信号量（共享式同步）
 *   1.4.2 CountDownLatch 倒计时闩
 *   1.4.3 CyclicBarrier 循环屏障
 *   1.4.4 自定义 AQS 同步器（独占锁实现）
 *
 * 编译：javac Demo1_4.java
 * 运行：java Demo1_4
 */
public class Demo1_4 {

    // ============================================================
    // 演示1：Semaphore 信号量（共享式，控制并发访问数）
    // ============================================================
    static class SemaphoreDemo {

        public static void run() throws InterruptedException {
            System.out.println("===== 演示1：Semaphore 信号量 =====");

            // 3个许可：同时最多3个线程访问
            Semaphore semaphore = new Semaphore(3);
            int threadCount = 6;
            CountDownLatch latch = new CountDownLatch(threadCount);

            System.out.println("  许可数: 3，线程数: " + threadCount + "（最多3个同时执行）");

            for (int i = 1; i <= threadCount; i++) {
                final int id = i;
                new Thread(() -> {
                    try {
                        System.out.println("  线程" + id + " 等待许可...");
                        semaphore.acquire(); // 获取许可
                        System.out.println("  线程" + id + " 获取许可，开始执行");
                        Thread.sleep(500);  // 模拟任务
                        System.out.println("  线程" + id + " 释放许可");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release(); // 释放许可
                        latch.countDown();
                    }
                }).start();
                Thread.sleep(100); // 错开启动
            }

            latch.await();
            System.out.println("  [结果] Semaphore 通过共享模式控制并发数");
        }
    }

    // ============================================================
    // 演示2：CountDownLatch 倒计时闩
    // ============================================================
    static class CountDownLatchDemo {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示2：CountDownLatch 倒计时闩 =====");

            int taskCount = 3;
            CountDownLatch latch = new CountDownLatch(taskCount);

            System.out.println("  主线程等待 " + taskCount + " 个子任务完成...");

            for (int i = 1; i <= taskCount; i++) {
                final int id = i;
                new Thread(() -> {
                    try {
                        System.out.println("  任务" + id + " 执行中...");
                        Thread.sleep(500 * id);
                        System.out.println("  任务" + id + " 完成！");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown(); // 计数减1
                    }
                }).start();
            }

            latch.await(); // 等待计数归零
            System.out.println("  所有任务完成，主线程继续执行");
            System.out.println("  [结果] CountDownLatch 不可重置，一次性使用");
        }
    }

    // ============================================================
    // 演示3：CyclicBarrier 循环屏障
    // ============================================================
    static class CyclicBarrierDemo {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示3：CyclicBarrier 循环屏障 =====");

            int parties = 3;
            CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
                // 所有线程到达屏障后执行
                System.out.println("  >> 屏障触发：所有线程到达，继续执行 <<");
            });

            for (int round = 1; round <= 2; round++) {
                final int r = round;
                System.out.println("\n  --- 第 " + r + " 轮 ---");

                for (int i = 1; i <= parties; i++) {
                    final int id = i;
                    new Thread(() -> {
                        try {
                            System.out.println("  线程" + id + " 到达屏障（第" + r + "轮）");
                            barrier.await(); // 等待其他线程
                            System.out.println("  线程" + id + " 通过屏障");
                        } catch (Exception e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                    Thread.sleep(200);
                }
                Thread.sleep(1000);
            }

            System.out.println("  [结果] CyclicBarrier 可重置，支持循环使用");
        }
    }

    // ============================================================
    // 演示4：自定义 AQS 同步器（独占锁实现）
    // ============================================================

    /**
     * 基于AQS实现的自定义互斥锁（独占模式）
     * state=0 未锁定，state=1 已锁定
     */
    static class SimpleLock extends AbstractQueuedSynchronizer {

        // 尝试获取锁
        @Override
        protected boolean tryAcquire(int arg) {
            // CAS 将 state 从 0 改为 1
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread()); // 记录持有线程
                return true;
            }
            return false;
        }

        // 尝试释放锁
        @Override
        protected boolean tryRelease(int arg) {
            if (getState() == 0) {
                throw new IllegalMonitorStateException("未持有锁");
            }
            setExclusiveOwnerThread(null);
            setState(0); // 不需要CAS，只有持有锁的线程才能释放
            return true;
        }

        @Override
        protected boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        public void lock() {
            acquire(1);
        }

        public void unlock() {
            release(1);
        }

        public boolean isLocked() {
            return getState() == 1;
        }
    }

    static class CustomAQLDemo {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示4：自定义 AQS 同步器（独占锁） =====");

            SimpleLock lock = new SimpleLock();
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            int[] sharedCounter = {0};

            System.out.println("  使用自定义 AQS 锁保护共享变量:");

            for (int i = 1; i <= threadCount; i++) {
                final int id = i;
                new Thread(() -> {
                    try {
                        lock.lock();
                        System.out.println("  线程" + id + " 获取锁");
                        int temp = sharedCounter[0];
                        Thread.sleep(10); // 制造竞争窗口
                        sharedCounter[0] = temp + 1;
                        System.out.println("  线程" + id + " count=" + sharedCounter[0]);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        lock.unlock();
                    }
                    latch.countDown();
                }).start();
            }

            latch.await();
            System.out.println("  最终 count=" + sharedCounter[0] + "（期望=" + threadCount + "）");
            System.out.println("  [结果] 自定义AQS通过 state + CLH队列实现独占锁");

            // AQS 核心原理说明
            System.out.println("\n  AQS 核心设计:");
            System.out.println("    1. volatile int state — 同步状态");
            System.out.println("       独占: 0=未锁, >0=锁定次数(可重入)");
            System.out.println("       共享: 剩余许可数");
            System.out.println("    2. CLH 双向队列 — 等待线程排队");
            System.out.println("    3. 模板方法模式: acquire/release 调度 tryAcquire/tryRelease");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║        Demo 1.4 — AQS 原理实战演示           ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        SemaphoreDemo.run();       // 演示1：Semaphore
        CountDownLatchDemo.run();  // 演示2：CountDownLatch
        CyclicBarrierDemo.run();   // 演示3：CyclicBarrier
        CustomAQLDemo.run();       // 演示4：自定义AQS同步器

        System.out.println("\n===== 全部演示结束 =====");
    }
}
