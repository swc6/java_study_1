import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Demo 8.2 — 代码层面优化 实战演示
 *
 * 涵盖内容：
 *   8.2.1 对象创建优化（基本类型 vs 装箱 / StringBuilder 重用）
 *   8.2.2 集合使用优化（初始容量影响）
 *   8.2.3 字符串优化（StringBuilder / 常量池 / intern / 正则预编译）
 *   8.2.4 其他优化技巧（局部变量 / 异常开销）
 *
 * 说明：
 *   - 纯 JDK 实现，使用 System.nanoTime 做相对耗时对比
 *   - 测试结果受机器/JIT 影响，仅用于观察相对趋势
 *   - 预热后多次测量，减少 JIT 噪声
 *
 * 编译：javac Demo8_2.java
 * 运行：java Demo8_2
 */
public class Demo8_2 {

    private static final int WARMUP = 5_000;
    private static final int MEASURE = 500_000;
    private static final int COLLECTION_MEASURE = 100_000;
    private static final int STRING_MEASURE = 100_000;
    private static final int MISC_MEASURE = 100_000_000;

    // ============================================================
    // 漓示1：对象创建与装箱开销
    // ============================================================
    static class ObjectOptDemo {

        public static void run() {
            System.out.println("===== 漓示1：对象创建与装箱 =====");

            // 预热
            for (int i = 0; i < WARMUP; i++) {
                boxedSum(i);
                primitiveSum(i);
                newBuilderOnce(i);
                reuseBuilderOnce(i);
            }

            // 装箱 vs 基本类型
            long tBox = timeBoxed(MEASURE);
            long tPrim = timePrimitive(MEASURE);
            System.out.println("  包装类型 Long 求和 " + MEASURE + " 次: " + ns2ms(tBox) + " ms");
            System.out.println("  基本类型 long 求和 " + MEASURE + " 次: " + ns2ms(tPrim) + " ms");

            // StringBuilder 重用 vs 每次新建
            long tNew = timeNewBuilder(MEASURE);
            long tReuse = timeReuseBuilder(MEASURE);
            System.out.println("  每次新建 StringBuilder: " + ns2ms(tNew) + " ms");
            System.out.println("  setLength(0) 复用     : " + ns2ms(tReuse) + " ms");

            System.out.println("  [结论]");
            System.out.println("    - 包装类型在循环中触发频繁装箱拆箱，基本类型优先");
            System.out.println("    - 重用对象（如 StringBuilder）减少 GC 压力");
            System.out.println("    - 对象池仅在创建昂贵时才有意义，过度使用反而复杂");
        }

        private static long boxedSum(int n) {
            long s = 0L;
            for (int i = 0; i < n; i++) s += (Long) (long) i; // 强制装箱路径
            return s;
        }
        private static long primitiveSum(int n) {
            long s = 0L;
            for (int i = 0; i < n; i++) s += i;
            return s;
        }
        private static long timeBoxed(int n) {
            long t = System.nanoTime();
            long s = boxedSum(n);
            return System.nanoTime() - t + (s & 0); // 防止死代码消除
        }
        private static long timePrimitive(int n) {
            long t = System.nanoTime();
            long s = primitiveSum(n);
            return System.nanoTime() - t + (s & 0);
        }
        private static long newBuilderOnce(int n) {
            StringBuilder sb = new StringBuilder();
            sb.append(n);
            return sb.length();
        }
        private static long reuseBuilderOnce(int n) {
            StringBuilder sb = new StringBuilder(16);
            sb.setLength(0);
            sb.append(n);
            return sb.length();
        }
        private static long timeNewBuilder(int n) {
            long t = System.nanoTime();
            long s = 0;
            for (int i = 0; i < n; i++) s += newBuilderOnce(i);
            return System.nanoTime() - t + (s & 0);
        }
        private static long timeReuseBuilder(int n) {
            StringBuilder sb = new StringBuilder(16);
            long t = System.nanoTime();
            long s = 0;
            for (int i = 0; i < n; i++) {
                sb.setLength(0);
                sb.append(i);
                s += sb.length();
            }
            return System.nanoTime() - t + (s & 0);
        }
    }

    // ============================================================
    // 漓示2：集合初始容量与遍历
    // ============================================================
    static class CollectionOptDemo {

        public static void run() {
            System.out.println("\n===== 漓示2：集合优化 =====");

            int n = COLLECTION_MEASURE;

            // 预热
            for (int i = 0; i < 100; i++) {
                insertArrayListNoCap(1000);
                insertArrayListWithCap(1000);
                insertMapNoCap(1000);
                insertMapWithCap(1000);
            }

            long tNoCap = timeInsertArrayListNoCap(n);
            long tCap = timeInsertArrayListWithCap(n);
            System.out.println("  ArrayList 不指定容量 插入 " + n + " 次: " + ns2ms(tNoCap) + " ms");
            System.out.println("  ArrayList 预设容量    插入 " + n + " 次: " + ns2ms(tCap) + " ms");

            long tMapNo = timeInsertMapNoCap(n);
            long tMapCap = timeInsertMapWithCap(n);
            System.out.println("  HashMap 不指定容量 插入 " + n + " 次: " + ns2ms(tMapNo) + " ms");
            System.out.println("  HashMap 预设容量   插入 " + n + " 次: " + ns2ms(tMapCap) + " ms");

            System.out.println("  [结论]");
            System.out.println("    - ArrayList 初始容量 = 10，扩容 1.5 倍并复制数组");
            System.out.println("    - HashMap 初始容量 = 16，扩容 2 倍并 rehash");
            System.out.println("    - 已知大小时预设容量，避免多次扩容");
        }

        private static void insertArrayListNoCap(int n) {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(i);
        }
        private static void insertArrayListWithCap(int n) {
            List<Integer> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(i);
        }
        private static long timeInsertArrayListNoCap(int n) {
            long t = System.nanoTime();
            insertArrayListNoCap(n);
            return System.nanoTime() - t;
        }
        private static long timeInsertArrayListWithCap(int n) {
            long t = System.nanoTime();
            insertArrayListWithCap(n);
            return System.nanoTime() - t;
        }
        private static void insertMapNoCap(int n) {
            Map<Integer, Integer> m = new HashMap<>();
            for (int i = 0; i < n; i++) m.put(i, i);
        }
        private static void insertMapWithCap(int n) {
            // 容量 = n / 0.75 + 1，避免扩容
            Map<Integer, Integer> m = new HashMap<>((int) (n / 0.75f) + 1);
            for (int i = 0; i < n; i++) m.put(i, i);
        }
        private static long timeInsertMapNoCap(int n) {
            long t = System.nanoTime();
            insertMapNoCap(n);
            return System.nanoTime() - t;
        }
        private static long timeInsertMapWithCap(int n) {
            long t = System.nanoTime();
            insertMapWithCap(n);
            return System.nanoTime() - t;
        }
    }

    // ============================================================
    // 漓示3：字符串拼接 / 常量池 / 正则预编译
    // ============================================================
    static class StringOptDemo {

        private static final Pattern PRECOMPILED = Pattern.compile("\\d+");

        public static void run() {
            System.out.println("\n===== 漓示3：字符串优化 =====");

            int n = STRING_MEASURE;

            // 预热
            for (int i = 0; i < 100; i++) {
                concatPlus(1000);
                concatSb(1000);
                regexRecompile(1000, "1234567");
                regexPre(1000, "1234567");
            }

            long tPlus = timeConcatPlus(n);
            long tSb = timeConcatSb(n);
            System.out.println("  + 拼接循环 " + n + " 次: " + ns2ms(tPlus) + " ms");
            System.out.println("  预分配 StringBuilder: " + ns2ms(tSb) + " ms");

            // 字符串常量池 / intern
            String s1 = "hello";             // 常量池
            String s2 = "hello";             // 同引用
            String s3 = new String("hello"); // 堆中新对象
            String s4 = s3.intern();          // 放入常量池
            System.out.println("  s1==s2 (常量池)   : " + (s1 == s2));
            System.out.println("  s1==s3 (new 对象): " + (s1 == s3));
            System.out.println("  s1==s4 (intern)  : " + (s1 == s4));

            // 正则预编译 vs 每次编译
            String input = "1234567";
            long tRecompile = timeRegexRecompile(n, input);
            long tPre = timeRegexPre(n, input);
            System.out.println("  Pattern.compile 循环内 " + n + " 次: " + ns2ms(tRecompile) + " ms");
            System.out.println("  预编译 Pattern      " + n + " 次: " + ns2ms(tPre) + " ms");

            System.out.println("  [结论]");
            System.out.println("    - 循环内拼接字符串要用 StringBuilder，且预分配容量");
            System.out.println("    - intern() 适合大量重复字符串场景，注意控制常量池大小");
            System.out.println("    - 正则 Pattern 一定要预编译并复用");
        }

        private static void concatPlus(int n) {
            String s = "";
            for (int i = 0; i < n; i++) s = s + i;
        }
        private static void concatSb(int n) {
            StringBuilder sb = new StringBuilder(n * 4);
            for (int i = 0; i < n; i++) sb.append(i);
        }
        private static long timeConcatPlus(int n) {
            long t = System.nanoTime();
            concatPlus(n);
            return System.nanoTime() - t;
        }
        private static long timeConcatSb(int n) {
            long t = System.nanoTime();
            concatSb(n);
            return System.nanoTime() - t;
        }
        private static void regexRecompile(int n, String input) {
            for (int i = 0; i < n; i++) Pattern.compile("\\d+").matcher(input).matches();
        }
        private static void regexPre(int n, String input) {
            for (int i = 0; i < n; i++) PRECOMPILED.matcher(input).matches();
        }
        private static long timeRegexRecompile(int n, String input) {
            long t = System.nanoTime();
            regexRecompile(n, input);
            return System.nanoTime() - t;
        }
        private static long timeRegexPre(int n, String input) {
            long t = System.nanoTime();
            regexPre(n, input);
            return System.nanoTime() - t;
        }
    }

    // ============================================================
    // 漓示4：局部变量 vs 静态字段 / 异常开销
    // ============================================================
    static class MiscOptDemo {

        private static long staticField = 0;

        public static void run() {
            System.out.println("\n===== 漓示4：其他优化技巧 =====");

            int n = MISC_MEASURE;

            // 预热
            for (int i = 0; i < 1000; i++) {
                timeLocal(1000);
                timeStatic(1000);
            }

            // 局部变量栈访问 vs 静态字段访问
            long tLocal = timeLocal(n);
            long tStatic = timeStatic(n);
            System.out.println("  局部变量累加 " + n + " 次: " + ns2ms(tLocal) + " ms");
            System.out.println("  静态字段累加 " + n + " 次: " + ns2ms(tStatic) + " ms");

            // 异常构造开销（含栈采集）—— 异常比正常返回慢 1~2 个数量级
            int excN = n / 100; // 异常路径慢，缩小 100 倍
            long tNormal = timeNormalReturn(n);
            long tExc = timeExceptionReturn(excN);
            System.out.println("  普通返回 " + n + " 次: " + ns2ms(tNormal) + " ms");
            System.out.println("  抛异常返回 " + excN + " 次: " + ns2ms(tExc) + " ms");

            System.out.println("  [结论]");
            System.out.println("    - 局部变量在栈上访问，比字段/静态字段快");
            System.out.println("    - 异常构造需采集栈轨迹，开销大，不应作为控制流");
            System.out.println("    - 业务异常用返回码/Optional，系统异常才用异常");
        }

        private static long timeLocal(int n) {
            long t = System.nanoTime();
            long s = 0;
            for (int i = 0; i < n; i++) s += i;
            return System.nanoTime() - t + (s & 0);
        }
        private static long timeStatic(int n) {
            long t = System.nanoTime();
            for (int i = 0; i < n; i++) staticField += i;
            return System.nanoTime() - t;
        }
        private static long timeNormalReturn(int n) {
            long t = System.nanoTime();
            int r = 0;
            for (int i = 0; i < n; i++) {
                int v = findNormal(i);
                r += v;
            }
            return System.nanoTime() - t + (r & 0);
        }
        private static int findNormal(int i) { return i >= 0 ? i : -1; }

        private static long timeExceptionReturn(int n) {
            long t = System.nanoTime();
            int k = 0;
            for (int i = 0; i < n; i++) {
                try {
                    findOrThrow(i);
                } catch (RuntimeException e) {
                    k++;
                }
            }
            return System.nanoTime() - t + (k & 0);
        }
        private static int findOrThrow(int i) {
            if (i % 100 == 99) throw new RuntimeException("not found");
            return i;
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 8.2 — 代码层面优化 实战演示           ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        ObjectOptDemo.run();       // 漓示1：对象/装箱
        CollectionOptDemo.run();   // 漓示2：集合
        StringOptDemo.run();       // 漓示3：字符串
        MiscOptDemo.run();         // 漓示4：其他

        System.out.println("\n===== 全部演示结束 =====");
    }

    private static double ns2ms(long ns) { return ns / 1_000_000.0; }
}
