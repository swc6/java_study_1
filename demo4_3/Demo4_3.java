import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Demo 4.3 — 动态代理 实战演示
 *
 * 涵盖内容：
 *   4.3.1 JDK 动态代理（Proxy + InvocationHandler）
 *   4.3.2 CGLIB 动态代理原理（字节码生成子类）
 *   4.3.3 两种代理对比
 *
 * 编译：javac Demo4_3.java
 * 运行：java Demo4_3
 */
public class Demo4_3 {

    // 被代理接口
    public interface Service {
        String doWork(String input);
    }

    public static class RealService implements Service {
        @Override
        public String doWork(String input) {
            System.out.println("    [RealService] 处理: " + input);
            return "result:" + input;
        }
    }

    // ============================================================
    // 演示1：JDK 动态代理 —— 计时 + 前后置日志
    // ============================================================
    static class JdkProxyDemo {

        // InvocationHandler：代理逻辑都在这里
        static class TimingHandler implements InvocationHandler {
            private final Object target;
            TimingHandler(Object target) { this.target = target; }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                long start = System.nanoTime();
                System.out.println("    [Proxy] before: " + method.getName());
                Object result = method.invoke(target, args); // 委托真实对象
                long cost = (System.nanoTime() - start) / 1_000;
                System.out.println("    [Proxy] after: " + method.getName() + " 耗时 " + cost + "us");
                return result;
            }
        }

        @SuppressWarnings("unchecked")
        public static <T> T createProxy(T target, Class<T> iface) {
            return (T) Proxy.newProxyInstance(
                    iface.getClassLoader(),
                    new Class<?>[]{iface},
                    new TimingHandler(target));
        }

        public static void run() {
            System.out.println("===== 演示1：JDK 动态代理 =====");

            RealService real = new RealService();
            Service proxy = createProxy(real, Service.class);

            System.out.println("  proxy 类型: " + proxy.getClass().getName()); // $Proxy0
            System.out.println("  isProxyClass: " + Proxy.isProxyClass(proxy.getClass()));
            System.out.println("  代理实现的接口: " + java.util.Arrays.toString(proxy.getClass().getInterfaces()));

            String result = proxy.doWork("hello");
            System.out.println("  返回值: " + result);

            System.out.println("  [特点] 只能代理接口；代理类由 ProxyGenerator 生成，命名形如 $ProxyN");
        }
    }

    // ============================================================
    // 演示2：CGLIB 动态代理原理（外部库，原理说明）
    // ============================================================
    static class CglibDemo {

        public static void run() {
            System.out.println("\n===== 漓示2：CGLIB 动态代理原理 =====");
            System.out.println("  CGLIB 通过字节码生成目标类的子类实现代理（无需接口）：");
            System.out.println("    Enhancer enhancer = new Enhancer();");
            System.out.println("    enhancer.setSuperclass(RealService.class);");
            System.out.println("    enhancer.setCallback(new MethodInterceptor() {");
            System.out.println("        public Object intercept(Object obj, Method m, Object[] args, MethodProxy proxy) {");
            System.out.println("            // 前置");
            System.out.println("            Object r = proxy.invokeSuper(obj, args); // 调用父类原始方法");
            System.out.println("            // 后置");
            System.out.println("            return r;");
            System.out.println("        }");
            System.out.println("    });");
            System.out.println("    RealService proxy = (RealService) enhancer.create();");
            System.out.println("  [注意]");
            System.out.println("    - CGLIB 依赖第三方库（net.sf.cglib 或 ByteBuddy）");
            System.out.println("    - 不能代理 final 类/方法（子类无法继承）");
            System.out.println("    - Spring AOP：有接口用 JDK 代理，无接口用 CGLIB");
            System.out.println("  [本演示为原理说明，未引入外部依赖，故不实际运行 CGLIB]");
        }
    }

    // ============================================================
    // 演示3：两种代理对比
    // ============================================================
    static class CompareDemo {

        public static void run() {
            System.out.println("\n===== 漓示3：两种代理对比 =====");
            System.out.println("  ┌──────────┬───────────────┬───────────────┐");
            System.out.println("  │ 特性     │ JDK 动态代理  │ CGLIB 代理    │");
            System.out.println("  ├──────────┼───────────────┼───────────────┤");
            System.out.println("  │ 代理对象 │ 接口          │ 普通类(子类)  │");
            System.out.println("  │ 实现方式 │ 反射 + 生成类│ 字节码生成    │");
            System.out.println("  │ 性能     │ 较低          │ 较高          │");
            System.out.println("  │ 依赖     │ JDK 自带      │ 第三方库      │");
            System.out.println("  │ 限制     │ 必须有接口    │ final 不可代理│");
            System.out.println("  └──────────┴───────────────┴───────────────┘");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 4.3 — 动态代理 实战演示             ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        JdkProxyDemo.run();   // 演示1：JDK 动态代理
        CglibDemo.run();      // 演示2：CGLIB 原理
        CompareDemo.run();    // 演示3：对比

        System.out.println("\n===== 全部演示结束 =====");
    }
}
