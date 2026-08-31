import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

/**
 * Demo 1.2 — synchronized 与 Lock 实战演示
 *
 * 涵盖内容：
 *   1.2.1 synchronized 三种用法 + 可重入性
 *   1.2.2 ReentrantLock 基本用法
 *   1.2.3 tryLock 超时获取
 *   1.2.4 lockInterruptibly 可中断锁
 *   1.2.5 Condition 多条件变量
 *
 * 编译：javac Demo1_2.java
 * 运行：java Demo1_2
 */
public class Demo1_2 {

    // ============================================================
    // 演示1：synchronized 三种用法
    // ============================================================
    static class SynchronizedUsageDemo {

        private final Object lock = new Object();
        private int count = 0;

        // 用法1：同步实例方法，锁是 this
        public synchronized void instanceMethod() {
            count++;
        }

        // 用法2：同步静态方法，锁是 Class 对象
        public static synchronized void staticMethod() {
            // System.out.println("静态同步方法，锁是 Demo1_2.class");
        }

        // 用法3：同步代码块，锁是指定对象
        public void blockMethod() {
            synchronized (lock) {
                count++;
            }
        }

        public static void run() throws InterruptedException {
            System.out.println("===== 演示1：synchronized 三种用法 =====");

            SynchronizedUsageDemo demo = new SynchronizedUsageDemo();
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 10000; j++) {
                        demo.instanceMethod();
                    }
                });
                threads[i].start();
            }

            for (Thread t : threads) t.join();

            System.out.println("  期望值: " + (threadCount * 10000));
            System.out.println("  实际值: " + demo.count + " (synchronized 保证线程安全)");
        }
    }

    // ============================================================
    // 演示2：synchronized 可重入性
    // ============================================================
    static class ReentrantDemo {

        public synchronized void methodA() {
            System.out.println("  进入 methodA，持有锁");
            methodB(); // 同一线程再次获取同一把锁
            System.out.println("  退出 methodA");
        }

        public synchronized void methodB() {
            System.out.println("  进入 methodB（可重入获取锁成功）");
        }

        public static void run() {
            System.out.println("\n===== 演示2：synchronized 可重入性 =====");
            ReentrantDemo demo = new ReentrantDemo();
            demo.methodA();
            System.out.println("  [结果] synchronized 是可重入锁，methodA 内调用 methodB 不会死锁");
        }
    }

    // ============================================================
    // 演示3：ReentrantLock 基本用法
    // ============================================================
    static class ReentrantLockDemo {

        private final ReentrantLock lock = new ReentrantLock();
        private int count = 0;

        public void increment() {
            lock.lock();
            try {
                count++;
            } finally {
                lock.unlock(); // 必须在 finally 中释放
            }
        }

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示3：ReentrantLock 基本用法 =====");

            ReentrantLockDemo demo = new ReentrantLockDemo();
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 10000; j++) {
                        demo.increment();
                    }
                });
                threads[i].start();
            }

            for (Thread t : threads) t.join();

            System.out.println("  期望值: " + (threadCount * 10000));
            System.out.println("  实际值: " + demo.count + " (ReentrantLock 保证线程安全)");
        }
    }

    // ============================================================
    // 演示4：tryLock 超时获取
    // ============================================================
    static class TryLockDemo {

        private final ReentrantLock lock = new ReentrantLock();

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示4：tryLock 超时获取 =====");

            TryLockDemo demo = new TryLockDemo();

            // 线程1 持有锁 2 秒
            Thread t1 = new Thread(() -> {
                demo.lock.lock();
                try {
                    System.out.println("  t1 获取锁，持有 2 秒");
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    demo.lock.unlock();
                    System.out.println("  t1 释放锁");
                }
            }, "t1");

            // 线程2 尝试在 1 秒内获取锁（会失败）
            Thread t2 = new Thread(() -> {
                try {
                    System.out.println("  t2 尝试在 1 秒内获取锁 ...");
                    boolean acquired = demo.lock.tryLock(1, TimeUnit.SECONDS);
                    if (acquired) {
                        try {
                            System.out.println("  t2 获取锁成功");
                        } finally {
                            demo.lock.unlock();
                        }
                    } else {
                        System.out.println("  t2 获取锁失败（超时），不会一直阻塞");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "t2");

            t1.start();
            Thread.sleep(100);
            t2.start();

            t1.join();
            t2.join();
            System.out.println("  [结果] tryLock 支持超时，避免无限等待");
        }
    }

    // ============================================================
    // 演示5：lockInterruptibly 可中断锁
    // ============================================================
    static class InterruptibleLockDemo {

        private final ReentrantLock lock = new ReentrantLock();

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示5：lockInterruptibly 可中断锁 =====");

            InterruptibleLockDemo demo = new InterruptibleLockDemo();

            // 线程1 持有锁不释放
            Thread t1 = new Thread(() -> {
                demo.lock.lock();
                try {
                    System.out.println("  t1 获取锁，长期持有");
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    demo.lock.unlock();
                }
            }, "t1");

            // 线程2 使用 lockInterruptibly，可被中断
            Thread t2 = new Thread(() -> {
                try {
                    System.out.println("  t2 等待获取锁（lockInterruptibly）...");
                    demo.lock.lockInterruptibly();
                    try {
                        System.out.println("  t2 获取锁成功");
                    } finally {
                        demo.lock.unlock();
                    }
                } catch (InterruptedException e) {
                    System.out.println("  t2 被中断，放弃获取锁（synchronized 做不到这点）");
                }
            }, "t2");

            t1.start();
            Thread.sleep(100);
            t2.start();

            Thread.sleep(1000);
            t2.interrupt(); // 中断 t2 的锁等待

            t2.join(2000);
            t1.interrupt();
            t1.join(2000);
            System.out.println("  [结果] lockInterruptibly 允许在等待锁时响应中断");
        }
    }

    // ============================================================
    // 演示6：Condition 多条件变量
    // ============================================================
    static class ConditionDemo {

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        private final int[] buffer = new int[5];
        private int count = 0;
        private int putIndex = 0;
        private int takeIndex = 0;

        public void put(int value) throws InterruptedException {
            lock.lock();
            try {
                while (count == buffer.length) {
                    notFull.await(); // 队列满，等待 notFull 条件
                }
                buffer[putIndex] = value;
                putIndex = (putIndex + 1) % buffer.length;
                count++;
                notEmpty.signal(); // 通知消费者
            } finally {
                lock.unlock();
            }
        }

        public int take() throws InterruptedException {
            lock.lock();
            try {
                while (count == 0) {
                    notEmpty.await(); // 队列空，等待 notEmpty 条件
                }
                int value = buffer[takeIndex];
                takeIndex = (takeIndex + 1) % buffer.length;
                count--;
                notFull.signal(); // 通知生产者
                return value;
            } finally {
                lock.unlock();
            }
        }

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示6：Condition 多条件变量（生产者-消费者） =====");

            ConditionDemo queue = new ConditionDemo();

            // 生产者
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 10; i++) {
                        queue.put(i);
                        System.out.println("  生产: " + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "producer");

            // 消费者
            Thread consumer = new Thread(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        int value = queue.take();
                        System.out.println("          消费: " + value);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "consumer");

            producer.start();
            consumer.start();
            producer.join();
            consumer.join();
            System.out.println("  [结果] Condition 实现了多条件变量，比 wait/notify 更灵活");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  Demo 1.2 — synchronized 与 Lock 实战演示   ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        SynchronizedUsageDemo.run();    // 演示1：synchronized 三种用法
        ReentrantDemo.run();            // 演示2：可重入性
        ReentrantLockDemo.run();        // 演示3：ReentrantLock 基本用法
        TryLockDemo.run();              // 演示4：tryLock 超时
        InterruptibleLockDemo.run();    // 演示5：lockInterruptibly
        ConditionDemo.run();            // 演示6：Condition

        System.out.println("\n===== 全部演示结束 =====");
    }
}
