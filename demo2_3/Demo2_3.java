import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Demo 2.3 — 类加载机制 实战演示
 *
 * 涵盖内容：
 *   2.3.1 类的生命周期（加载 → 链接(验证/准备/解析) → 初始化）
 *   2.3.2 类加载器层级（Bootstrap / Platform / App）
 *   2.3.3 双亲委派模型（委派与安全性）
 *   2.3.4 自定义类加载器（命名空间隔离 / 破坏双亲委派）
 *
 * 编译：javac Demo2_3.java
 * 运行：java Demo2_3
 */
public class Demo2_3 {

    // ============================================================
    // 演示1：类的生命周期 —— 加载、链接、初始化的时机
    // ============================================================
    static class LifecycleDemo {

        // 目标类：<clinit> 由静态变量赋值 + 静态块按源码顺序组合而成
        public static class Foo {
            static {
                System.out.println("    [Foo] 第 1 个静态块执行");
            }
            static int value = initValue(); // 静态变量赋值，调用 initValue()
            static {
                System.out.println("    [Foo] 第 2 个静态块执行");
            }

            static int initValue() {
                System.out.println("    [Foo] 静态变量 value 赋值（调用 initValue()）");
                return 42;
            }

            private Foo() {
                System.out.println("    [Foo] 实例构造方法 <init> 执行（每次 new 都执行）");
            }
        }

        public static void run() throws Exception {
            System.out.println("===== 演示1：类的生命周期 =====");
            String name = "Demo2_3$LifecycleDemo$Foo";
            ClassLoader cl = Demo2_3.class.getClassLoader();

            // 1) initialize=false：仅加载+链接（验证/准备/解析），不触发 <clinit>
            System.out.println("  1) Class.forName(initialize=false) —— 加载但不初始化");
            Class<?> c1 = Class.forName(name, false, cl);
            System.out.println("    已加载，但未初始化（上方无任何 [Foo] 静态块输出）");
            System.out.println("    此时静态变量 value 处于准备阶段：默认零值 0");

            // 2) initialize=true：触发初始化 → 执行 <clinit>
            System.out.println("\n  2) Class.forName(initialize=true) —— 触发初始化，执行 <clinit>");
            Class<?> c2 = Class.forName(name, true, cl);
            System.out.println("    [观察] <clinit> 按源码顺序执行：静态块 → 静态变量赋值 → 静态块");

            // 3) 实例化：触发 <init>（不再重复 <clinit>）
            System.out.println("\n  3) new 实例 —— 首次实例化触发 <init>，<clinit> 不会重复执行");
            Object o1 = c2.getDeclaredConstructor().newInstance();
            Object o2 = c2.getDeclaredConstructor().newInstance();
            System.out.println("    (再次 new 只执行 <init>，<clinit> 已执行过不再触发)");

            System.out.println("  [结论] 加载→链接(验证/准备/解析)→初始化，初始化仅一次");
        }
    }

    // ============================================================
    // 演示2：类加载器层级 —— Bootstrap / Platform / App
    // ============================================================
    static class ClassLoaderHierarchyDemo {

        public static void run() {
            System.out.println("\n===== 漓示2：类加载器层级 =====");

            // java.lang.String 由启动类加载器(Bootstrap)加载，Java 中表现为 null
            ClassLoader bootstrap = String.class.getClassLoader();
            System.out.println("  java.lang.String 的加载器: "
                    + (bootstrap == null ? "null → Bootstrap ClassLoader(C++ 实现)" : bootstrap));

            // 本类 Demo2_3 由应用类加载器(App)加载
            ClassLoader app = Demo2_3.class.getClassLoader();
            System.out.println("  Demo2_3 的加载器: " + app);

            // 自下而上打印父加载器链
            System.out.println("  父加载器链(App → Platform → Bootstrap):");
            ClassLoader cur = app;
            int level = 0;
            while (cur != null) {
                System.out.println("    " + indent(level++) + "└─ " + cur);
                cur = cur.getParent();
            }
            System.out.println("    " + indent(level) + "└─ null → Bootstrap ClassLoader");
            System.out.println("  [结论] 类加载请求逐级委派给父加载器，父加载不了才自己加载");
        }

        private static String indent(int n) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append("  ");
            return sb.toString();
        }
    }

    // ============================================================
    // 演示3：双亲委派模型 —— 委派行为与包保护安全性
    // ============================================================
    static class ParentDelegationDemo {

        // 专门用于测试 defineClass 的加载器
        static class DefineTestLoader extends ClassLoader {
            public Class<?> tryDefine(String name, byte[] data) throws ClassFormatError, SecurityException {
                return defineClass(name, data, 0, data.length);
            }
        }

        public static void run() throws Exception {
            System.out.println("\n===== 漓示3：双亲委派模型 =====");

            // 1) 自定义加载器加载 java.lang.String → 委派给父加载器(最终 Bootstrap)
            System.out.println("  1) 用自定义加载器加载 java.lang.String");
            CustomClassLoader loader = new CustomClassLoader();
            Class<?> strClass = loader.loadClass("java.lang.String");
            ClassLoader who = strClass.getClassLoader();
            System.out.println("    返回的 Class: " + strClass.getName());
            System.out.println("    实际加载器: "
                    + (who == null ? "Bootstrap(启动类加载器)" : who)
                    + "（并非自定义加载器）");
            System.out.println("    strClass == String.class ? " + (strClass == String.class)
                    + " (同一份，说明委派成功)");

            // 2) 尝试在 java.lang 包下定义新类 → 被禁止
            System.out.println("\n  2) 尝试 defineClass(java.lang.Evil) —— 破坏核心包");
            DefineTestLoader t = new DefineTestLoader();
            try {
                t.tryDefine("java.lang.Evil", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0});
                System.out.println("    (意外：未被禁止)");
            } catch (SecurityException e) {
                System.out.println("    抛出 SecurityException: " + e.getMessage());
                System.out.println("    [结论] java.* 等核心包受保护，禁止自定义加载器加载 → 双亲委派的安全保障");
            } catch (ClassFormatError e) {
                System.out.println("    抛出 " + e.getClass().getSimpleName()
                        + " (包名先于格式被检查，正常应见 SecurityException)");
            }

            System.out.println("  [破坏双亲委派] SPI(JDBC)、OSGi、Tomcat 每个应用独立 ClassLoader 等");
        }
    }

    // ============================================================
    // 演示4：自定义类加载器 —— 读取自身字节码，验证命名空间隔离
    // ============================================================
    static class CustomClassLoader extends ClassLoader {
        CustomClassLoader() {
            super(getSystemClassLoader()); // 父加载器 = App ClassLoader
        }

        // 对 "Demo2_3" 破坏双亲委派：不委派父加载器，强制自己加载（模拟容器隔离）
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if ("Demo2_3".equals(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) c = findClass(name);
                if (resolve) resolveClass(c);
                return c;
            }
            return super.loadClass(name, resolve); // 其它类照常委派父加载器
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!"Demo2_3".equals(name)) {
                throw new ClassNotFoundException(name);
            }
            byte[] data = readOwnBytes();
            if (data == null) {
                throw new ClassNotFoundException(name + "：找不到 Demo2_3.class 字节码");
            }
            return defineClass(name, data, 0, data.length);
        }

        // 读取 Demo2_3.class 字节码（先资源流，后文件回退）
        private static byte[] readOwnBytes() {
            try (InputStream in = Demo2_3.class.getResourceAsStream("/Demo2_3.class")) {
                if (in != null) return readAll(in);
            } catch (IOException ignore) {
            }
            File f = new File("Demo2_3.class");
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) {
                    return readAll(in);
                } catch (IOException ignore) {
                }
            }
            return null;
        }

        private static byte[] readAll(InputStream in) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    static class CustomLoaderDemo {

        public static void run() throws Exception {
            System.out.println("\n===== 漓示4：自定义类加载器（命名空间隔离） =====");

            CustomClassLoader l1 = new CustomClassLoader();
            CustomClassLoader l2 = new CustomClassLoader();

            Class<?> c1 = l1.loadClass("Demo2_3"); // 由 l1 自己加载
            Class<?> c2 = l2.loadClass("Demo2_3"); // 由 l2 自己加载
            Class<?> cSys = Demo2_3.class;          // 由 App 加载器加载

            System.out.println("  cSys (App 加载器) 的加载器: " + (cSys.getClassLoader() == null ? "Bootstrap" : cSys.getClassLoader()));
            System.out.println("  c1   的加载器: " + c1.getClassLoader());
            System.out.println("  c2   的加载器: " + c2.getClassLoader());
            System.out.println("  c1 == c2   ? " + (c1 == c2) + " (不同加载器 → 不同 Class 对象)");
            System.out.println("  c1 == cSys ? " + (c1 == cSys) + " (与系统加载的也不同)");
            System.out.println("  cSys.isAssignableFrom(c1) ? " + cSys.isAssignableFrom(c1)
                    + " (同名但加载器不同，类型不兼容！)");
            System.out.println("  [结论] 类的唯一性 = 类全名 + 加载它的 ClassLoader；");
            System.out.println("         不同加载器加载同名类，互不兼容（Tomcat 热部署、模块隔离的基础）");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 2.3 — 类加载机制 实战演示            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        LifecycleDemo.run();              // 演示1：类的生命周期
        ClassLoaderHierarchyDemo.run();   // 演示2：类加载器层级
        ParentDelegationDemo.run();       // 演示3：双亲委派模型
        CustomLoaderDemo.run();           // 演示4：自定义类加载器

        System.out.println("\n===== 全部演示结束 =====");
    }
}
