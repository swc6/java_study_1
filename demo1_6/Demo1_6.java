import java.util.concurrent.*;

/**
 * Demo 1.6 — 线程池 实战演示
 *
 * 涵盖内容：
 *   1.6.1 ThreadPoolExecutor 七大参数 + 任务提交流程
 *   1.6.2 四种拒绝策略对比
 *   1.6.3 自定义线程池
 *   1.6.4 Fork/Join 框架
 *
 * 编译：javac Demo1_6.java
 * 运行：java Demo1_6
 */
public class Demo1_6 {

    // ============================================================
    // 演示1：ThreadPoolExecutor 七大参数 + 任务提交流程
    // ============================================================
    static class ThreadPoolExecutorDemo {

        public static void run() throws InterruptedException {
            System.out.println("===== 演示1：ThreadPoolExecutor 七大参数 + 提交流程 =====");

            int coreSize = 2;
            int maxSize = 4;
            int queueCapacity = 2;

            // 七大参数说明
            System.out.println("  参数配置:");
            System.out.println("    corePoolSize    = " + coreSize + " (核心线程数)");
            System.out.println("    maximumPoolSize = " + maxSize + " (最大线程数)");
            System.out.println("    keepAliveTime   = 5秒 (非核心线程空闲存活时间)");
            System.out.println("    workQueue       = ArrayBlockingQueue(" + queueCapacity + ")");
            System.out.println("    threadFactory   = 自定义(带命名)");
            System.out.println("    handler         = AbortPolicy(默认)");

            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    coreSize,
                    maxSize,
                    5L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(queueCapacity),
                    new ThreadFactory() {
                        private final java.util.concurrent.atomic.AtomicInteger num =
                                new java.util.concurrent.atomic.AtomicInteger(1);

                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "pool-worker-" + num.getAndIncrement());
                            System.out.println("  [创建新线程] " + t.getName());
                            return t;
                        }
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );

            System.out.println("\n  提交 " + (maxSize + queueCapacity) + " 个任务:");
            System.out.println("  流程: 核心线程(2) → 队列(2) → 非核心线程(2) → 拒绝策略\n");

            // 提交6个任务：2核心+2队列+2非核心 = 6（恰好不触发拒绝）
            for (int i = 1; i <= 6; i++) {
                final int taskId = i;
                try {
                    pool.execute(() -> {
                        System.out.println("  任务" + taskId + " 由 " + Thread.currentThread().getName() + " 执行");
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    System.out.println("  提交任务" + taskId
                            + " → 活跃=" + pool.getActiveCount()
                            + ", 队列=" + pool.getQueue().size()
                            + ", 池大小=" + pool.getPoolSize());
                } catch (RejectedExecutionException e) {
                    System.out.println("  任务" + taskId + " 被拒绝！(" + e.getClass().getSimpleName() + ")");
                }
                Thread.sleep(50);
            }

            // 等待所有任务完成
            Thread.sleep(2000);
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            System.out.println("\n  [流程总结]");
            System.out.println("    1. 核心线程未满 → 创建核心线程");
            System.out.println("    2. 核心线程满 → 入队等待");
            System.out.println("    3. 队列满 → 创建非核心线程(不超过maximumPoolSize)");
            System.out.println("    4. 最大线程满+队列满 → 执行拒绝策略");
        }
    }

    // ============================================================
    // 演示2：四种拒绝策略对比
    // ============================================================
    static class RejectionPolicyDemo {

        public static void runPolicy(String name, java.util.concurrent.RejectedExecutionHandler handler)
                throws InterruptedException {

            System.out.println("\n  --- " + name + " ---");

            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    1, 1,
                    0L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(1),
                    handler
            );

            // 提交4个任务：1执行 + 1队列 + 2被拒绝
            for (int i = 1; i <= 4; i++) {
                final int taskId = i;
                try {
                    pool.execute(() -> {
                        System.out.println("    " + name + ": 任务" + taskId + " 由 "
                                + Thread.currentThread().getName() + " 执行");
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } catch (RejectedExecutionException e) {
                    System.out.println("    " + name + ": 任务" + taskId + " 被拒绝(抛异常)");
                }
            }

            Thread.sleep(1500);
            pool.shutdown();
            pool.awaitTermination(3, TimeUnit.SECONDS);
        }

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示2：四种拒绝策略对比 =====");
            System.out.println("  线程池: core=1, max=1, queue=1 → 提交4个任务，2个会被拒绝");

            runPolicy("AbortPolicy", new ThreadPoolExecutor.AbortPolicy());
            runPolicy("CallerRunsPolicy", new ThreadPoolExecutor.CallerRunsPolicy());
            runPolicy("DiscardPolicy", new ThreadPoolExecutor.DiscardPolicy());
            runPolicy("DiscardOldestPolicy", new ThreadPoolExecutor.DiscardOldestPolicy());

            System.out.println("\n  [总结]");
            System.out.println("    AbortPolicy      — 抛异常（默认，快速发现问题）");
            System.out.println("    CallerRunsPolicy — 调用者线程执行（降压，不丢任务）");
            System.out.println("    DiscardPolicy    — 静默丢弃新任务");
            System.out.println("    DiscardOldestPolicy — 丢弃队列最旧任务，重试新任务");
        }
    }

    // ============================================================
    // 演示3：Fork/Join 框架
    // ============================================================

    /**
     * 递归计算 1~n 的和，演示 Fork/Join 分治
     */
    static class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 10000; // 阈值
        private final long start;
        private final long end;

        SumTask(long start, long end) {
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            long length = end - start;
            if (length <= THRESHOLD) {
                // 任务足够小，直接计算
                long sum = 0;
                for (long i = start; i <= end; i++) {
                    sum += i;
                }
                return sum;
            }

            // 分裂为两个子任务
            long mid = (start + end) / 2;
            SumTask left = new SumTask(start, mid);
            SumTask right = new SumTask(mid + 1, end);

            // 并行执行
            left.fork();   // 异步执行左任务
            Long rightResult = right.compute(); // 同步执行右任务
            Long leftResult = left.join();      // 等待左任务完成

            return leftResult + rightResult;
        }
    }

    static class ForkJoinDemo {

        public static void run() {
            System.out.println("\n===== 演示3：Fork/Join 框架 =====");

            long n = 100_000_000L; // 1到1亿求和

            // Fork/Join 方式
            ForkJoinPool pool = new ForkJoinPool();
            long start1 = System.nanoTime();
            Long result = pool.invoke(new SumTask(1, n));
            long time1 = (System.nanoTime() - start1) / 1_000_000;
            pool.shutdown();

            // 单线程方式对比
            long start2 = System.nanoTime();
            long singleResult = 0;
            for (long i = 1; i <= n; i++) {
                singleResult += i;
            }
            long time2 = (System.nanoTime() - start2) / 1_000_000;

            System.out.println("  计算 1 到 " + n + " 的和:");
            System.out.println("  Fork/Join: 结果=" + result + ", 耗时=" + time1 + "ms");
            System.out.println("  单线程:    结果=" + singleResult + ", 耗时=" + time2 + "ms");
            System.out.println("  [原理] 大任务拆分为小任务(THRESHOLD=" + 10000 + ")，工作窃取调度");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║         Demo 1.6 — 线程池实战演示            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        ThreadPoolExecutorDemo.run();     // 演示1：七大参数+提交流程
        RejectionPolicyDemo.run();        // 演示2：四种拒绝策略
        ForkJoinDemo.run();               // 演示3：Fork/Join

        System.out.println("\n===== 全部演示结束 =====");
    }
}
