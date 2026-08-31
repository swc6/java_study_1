import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Demo 6.2 — 序列化注意事项 实战演示
 *
 * 涵盖内容：
 *   6.2.1 serialVersionUID 与版本兼容
 *   6.2.2 单例防破坏（readResolve）
 *   6.2.3 敏感数据保护（transient + 自定义读写）
 *   6.2.4 安全反序列化（ObjectInputFilter）
 *
 * 编译：javac Demo6_2.java
 * 运行：java Demo6_2
 */
public class Demo6_2 {

    // ============================================================
    // 演示1：serialVersionUID 与版本兼容性
    // ============================================================
    static class VersionDemo {

        public static class V1 implements Serializable {
            private static final long serialVersionUID = 1L; // 显式声明
            private String name;
            private int age;
            public V1(String name, int age) { this.name = name; this.age = age; }
            @Override public String toString() { return "V1{name='" + name + "', age=" + age + "}"; }
        }

        /** 模拟“类演进了字段”的场景：新增字段 score，serialVersionUID 保持一致 */
        public static class V2 implements Serializable {
            private static final long serialVersionUID = 1L; // 与 V1 一致
            private String name;
            private int age;
            private int score = -1; // 新增字段，反序列化时取默认值
            public V2() {}
            public V2(String name, int age, int score) { this.name = name; this.age = age; this.score = score; }
            @Override public String toString() { return "V2{name='" + name + "', age=" + age + ", score=" + score + "}"; }
        }

        @SuppressWarnings("unchecked")
        public static void run() throws Exception {
            System.out.println("===== 演示1：serialVersionUID =====");

            // 用 V1 写出字节
            V1 v1 = new V1("Alice", 28);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) { oos.writeObject(v1); }
            byte[] data = baos.toByteArray();
            System.out.println("  V1 序列化字节: " + data.length + "B");

            // 把同样的字节当作 V2 反序列化（serialVersionUID 一致 → 兼容）
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                Object obj = ois.readObject();
                System.out.println("  作为 V2 反序列化: " + obj + "（score 取默认值 -1）");
            }

            System.out.println("  [结论]");
            System.out.println("    - 不显式声明 serialVersionUID，编译器按类结构自动生成，改动即不兼容");
            System.out.println("    - 显式声明后，新增/删除字段在反序列化时按默认值补齐，不会失败");
        }
    }

    // ============================================================
    // 演示2：单例防破坏 —— readResolve
    // ============================================================
    static class SingletonDemo {

        /** 没有保护的“单例”：反序列化会创建新对象 */
        public static class UnsafeSingleton implements Serializable {
            private static final long serialVersionUID = 1L;
            private static final UnsafeSingleton INSTANCE = new UnsafeSingleton();
            private UnsafeSingleton() {}
            public static UnsafeSingleton getInstance() { return INSTANCE; }
            @Override public String toString() { return "UnsafeSingleton@" + System.identityHashCode(this); }
        }

        /** 安全单例：实现 readResolve，反序列化时返回原实例 */
        public static class SafeSingleton implements Serializable {
            private static final long serialVersionUID = 1L;
            private static final SafeSingleton INSTANCE = new SafeSingleton();
            private SafeSingleton() {}
            public static SafeSingleton getInstance() { return INSTANCE; }

            // 反序列化时，JVM 在新建对象后调用此方法，用其返回值替换 readObject 的结果
            private Object readResolve() { return INSTANCE; }

            @Override public String toString() { return "SafeSingleton@" + System.identityHashCode(this); }
        }

        public static void run() throws Exception {
            System.out.println("\n===== 漓示2：单例防破坏 =====");

            UnsafeSingleton u1 = UnsafeSingleton.getInstance();
            UnsafeSingleton u2 = roundTrip(u1);
            System.out.println("  Unsafe 原实例  : " + u1);
            System.out.println("  Unsafe 反序列化: " + u2);
            System.out.println("  是否同一对象  : " + (u1 == u2) + " ← 反序列化创建了新对象，破坏单例");

            SafeSingleton s1 = SafeSingleton.getInstance();
            SafeSingleton s2 = roundTrip(s1);
            System.out.println("  Safe   原实例  : " + s1);
            System.out.println("  Safe   反序列化: " + s2);
            System.out.println("  是否同一对象  : " + (s1 == s2) + " ← readResolve 拦截，返回唯一实例");

            System.out.println("  [结论]");
            System.out.println("    - 反序列化会绕过构造器新建对象，破坏单例");
            System.out.println("    - 实现 readResolve() 返回唯一实例即可保护");
            System.out.println("    - 枚举单例天然安全（JVM 在反序列化时直接返回常量）");
        }

        @SuppressWarnings("unchecked")
        private static <T> T roundTrip(T obj) throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) { oos.writeObject(obj); }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                return (T) ois.readObject();
            }
        }
    }

    // ============================================================
    // 演示3：敏感数据保护（transient + 自定义读写）
    // ============================================================
    static class SensitiveDemo {

        public static class Account implements Serializable {
            private static final long serialVersionUID = 1L;

            private String username;
            private transient String password;   // 不走默认序列化
            private transient String token;       // 自定义加密写入

            public Account(String username, String password, String token) {
                this.username = username;
                this.password = password;
                this.token = token;
            }

            private void writeObject(ObjectOutputStream out) throws IOException {
                out.defaultWriteObject();
                out.writeUTF(xor(password));  // 简单异或混淆
                out.writeUTF(xor(token));
            }

            private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
                in.defaultReadObject();
                this.password = xor(in.readUTF());
                this.token = xor(in.readUTF());
            }

            private static String xor(String s) {
                char[] k = "KEY".toCharArray();
                char[] c = s.toCharArray();
                for (int i = 0; i < c.length; i++) c[i] ^= k[i % k.length];
                return new String(c);
            }

            @Override public String toString() {
                return "Account{username='" + username + "', password='" + password + "', token='" + token + "'}";
            }
        }

        /** 探测字节流中是否包含明文敏感字符串 */
        public static void run() throws Exception {
            System.out.println("\n===== 漓示3：敏感数据保护 =====");

            Account acc = new Account("alice", "P@ssw0rd", "Bearer-abc123");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) { oos.writeObject(acc); }
            byte[] data = baos.toByteArray();

            System.out.println("  字节中包含 'P@ssw0rd' 明文: " + new String(data).contains("P@ssw0rd"));
            System.out.println("  字节中包含 'Bearer-abc123' 明文: " + new String(data).contains("Bearer-abc123"));

            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                System.out.println("  反序列化: " + ois.readObject());
            }

            System.out.println("  [结论]");
            System.out.println("    - transient 字段不会被默认序列化写入字节流");
            System.out.println("    - 自定义 writeObject 可加密敏感字段，避免明文落盘");
            System.out.println("    - 实际项目应使用 AES/GCM 等标准加密，示例仅演示思路");
        }
    }

    // ============================================================
    // 演示4：安全反序列化 —— ObjectInputFilter
    // ============================================================
    static class FilterDemo {

        public static class GoodBean implements Serializable {
            private static final long serialVersionUID = 1L;
            private String msg;
            public GoodBean() {}
            public GoodBean(String msg) { this.msg = msg; }
            @Override public String toString() { return "GoodBean{msg='" + msg + "'}"; }
        }

        public static void run() throws Exception {
            System.out.println("\n===== 漓示4：ObjectInputFilter =====");

            GoodBean bean = new GoodBean("hello");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) { oos.writeObject(bean); }
            byte[] data = baos.toByteArray();

            // JDK 9+ 全局过滤器：白名单允许 Demo6_2 内部类 + JDK 核心类
            ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
                    Demo6_2.class.getName() + "$*;java.lang.*;java.util.*;!*");

            // 放行场景
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                ois.setObjectInputFilter(filter);
                Object obj = ois.readObject();
                System.out.println("  白名单放行: " + obj);
            }

            // 拒绝场景：伪造一个不在白名单的字节流（直接尝试反序列化 String[] 不在白名单）
            byte[] evilBytes;
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(new String[]{"x", "y"});
                evilBytes = bos.toByteArray();
            }

            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(evilBytes))) {
                ois.setObjectInputFilter(filter);
                ois.readObject();
                System.out.println("  结果: 竟然放行了（不应发生）");
            } catch (Exception ex) {
                System.out.println("  拒绝反序列化: " + ex.getClass().getSimpleName()
                        + " - " + ex.getMessage());
            }

            System.out.println("  [结论]");
            System.out.println("    - 反序列化是危险操作，外部数据可能触发已知 gadget 链");
            System.out.println("    - ObjectInputFilter 可限定允许/拒绝的类，!* 表示默认拒绝其余");
            System.out.println("    - 生产建议：JVM 全局配置 -Djdk.serialFilter");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 6.2 — 序列化注意事项 实战演示         ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        VersionDemo.run();       // 演示1：serialVersionUID
        SingletonDemo.run();     // 演示2：单例防破坏
        SensitiveDemo.run();     // 演示3：敏感数据保护
        FilterDemo.run();        // 演示4：安全反序列化

        System.out.println("\n===== 全部演示结束 =====");
    }
}
