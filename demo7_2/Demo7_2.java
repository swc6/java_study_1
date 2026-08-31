/**
 * Demo 7.2 — 结构型模式 实战演示
 *
 * 涵盖内容：
 *   7.2.1 代理模式（静态代理）
 *   7.2.2 装饰器模式（多层包装增强）
 *   7.2.3 适配器模式（类适配器 / 对象适配器）
 *
 * 编译：javac Demo7_2.java
 * 运行：java Demo7_2
 */
public class Demo7_2 {

    // ============================================================
    // 演示1：代理模式 —— 控制访问、加增强
    // ============================================================
    static class ProxyDemo {

        interface Service { String execute(String input); }

        // 真实主题：实际业务
        static class RealService implements Service {
            public String execute(String input) {
                return "处理[" + input + "]";
            }
        }

        // 代理：持有真实对象引用，前后加日志/权限/缓存等
        static class LoggingProxy implements Service {
            private final Service real;
            public LoggingProxy(Service real) { this.real = real; }
            public String execute(String input) {
                System.out.println("    [proxy] 前置日志: 入参=" + input);
                String result = real.execute(input);
                System.out.println("    [proxy] 后置日志: 结果=" + result);
                return result;
            }
        }

        public static void run() {
            System.out.println("===== 漓示1：代理模式 =====");

            Service proxy = new LoggingProxy(new RealService());
            String r = proxy.execute("hello");
            System.out.println("  最终结果: " + r);

            System.out.println("  [结论]");
            System.out.println("    - 代理与被代理实现同一接口，调用者无感知");
            System.out.println("    - 静态代理需为每个接口手写代理类");
            System.out.println("    - 动态代理（JDK Proxy / CGLIB）可统一生成，见 Demo4_3");
        }
    }

    // ============================================================
    // 演示2：装饰器模式 —— 递归包装增强
    // ============================================================
    static class DecoratorDemo {

        interface Coffee { double cost(); String desc(); }

        static class SimpleCoffee implements Coffee {
            public double cost() { return 1.0; }
            public String desc() { return "SimpleCoffee"; }
        }

        // 装饰器基类：持有一个 Coffee，行为委托
        abstract static class CoffeeDecorator implements Coffee {
            protected final Coffee inner;
            protected CoffeeDecorator(Coffee c) { this.inner = c; }
        }

        static class Milk extends CoffeeDecorator {
            public Milk(Coffee c) { super(c); }
            public double cost() { return inner.cost() + 0.5; }
            public String desc() { return inner.desc() + "+Milk"; }
        }
        static class Sugar extends CoffeeDecorator {
            public Sugar(Coffee c) { super(c); }
            public double cost() { return inner.cost() + 0.2; }
            public String desc() { return inner.desc() + "+Sugar"; }
        }
        static class Whip extends CoffeeDecorator {
            public Whip(Coffee c) { super(c); }
            public double cost() { return inner.cost() + 0.8; }
            public String desc() { return inner.desc() + "+Whip"; }
        }

        public static void run() {
            System.out.println("\n===== 漓示2：装饰器模式 =====");

            Coffee c1 = new SimpleCoffee();
            System.out.println("  " + c1.desc() + " = " + c1.cost());

            Coffee c2 = new Milk(new Sugar(new SimpleCoffee()));
            System.out.println("  " + c2.desc() + " = " + c2.cost());

            Coffee c3 = new Whip(new Milk(new Sugar(new SimpleCoffee())));
            System.out.println("  " + c3.desc() + " = " + c3.cost());

            System.out.println("  [结论]");
            System.out.println("    - 装饰器与被装饰者同接口，可任意嵌套");
            System.out.println("    - 区别代理：装饰器强调“增强功能”，代理强调“控制访问”");
            System.out.println("    - JDK I/O 流是典型装饰器：BufferedInputStream(FileInputStream)");
        }
    }

    // ============================================================
    // 演示3：适配器模式 —— 接口转换
    // ============================================================
    static class AdapterDemo {

        // 目标接口（客户端期望的）
        interface Target { String request(); }

        // 被适配者：方法签名不一致
        static class Adaptee {
            public String specificRequest() { return "被适配者的输出"; }
        }

        // 类适配器：通过继承 + 实现接口
        static class ClassAdapter extends Adaptee implements Target {
            public String request() { return "类适配器 -> " + specificRequest(); }
        }

        // 对象适配器：通过组合持有被适配者
        static class ObjectAdapter implements Target {
            private final Adaptee adaptee;
            public ObjectAdapter(Adaptee a) { this.adaptee = a; }
            public String request() { return "对象适配器 -> " + adaptee.specificRequest(); }
        }

        public static void run() {
            System.out.println("\n===== 漓示3：适配器模式 =====");

            Target t1 = new ClassAdapter();
            System.out.println("  " + t1.request());

            Target t2 = new ObjectAdapter(new Adaptee());
            System.out.println("  " + t2.request());

            System.out.println("  [结论]");
            System.out.println("    - 类适配器：继承被适配者，受单继承限制");
            System.out.println("    - 对象适配器：组合持有，更灵活（推荐）");
            System.out.println("    - 常见场景：旧接口适配新接口，第三方 SDK 适配");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 7.2 — 结构型模式 实战演示            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        ProxyDemo.run();       // 演示1：代理
        DecoratorDemo.run();  // 演示2：装饰器
        AdapterDemo.run();     // 演示3：适配器

        System.out.println("\n===== 全部演示结束 =====");
    }
}
