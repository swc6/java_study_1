/**
 * Demo 7.1 — 创建型模式 实战演示
 *
 * 涵盖内容：
 *   7.1.1 单例模式（饿汉 / 双重检查 / 静态内部类 / 枚举）
 *   7.1.2 工厂模式（简单工厂 / 工厂方法 / 抽象工厂）
 *   7.1.3 建造者模式（链式构造不可变对象）
 *
 * 编译：javac Demo7_1.java
 * 运行：java Demo7_1
 */
public class Demo7_1 {

    // ============================================================
    // 演示1：单例模式
    // ============================================================
    static class SingletonDemo {

        // 1. 饿汉式：类加载时即创建，线程安全，无延迟加载
        static class EagerSingleton {
            private static final EagerSingleton INSTANCE = new EagerSingleton();
            private EagerSingleton() {}
            public static EagerSingleton getInstance() { return INSTANCE; }
        }

        // 2. 双重检查锁（DCL）：延迟加载，volatile 防止指令重排
        static class DclSingleton {
            private static volatile DclSingleton instance;
            private DclSingleton() {}
            public static DclSingleton getInstance() {
                if (instance == null) {                       // 第一次检查（无锁）
                    synchronized (DclSingleton.class) {
                        if (instance == null) {               // 第二次检查（同步）
                            instance = new DclSingleton();
                        }
                    }
                }
                return instance;
            }
        }

        // 3. 静态内部类：利用类加载机制保证线程安全 + 延迟加载
        static class HolderSingleton {
            private HolderSingleton() {}
            private static class Holder {
                private static final HolderSingleton INSTANCE = new HolderSingleton();
            }
            public static HolderSingleton getInstance() { return Holder.INSTANCE; }
        }

        // 4. 枚举单例：天然防反射、防序列化破坏
        enum EnumSingleton {
            INSTANCE;
            public void doSomething() {}
        }

        public static void run() {
            System.out.println("===== 演示1：单例模式 =====");

            System.out.println("  饿汉式   : " + EagerSingleton.getInstance());
            System.out.println("  DCL     : " + DclSingleton.getInstance());
            System.out.println("  静态内部类: " + HolderSingleton.getInstance());
            System.out.println("  枚举     : " + EnumSingleton.INSTANCE);

            // 同一实例验证
            System.out.println("  饿汉式两次获取是否相同: "
                    + (EagerSingleton.getInstance() == EagerSingleton.getInstance()));

            System.out.println("  [结论]");
            System.out.println("    - 无延迟需求用饿汉式；需延迟加载用静态内部类或 DCL");
            System.out.println("    - 枚举是单例最佳实践：天然防反射/序列化破坏");
        }
    }

    // ============================================================
    // 演示2：工厂模式
    // ============================================================
    static class FactoryDemo {

        interface Product { String name(); }

        static class ProductA implements Product { public String name() { return "A"; } }
        static class ProductB implements Product { public String name() { return "B"; } }

        // 简单工厂：根据参数返回不同产品
        static class SimpleFactory {
            public Product create(String type) {
                switch (type) {
                    case "A": return new ProductA();
                    case "B": return new ProductB();
                    default: throw new IllegalArgumentException("未知类型: " + type);
                }
            }
        }

        // 工厂方法：每个具体工厂只造一种产品
        abstract static class FactoryMethod {
            public abstract Product create();
        }
        static class FactoryA extends FactoryMethod { public Product create() { return new ProductA(); } }
        static class FactoryB extends FactoryMethod { public Product create() { return new ProductB(); } }

        // 抽象工厂：造一族相关产品
        interface GuiFactory {
            Button createButton();
            Checkbox createCheckbox();
        }
        interface Button { String render(); }
        interface Checkbox { String render(); }
        static class WinButton implements Button { public String render() { return "[Win Button]"; } }
        static class WinCheckbox implements Checkbox { public String render() { return "[Win Checkbox]"; } }
        static class MacButton implements Button { public String render() { return "[Mac Button]"; } }
        static class MacCheckbox implements Checkbox { public String render() { return "[Mac Checkbox]"; } }
        static class WinFactory implements GuiFactory {
            public Button createButton() { return new WinButton(); }
            public Checkbox createCheckbox() { return new WinCheckbox(); }
        }
        static class MacFactory implements GuiFactory {
            public Button createButton() { return new MacButton(); }
            public Checkbox createCheckbox() { return new MacCheckbox(); }
        }

        public static void run() {
            System.out.println("\n===== 漓示2：工厂模式 =====");

            System.out.println("  简单工厂 create(A): " + new SimpleFactory().create("A").name());
            System.out.println("  简单工厂 create(B): " + new SimpleFactory().create("B").name());

            System.out.println("  工厂方法 FactoryA: " + new FactoryA().create().name());
            System.out.println("  工厂方法 FactoryB: " + new FactoryB().create().name());

            GuiFactory win = new WinFactory();
            GuiFactory mac = new MacFactory();
            System.out.println("  抽象工厂 Win: " + win.createButton().render() + " " + win.createCheckbox().render());
            System.out.println("  抽象工厂 Mac: " + mac.createButton().render() + " " + mac.createCheckbox().render());

            System.out.println("  [结论]");
            System.out.println("    - 简单工厂：参数化创建，但新增产品要改工厂");
            System.out.println("    - 工厂方法：每个产品一个工厂类，符合开闭原则");
            System.out.println("    - 抽象工厂：成族创建（如一套 UI 风格）");
        }
    }

    // ============================================================
    // 演示3：建造者模式
    // ============================================================
    static class BuilderDemo {

        // 不可变对象：通过 Builder 链式构造
        static final class User {
            private final String name;
            private final int age;
            private final String email;
            private final String phone;

            private User(Builder b) {
                this.name = b.name;
                this.age = b.age;
                this.email = b.email;
                this.phone = b.phone;
            }

            public String toString() {
                return "User{name='" + name + "', age=" + age
                        + ", email='" + email + "', phone='" + phone + "'}";
            }

            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private String name;
                private int age;
                private String email;
                private String phone;

                public Builder name(String n)    { this.name = n; return this; }
                public Builder age(int a)        { this.age = a; return this; }
                public Builder email(String e)   { this.email = e; return this; }
                public Builder phone(String p)   { this.phone = p; return this; }

                public User build() {
                    if (name == null) throw new IllegalStateException("name 必填");
                    return new User(this);
                }
            }
        }

        public static void run() {
            System.out.println("\n===== 漓示3：建造者模式 =====");

            User u = User.builder()
                    .name("Alice")
                    .age(25)
                    .email("alice@example.com")
                    .phone("13800000000")
                    .build();
            System.out.println("  构造完成: " + u);

            // 部分字段
            User u2 = User.builder().name("Bob").age(30).build();
            System.out.println("  部分字段: " + u2);

            // 缺必填项
            try {
                User.builder().age(10).build();
            } catch (IllegalStateException e) {
                System.out.println("  校验失败: " + e.getMessage());
            }

            System.out.println("  [结论]");
            System.out.println("    - 解决多参数构造器爆炸问题，链式调用可读性好");
            System.out.println("    - 可在 build() 中做参数校验");
            System.out.println("    - 字段 final，构造后不可变，天然线程安全");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 7.1 — 创建型模式 实战演示            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        SingletonDemo.run();  // 演示1：单例
        FactoryDemo.run();    // 演示2：工厂
        BuilderDemo.run();     // 演示3：建造者

        System.out.println("\n===== 全部演示结束 =====");
    }
}
