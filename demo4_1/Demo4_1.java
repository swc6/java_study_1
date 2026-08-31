import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Demo 4.1 — 反射 API 实战演示
 *
 * 涵盖内容：
 *   4.1.1 获取 Class 对象的三种方式
 *   4.1.2 反射操作（构造器 / 字段 / 方法）
 *   4.1.3 反射性能优化（setAccessible / MethodHandle）
 *
 * 编译：javac Demo4_1.java
 * 运行：java Demo4_1
 */
public class Demo4_1 {

    // 样本类：含无参/带参构造器、私有字段、公有方法
    public static class User {
        private String name;
        private int age;

        public User() {}
        public User(String name, int age) { this.name = name; this.age = age; }

        public String greet(String who) { return "Hi " + who + ", I am " + name; }
        @Override public String toString() { return "User(" + name + "," + age + ")"; }
    }

    // ============================================================
    // 演示1：获取 Class 对象
    // ============================================================
    static class GetClassDemo {

        public static void run() throws Exception {
            System.out.println("===== 演示1：获取 Class 对象 =====");

            // 方式1：类名.class
            Class<User> c1 = User.class;
            System.out.println("  方式1 .class          : " + c1.getName());

            // 方式2：对象.getClass()
            User user = new User("Alice", 25);
            Class<?> c2 = user.getClass();
            System.out.println("  方式2 getClass()      : " + c2.getName());

            // 方式3：Class.forName(全限定名)
            Class<?> c3 = Class.forName("Demo4_1$User");
            System.out.println("  方式3 Class.forName   : " + c3.getName());

            System.out.println("  三者同一 Class 对象? "
                    + (c1 == c2 && c2 == c3)); // true：每个类加载后只有一个 Class
        }
    }

    // ============================================================
    // 演示2：反射操作（构造器 / 字段 / 方法）
    // ============================================================
    static class OperationDemo {

        public static void run() throws Exception {
            System.out.println("\n===== 漓示2：反射操作 =====");

            Class<User> clazz = User.class;

            // 构造器 + 创建实例
            Constructor<User> ctor = clazz.getDeclaredConstructor(String.class, int.class);
            ctor.setAccessible(true);
            User user = ctor.newInstance("Bob", 30);
            System.out.println("  反射创建实例: " + user);

            // 字段读写（私有字段需 setAccessible）
            Field name = clazz.getDeclaredField("name");
            name.setAccessible(true);
            System.out.println("  反射读 name = " + name.get(user));
            name.set(user, "Charlie");
            System.out.println("  反射写后 name = " + name.get(user));

            Field age = clazz.getDeclaredField("age");
            age.setAccessible(true);
            System.out.println("  反射读 age = " + age.getInt(user));

            // 方法调用
            Method greet = clazz.getDeclaredMethod("greet", String.class);
            greet.setAccessible(true);
            String result = (String) greet.invoke(user, "World");
            System.out.println("  反射调用 greet(\"World\") = " + result);

            // 枚举所有声明方法
            System.out.println("  User 声明的方法:");
            for (Method m : clazz.getDeclaredMethods()) {
                System.out.println("    " + m.getName()
                        + " " + java.util.Arrays.toString(m.getParameterTypes()));
            }
        }
    }

    // ============================================================
    // 演示3：反射性能优化（setAccessible / MethodHandle）
    // ============================================================
    static class PerfDemo {

        static volatile Object sink; // 防止 JIT 死码消除

        public static void run() throws Throwable {
            System.out.println("\n===== 漓示3：反射性能优化 =====");

            User user = new User("Alice", 25);
            Method greet = User.class.getMethod("greet", String.class);
            Method greetOpen = User.class.getMethod("greet", String.class);
            greetOpen.setAccessible(true);               // 优化1：关闭访问检查
            MethodHandle mh = MethodHandles.lookup().unreflect(greet); // 优化2：方法句柄

            final int WARM = 200_000;
            final int N = 2_000_000;

            // 预热
            for (int i = 0; i < WARM; i++) {
                sink = greet.invoke(user, "x");
                sink = greetOpen.invoke(user, "x");
                sink = (String) mh.invokeExact(user, "x");
            }

            // 直接调用基准
            long t0 = System.nanoTime();
            for (int i = 0; i < N; i++) sink = user.greet("x");
            long direct = System.nanoTime() - t0;

            // 反射 invoke（未关访问检查）
            t0 = System.nanoTime();
            for (int i = 0; i < N; i++) sink = greet.invoke(user, "x");
            long reflect = System.nanoTime() - t0;

            // 反射 invoke + setAccessible(true)
            t0 = System.nanoTime();
            for (int i = 0; i < N; i++) sink = greetOpen.invoke(user, "x");
            long accessible = System.nanoTime() - t0;

            // MethodHandle
            t0 = System.nanoTime();
            for (int i = 0; i < N; i++) sink = (String) mh.invokeExact(user, "x");
            long handle = System.nanoTime() - t0;

            System.out.println("  直接调用                : " + nsPerOp(direct, N) + " ns/op");
            System.out.println("  Method.invoke           : " + nsPerOp(reflect, N) + " ns/op");
            System.out.println("  Method.invoke+setAccess : " + nsPerOp(accessible, N) + " ns/op");
            System.out.println("  MethodHandle.invokeExact: " + nsPerOp(handle, N) + " ns/op");
            System.out.println("  [结论] 反射 invoke 每次做参数装箱+访问检查，较慢；");
            System.out.println("         setAccessible(true) 跳过访问检查；MethodHandle 接近直接调用");
        }

        private static String nsPerOp(long totalNs, int n) {
            return String.format("%.2f", totalNs / (double) n);
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Throwable {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 4.1 — 反射 API 实战演示             ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        GetClassDemo.run();    // 演示1：获取 Class 对象
        OperationDemo.run();   // 演示2：反射操作
        PerfDemo.run();        // 演示3：性能优化

        System.out.println("\n===== 全部演示结束 =====");
    }
}
