import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Demo 3.2 — 通配符 实战演示
 *
 * 涵盖内容：
 *   3.2.1 三种通配符（? extends T / ? super T / ?）
 *   3.2.2 PECS 原则（Producer Extends, Consumer Super）
 *   3.2.3 通配符捕获（capture）
 *
 * 编译：javac Demo3_2.java
 * 运行：java Demo3_2
 */
public class Demo3_2 {

    // ============================================================
    // 演示1：三种通配符
    // ============================================================
    static class ThreeWildcardsDemo {

        // 上界 ? extends T：作为"生产者"只能读，不能写（除 null）
        static double sum(List<? extends Number> list) {
            double s = 0;
            for (Number n : list) s += n.doubleValue();
            // list.add(1); // 编译错误：无法向 ? extends 写入
            return s;
        }

        // 下界 ? super T：作为"消费者"只能写 T 及子类，读只能得 Object
        static void fill(List<? super Integer> list, int n) {
            for (int i = 0; i < n; i++) list.add(i);          // OK：可写 Integer
            // Integer x = list.get(0); // 编译错误：读取只能得 Object
            Object o = list.get(0);                            // OK
            System.out.println("    fill 内读取出的是 Object: " + o.getClass().getSimpleName());
        }

        // 任意通配符 ?：等同 ? extends Object
        static void printSize(List<?> list) {
            Object o = list.get(0);                            // 只能读为 Object
            System.out.println("    ? 的 size=" + list.size() + " first=" + o);
        }

        public static void run() {
            System.out.println("===== 演示1：三种通配符 =====");

            List<Integer> ints = Arrays.asList(1, 2, 3);
            List<Number> nums = new ArrayList<>(Arrays.asList(1.0, 2.0));

            System.out.println("  sum(List<Integer>) via ? extends Number = " + sum(ints));
            fill(nums, 3);                                      // List<Number> 接受 ? super Integer
            System.out.println("  fill(List<Number>,3) via ? super Integer 后: " + nums);
            printSize(ints);
        }
    }

    // ============================================================
    // 演示2：PECS 原则
    // ============================================================
    static class PECSDemo {

        // 从 src(extends) 读 → 向 dst(super) 写
        static <T> void copy(List<? super T> dst, List<? extends T> src) {
            for (T t : src) dst.add(t);
        }

        public static void run() {
            System.out.println("\n===== 漓示2：PECS 原则 =====");

            List<Integer> src = Arrays.asList(1, 2, 3);
            List<Number> dst = new ArrayList<>();
            copy(dst, src);  // dst 是消费者(super Integer)，src 是生产者(extends Integer)

            System.out.println("  copy(List<? super Integer>, List<? extends Integer>) 结果: " + dst);
            System.out.println("  [结论] P = Producer Extends（读用 ? extends），C = Consumer Super（写用 ? super）");
        }
    }

    // ============================================================
    // 演示3：通配符捕获（capture）
    // ============================================================
    static class CaptureDemo {

        // 直接对 List<?> 做 set 会失败：? 的类型未知，无法写入
        // 利用 "capture helper" 模式：把 ? 捕获为一个类型变量 E
        static void swap(List<?> list, int i, int j) {
            captureSwap(list, i, j); // 编译器把 ? 捕获为 E
        }

        private static <E> void captureSwap(List<E> list, int i, int j) {
            E tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }

        public static void run() {
            System.out.println("\n===== 漓示3：通配符捕获(capture) =====");

            List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
            System.out.println("  交换前: " + list);
            swap(list, 0, 2);
            System.out.println("  交换后: " + list);
            System.out.println("  [结论] 通过 capture 辅助方法 <E>，可在 ? 上完成读写（交换）");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 3.2 — 通配符 实战演示               ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        ThreeWildcardsDemo.run(); // 演示1：三种通配符
        PECSDemo.run();           // 演示2：PECS 原则
        CaptureDemo.run();        // 演示3：通配符捕获

        System.out.println("\n===== 全部演示结束 =====");
    }
}
