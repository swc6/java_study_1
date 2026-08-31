import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo 3.1 — 类型擦除 实战演示
 *
 * 涵盖内容：
 *   3.1.1 泛型是编译时特性，运行时类型参数被擦除
 *   3.1.2 擦除规则（无界→Object，上界→上界类型）
 *   3.1.3 擦除带来的运行时限制
 *
 * 编译：javac Demo3_1.java
 * 运行：java Demo3_1
 */
public class Demo3_1 {

    // ============================================================
    // 演示1：类型擦除 —— 运行时 List<String> 与 List<Integer> 是同一个类
    // ============================================================
    static class ErasureDemo {

        public static void run() {
            System.out.println("===== 演示1：类型擦除 =====");

            List<String> strings = new ArrayList<>();
            List<Integer> ints = new ArrayList<>();

            // 运行时两者是同一个 Class 对象（泛型参数被擦除）
            System.out.println("  List<String>.getClass() == List<Integer>.getClass() ? "
                    + (strings.getClass() == ints.getClass())); // true
            System.out.println("  运行时类型: " + strings.getClass().getName() + " (看不到泛型参数)");

            // 通过原始类型/反射可绕过编译期类型检查（堆污染 heap pollution）
            List<Integer> list = new ArrayList<>();
            list.add(1);
            // list.add("x"); // 编译期拒绝
            @SuppressWarnings({"unchecked", "rawtypes"})
            List raw = list;
            raw.add("混入的非 Integer"); // 运行期擦除后只知是 Object，不报错
            System.out.println("  通过原始类型混入字符串后 list.size() = " + list.size());

            System.out.println("  尝试以 Integer 取出第二个元素（编译期插入 checkcast）...");
            try {
                Integer x = list.get(1);
                System.out.println("  意外成功: " + x);
            } catch (ClassCastException e) {
                System.out.println("  运行期抛出 ClassCastException: " + e.getMessage());
                System.out.println("  [结论] 泛型类型安全靠编译期检查 + 运行期 checkcast 共同保证");
            }
        }
    }

    // ============================================================
    // 演示2：擦除规则 —— 上界决定擦除后的类型
    // ============================================================
    static class BoundRuleDemo {

        static class Holder<T> { T value; }                            // <T> → Object
        static class NumberHolder<T extends Number> { T value; }       // → Number
        static class CompHolder<T extends Comparable<T>> { T value; } // → Comparable
        static class Pair<K, V> { K key; V val; }                      // K→Object, V→Object

        public static void run() throws Exception {
            System.out.println("\n===== 演示2：擦除规则（上界决定擦除后类型） =====");

            // Field.getType() 返回擦除后的类型（运行时实际类型）
            System.out.println("  <T>            字段擦除为: " + fieldErasure(Holder.class, "value"));
            System.out.println("  <T extends Number>  字段擦除为: " + fieldErasure(NumberHolder.class, "value"));
            System.out.println("  <T extends Comparable<T>> 字段擦除为: " + fieldErasure(CompHolder.class, "value"));
            System.out.println("  Pair<K,V>      K擦除为 " + fieldErasure(Pair.class, "key")
                    + ", V擦除为 " + fieldErasure(Pair.class, "val"));

            System.out.println("  [规则]");
            System.out.println("    无界 <T>            → Object");
            System.out.println("    <T extends Number>  → Number");
            System.out.println("    <T extends Comparable<T>> → Comparable");
        }

        private static String fieldErasure(Class<?> cls, String fieldName) throws Exception {
            Field f = cls.getDeclaredField(fieldName);
            Type generic = f.getGenericType();      // TypeVariable（如 T）
            Class<?> erased = f.getType();         // 擦除后的类
            return erased.getSimpleName() + "  (genericType=" + generic + ")";
        }
    }

    // ============================================================
    // 演示3：擦除带来的运行时限制
    // ============================================================
    static class LimitationDemo {

        public static void run() {
            System.out.println("\n===== 漓示3：擦除带来的限制 =====");

            System.out.println("  1) 无法 new T()：擦除后 T 是 Object，运行期无法确定具体类型");
            System.out.println("  2) 无法 new T[10]：泛型数组有运行时类型安全问题");
            System.out.println("  3) 无法用 instanceof List<String>：运行时只有 List");
            System.out.println("  4) 静态字段/方法不能使用类的类型参数");

            List<String> list = new ArrayList<>();
            System.out.println("  list instanceof List ? " + (list instanceof List));       // true
            // System.out.println(list instanceof List<String>); // 编译错误
            System.out.println("  → 只能 instanceof List，不能 instanceof List<String>");

            // Class 字面量也只认原始类型
            System.out.println("  List<String>.class == List.class ? "
                    + (List.class == ArrayList.class.getSuperclass())); // List.class 唯一
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 3.1 — 类型擦除 实战演示             ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        ErasureDemo.run();    // 演示1：类型擦除
        BoundRuleDemo.run();  // 演示2：擦除规则
        LimitationDemo.run(); // 演示3：运行时限制

        System.out.println("\n===== 全部演示结束 =====");
    }
}
