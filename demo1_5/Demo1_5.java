import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 1.5 — 并发容器 实战演示
 *
 * 涵盖内容：
 *   1.5.1 ConcurrentHashMap 高并发 Map
 *   1.5.2 CopyOnWriteArrayList 写时复制列表
 *   1.5.3 BlockingQueue 生产者-消费者模型
 *   1.5.4 ConcurrentSkipListMap 有序并发 Map
 *
 * 编译：javac Demo1_5.java
 * 运行：java Demo1_5
 */
public class Demo1_5 {

    // ============================================================
    // 演示1：ConcurrentHashMap 高并发读写
    // ============================================================
    static class ConcurrentHashMapDemo {

        public static void run() throws InterruptedException {
            System.out.println("===== 演示1：ConcurrentHashMap 高并发读写 =====");

            ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);

            // 多线程并发写入
            for (int i = 0; i < threadCount; i++) {
                final int id = i;
                new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        map.put("key-" + id + "-" + j, j);
                    }
                    latch.countDown();
                }).start();
            }

            latch.await();

            System.out.println("  线程数: " + threadCount + "，每线程写1000条");
            System.out.println("  map.size() = " + map.size() + "（期望=" + (threadCount * 1000) + "）");

            // 原子操作
            map.put("counter", 0);
            map.compute("counter", (k, v) -> v + 100); // 原子更新
            map.merge("counter", 50, Integer::sum);      // 原子合并
            System.out.println("  compute + merge 后 counter = " + map.get("counter"));

            System.out.println("  [原理] JDK1.8: CAS写空桶 + synchronized锁链表头节点");

            // ConcurrentHashMap 不允许 null key/value
            try {
                map.put(null, 1);
            } catch (NullPointerException e) {
                System.out.println("  [注意] ConcurrentHashMap 不允许 null key/value");
            }
        }
    }

    // ============================================================
    // 演示2：CopyOnWriteArrayList 写时复制
    // ============================================================
    static class CopyOnWriteArrayListDemo {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示2：CopyOnWriteArrayList 写时复制 =====");

            CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
            CountDownLatch latch = new CountDownLatch(2);

            // 写线程
            Thread writer = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    list.add("item-" + i);
                    System.out.println("  写入: item-" + i + "，size=" + list.size());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                latch.countDown();
            }, "writer");

            // 读线程（遍历时不需要加锁，不会 ConcurrentModificationException）
            Thread reader = new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    System.out.println("  读线程遍历: " + list + "（快照读，不阻塞写）");
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                latch.countDown();
            }, "reader");

            writer.start();
            reader.start();
            latch.await();

            System.out.println("  最终: " + list);
            System.out.println("  [特点] 读无锁、写复制，适合读多写少场景");
        }
    }

    // ============================================================
    // 演示3：BlockingQueue 生产者-消费者
    // ============================================================
    static class BlockingQueueDemo {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示3：BlockingQueue 生产者-消费者模型 =====");

            // 有界阻塞队列，容量3
            ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(3);
            CountDownLatch latch = new CountDownLatch(2);

            // 生产者
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 6; i++) {
                        String item = "产品" + i;
                        queue.put(item); // 队列满时自动阻塞
                        System.out.println("  生产: " + item + "，队列大小=" + queue.size());
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }, "producer");

            // 消费者
            Thread consumer = new Thread(() -> {
                try {
                    for (int i = 0; i < 6; i++) {
                        Thread.sleep(500); // 消费比生产慢
                        String item = queue.take(); // 队列空时自动阻塞
                        System.out.println("          消费: " + item);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }, "consumer");

            producer.start();
            consumer.start();
            latch.await();

            System.out.println("  [结果] BlockingQueue 自动处理阻塞，无需手动 wait/notify");
        }
    }

    // ============================================================
    // 演示4：ConcurrentSkipListMap 有序并发 Map
    // ============================================================
    static class ConcurrentSkipListMapDemo {

        public static void run() throws InterruptedException {
            System.out.println("\n===== 演示4：ConcurrentSkipListMap 有序并发 Map =====");

            ConcurrentSkipListMap<Integer, String> skipMap = new ConcurrentSkipListMap<>();
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);

            // 多线程并发写入
            for (int i = 0; i < threadCount; i++) {
                final int id = i;
                new Thread(() -> {
                    for (int j = 0; j < 5; j++) {
                        int key = id * 10 + j;
                        skipMap.put(key, "value-" + key);
                    }
                    latch.countDown();
                }).start();
            }

            latch.await();

            System.out.println("  并发写入 " + skipMap.size() + " 个元素，自动按 key 排序:");
            System.out.println("  " + skipMap);

            // 有序操作
            System.out.println("\n  firstKey() = " + skipMap.firstKey());
            System.out.println("  lastKey()  = " + skipMap.lastKey());
            System.out.println("  headMap(20) = " + skipMap.headMap(20));
            System.out.println("  tailMap(20) = " + skipMap.tailMap(20));
            System.out.println("  subMap(10, 30) = " + skipMap.subMap(10, 30));

            System.out.println("  [原理] 跳表(SkipList) + CAS，无锁并发排序");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║       Demo 1.5 — 并发容器实战演示            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        ConcurrentHashMapDemo.run();         // 演示1：ConcurrentHashMap
        CopyOnWriteArrayListDemo.run();      // 演示2：CopyOnWriteArrayList
        BlockingQueueDemo.run();             // 演示3：BlockingQueue
        ConcurrentSkipListMapDemo.run();     // 演示4：ConcurrentSkipListMap

        System.out.println("\n===== 全部演示结束 =====");
    }
}
