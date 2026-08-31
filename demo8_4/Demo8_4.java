import java.util.ArrayList;
import java.util.List;

/**
 * Demo 8.4 — JIT 编译与调优 实战演示
 *
 * 涵盖内容：
 *   8.4.1 JIT 编译基础（解释执行 → 编译执行，热点探测）
 *   8.4.2 逃逸分析（栈上分配 / 标量替换 / 锁消除）
 *   8.4.3 方法内联（小方法被内联后无方法调用开销）
 *   8.4.4 循环优化（循环展开 / 不变代码外提）
 *
 * 说明：
 *   - 不依赖第三方库
 *   - JIT 行为受 JVM 参数影响，建议运行：
 *       java -XX:+PrintCompilation -XX:+UnlockDiagnosticVMOptions
 *            -XX:+PrintInlining -XX:+DoEscapeAnalysis Demo8_4
 *
 * 编译：javac Demo8_4.java
 * 运行：java Demo8_4
 */
public class Demo8_4 {

    private static final int ITER = 50_000_000; // 足够多次以触发 JIT

    // ============================================================
    // 演示1：JIT 热点探测 —— 方法被调用足够多次后编译
    // ============================================================
    static class HotspotDemo {

        static int hot(int a, int b) { return a + b; }

        public static void run() {
            System.out.println("===== 漓示1：JIT 热点探测 =====");

            // 解释执行阶段慢；超过 CompileThreshold 后被编译，速度提升
            long t1 = System.nanoTime();
            int s = 0;
            for (int i = 0; i < ITER; i++) s += hot(i, i);
            long dur = System.nanoTime() - t1;
            System.out.println("  hot() 调用 " + ITER + " 次: " + (dur / 1_000_000) + " ms  sum=" + s);

            System.out.println("  [结论]");
            System.out.println("    - HotSpot 用方法调用计数 + 回边计数判定热点");
            System.out.println("    - -XX:CompileThreshold 设置编译阈值（C2 默认约 10000）");
            System.out.println("    - -XX:+PrintCompilation 打印编译日志（含方法名）");
            System.out.println("    - 编译等级：0 解释执行 → 1 (C1) → 2 (C2)");
        }
    }

    // ============================================================
    // 漓示2：逃逸分析 —— 栈上分配 / 锁消除
    // ============================================================
    static class EscapeDemo {

        static class Point {
            int x, y;
            Point(int x, int y) { this.x = x; this.y = y; }
        }

        // 对象未逃逸出方法 → JIT 可栈上分配或标量替换
        static int sumNoEscape(int n) {
            int s = 0;
            for (int i = 0; i < n; i++) {
                Point p = new Point(i, i); // 局部对象，未逃逸
                s += p.x + p.y;
            }
            return s;
        }

        // 对象逃逸出方法（返回） → 必须堆分配
        static List<Point> produceEscape(int n) {
            List<Point> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(new Point(i, i));
            return list;
        }

        // 锁消除：StringBuffer 的同步锁在单线程未逃逸时被消除
        static String bufferConcat(int n) {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < n; i++) sb.append(i);
            return sb.toString();
        }

        public static void run() {
            System.out.println("\n===== 漓示2：逃逸分析 =====");

            // 预热
            for (int i = 0; i < 10_000; i++) {
                sumNoEscape(100);
                produceEscape(10);
                bufferConcat(100);
            }

            long t1 = System.nanoTime();
            int s = sumNoEscape(ITER);
            long d1 = System.nanoTime() - t1;
            System.out.println("  未逃逸 sumNoEscape: " + (d1 / 1_000_000) + " ms  sum=" + s);

            long t2 = System.nanoTime();
            List<Point> list = produceEscape(1_000_000);
            long d2 = System.nanoTime() - t2;
            System.out.println("  逃逸 produceEscape(1M): " + (d2 / 1_000_000) + " ms  size=" + list.size());

            long t3 = System.nanoTime();
            String r = bufferConcat(1_000_000);
            long d3 = System.nanoTime() - t3;
            System.out.println("  StringBuffer 锁消除: " + (d3 / 1_000_000) + " ms  len=" + r.length());

            System.out.println("  [结论]");
            System.out.println("    - 逃逸分析：分析对象作用域是否超出方法/线程");
            System.out.println("    - 栈上分配：未逃逸出方法 → 对象分配在栈，方法结束自动回收");
            System.out.println("    - 标量替换：把对象字段拆为局部变量，避免整体分配");
            System.out.println("    - 锁消除：对象未逃逸出线程 → 同步锁被消除");
            System.out.println("    - -XX:+DoEscapeAnalysis 默认开启");
        }
    }

    // ============================================================
    // 漓示3：方法内联
    // ============================================================
    static class InlineDemo {

        // 小方法：体积小，常被内联
        static int add(int a, int b) { return a + b; }
        static int mul(int a, int b) { return a * b; }

        // 调用链：calc 调用 add/mul，若被内联，等同于直接展开
        static int calc(int a, int b) {
            return add(a, b) + mul(a, b);
        }

        public static void run() {
            System.out.println("\n===== 漓示3：方法内联 =====");

            for (int i = 0; i < 10_000; i++) calc(i, i); // 预热

            long t1 = System.nanoTime();
            int s = 0;
            for (int i = 0; i < ITER; i++) s += calc(i, i);
            long d1 = System.nanoTime() - t1;
            System.out.println("  calc (调用 add/mul) " + ITER + " 次: " + (d1 / 1_000_000) + " ms  sum=" + s);

            // 等价的内联手写版本，用于对比
            long t2 = System.nanoTime();
            int s2 = 0;
            for (int i = 0; i < ITER; i++) s2 += (i + i) + (i * i);
            long d2 = System.nanoTime() - t2;
            System.out.println("  手写等价展开       " + ITER + " 次: " + (d2 / 1_000_000) + " ms  sum=" + s2);

            System.out.println("  [结论]");
            System.out.println("    - 小方法会被 JIT 内联，消除方法调用开销");
            System.out.println("    - 内联条件：方法体积小 + 调用频繁");
            System.out.println("    - -XX:+PrintInlining 查看内联决策");
            System.out.println("    - 不要过早优化：写小方法给 JIT 机会即可");
        }
    }

    // ============================================================
    // 漓示4：循环优化（展开 / 不变代码外提）
    // ============================================================
    static class LoopDemo {

        // 不变代码外提：data.length 提取到循环外
        static int sumWithHoist(int[] data) {
            int s = 0;
            int limit = data.length; // 外提
            for (int i = 0; i < limit; i++) s += data[i];
            return s;
        }

        // 循环展开：手动展开 4 倍
        static int sumUnrolled(int[] data) {
            int s = 0;
            int limit = data.length - 3;
            int i = 0;
            for (; i < limit; i += 4) {
                s += data[i] + data[i + 1] + data[i + 2] + data[i + 3];
            }
            for (; i < data.length; i++) s += data[i]; // 处理剩余
            return s;
        }

        public static void run() {
            System.out.println("\n===== 漓示4：循环优化 =====");

            int[] data = new int[10_000_000];
            for (int i = 0; i < data.length; i++) data[i] = i;

            // 预热
            for (int i = 0; i < 100; i++) {
                sumWithHoist(data);
                sumUnrolled(data);
            }

            long t1 = System.nanoTime();
            int s1 = sumWithHoist(data);
            long d1 = System.nanoTime() - t1;
            System.out.println("  sumWithHoist: " + (d1 / 1_000_000) + " ms  sum=" + s1);

            long t2 = System.nanoTime();
            int s2 = sumUnrolled(data);
            long d2 = System.nanoTime() - t2;
            System.out.println("  sumUnrolled: " + (d2 / 1_000_000) + " ms  sum=" + s2);

            System.out.println("  [结论]");
            System.out.println("    - 循环不变代码外提：JIT 自动把 data.length 提到循环外");
            System.out.println("    - 循环展开：减少分支判断次数，提升流水线效率");
            System.out.println("    - 现代 JIT 大多自动展开，手动展开收益有限");
            System.out.println("    - -XX:LoopMaxUnroll 控制最大展开次数");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 8.4 — JIT 编译与调优 实战演示        ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        HotspotDemo.run();   // 漓示1：热点探测
        EscapeDemo.run();    // 漓示2：逃逸分析
        InlineDemo.run();    // 漓示3：方法内联
        LoopDemo.run();      // 漓示4：循环优化

        System.out.println("\n===== 全部演示结束 =====");
    }
}
