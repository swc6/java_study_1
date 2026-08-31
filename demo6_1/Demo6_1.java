import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Demo 6.1 — Java 原生序列化 实战演示
 *
 * 涵盖内容：
 *   6.1.1 实现 Serializable（默认序列化 / transient / static / 自定义 writeObject & readObject）
 *   6.1.2 Externalizable 接口（强制实现 writeExternal & readExternal）
 *   6.1.3 两者对比（字节大小 / 耗时 / 控制度）
 *
 * 编译：javac Demo6_1.java
 * 运行：java Demo6_1
 */
public class Demo6_1 {

    // ============================================================
    // 演示1：Serializable —— 标记接口 + 默认序列化
    // ============================================================
    static class SerializableDemo {

        /** 普通 Serializable 类：演示 transient / static / 自定义读写 */
        public static class User implements Serializable {
            private static final long serialVersionUID = 1L;

            private String name;
            private int age;
            private transient String password;   // transient：不参与默认序列化
            private static String company = "TechCorp"; // static：不参与序列化

            public User() {} // 反序列化需要可访问构造器？Serializable 不调用构造器

            public User(String name, int age, String password) {
                this.name = name;
                this.age = age;
                this.password = password;
            }

            /** 自定义序列化：先默认写入，再追加加密后的 password */
            private void writeObject(ObjectOutputStream out) throws IOException {
                out.defaultWriteObject();            // 写入非 transient 非 static 字段
                out.writeUTF(encrypt(password));    // 手动写入 password（加密）
            }

            /** 自定义反序列化：先默认读取，再解密 password */
            private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
                in.defaultReadObject();
                this.password = decrypt(in.readUTF());
            }

            private static String encrypt(String s) {
                return new StringBuilder(s).reverse().toString(); // 简单可逆混淆
            }

            private static String decrypt(String s) {
                return new StringBuilder(s).reverse().toString();
            }

            @Override
            public String toString() {
                return "User{name='" + name + "', age=" + age
                        + ", password='" + password + "', company='" + company + "'}";
            }
        }

        @SuppressWarnings("unchecked")
        public static void run() throws Exception {
            System.out.println("===== 演示1：Serializable =====");

            // 修改 static 字段，验证其不参与序列化
            User.company = "ModifiedCorp";

            User u = new User("Alice", 28, "p@ssw0rd");
            System.out.println("  原对象    : " + u);

            // 序列化
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(u);
            }
            byte[] data = baos.toByteArray();
            System.out.println("  序列化字节数: " + data.length);

            // 反序列化前先把 static 字段改回，看反序列化后是否会改变它（应该不会）
            User.company = "AfterSerializeCorp";

            User back;
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                back = (User) ois.readObject();
            }
            System.out.println("  反序列化后: " + back);

            System.out.println("  [结论]");
            System.out.println("    - transient 字段：默认不写入，但通过自定义 writeObject/readObject 可手动加入");
            System.out.println("    - static 字段  ：不参与序列化，反序列化后取当前 JVM 内的值");
            System.out.println("    - Serializable  ：不调用构造器，直接由 JVM 重建对象");
        }
    }

    // ============================================================
    // 演示2：Externalizable —— 强制实现读写方法
    // ============================================================
    static class ExternalizableDemo {

        public static class Order implements Externalizable {
            private static final long serialVersionUID = 1L;

            private String id;
            private BigDecimal amount;

            public Order() {} // Externalizable 反序列化会调用无参构造器！

            public Order(String id, BigDecimal amount) {
                this.id = id;
                this.amount = amount;
            }

            @Override
            public void writeExternal(ObjectOutput out) throws IOException {
                out.writeUTF(id);
                out.writeObject(amount);
            }

            @Override
            public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
                this.id = in.readUTF();
                this.amount = (BigDecimal) in.readObject();
            }

            @Override
            public String toString() {
                return "Order{id='" + id + "', amount=" + amount + "}";
            }
        }

        public static void run() throws Exception {
            System.out.println("\n===== 漓示2：Externalizable =====");

            Order o = new Order("ORD-2026-001", new BigDecimal("99.50"));
            System.out.println("  原对象    : " + o);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(o);
            }
            byte[] data = baos.toByteArray();
            System.out.println("  序列化字节数: " + data.length);

            Order back;
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                back = (Order) ois.readObject();
            }
            System.out.println("  反序列化后: " + back);

            System.out.println("  [结论]");
            System.out.println("    - 必须显式实现 writeExternal/readExternal，否则字段全为默认值");
            System.out.println("    - 反序列化会调用无参构造器（必须 public）");
            System.out.println("    - 完全控制写入内容，通常比 Serializable 字节更少、速度更快");
        }
    }

    // ============================================================
    // 演示3：Serializable vs Externalizable 对比
    // ============================================================
    static class CompareDemo {

        public static class SBean implements Serializable {
            private static final long serialVersionUID = 1L;
            private String id;
            private int qty;
            private BigDecimal price;

            public SBean() {}
            public SBean(String id, int qty, BigDecimal price) {
                this.id = id; this.qty = qty; this.price = price;
            }
            @Override public String toString() { return id + "/" + qty + "/" + price; }
        }

        public static class EBean implements Externalizable {
            private static final long serialVersionUID = 1L;
            private String id;
            private int qty;
            private BigDecimal price;

            public EBean() {}
            public EBean(String id, int qty, BigDecimal price) {
                this.id = id; this.qty = qty; this.price = price;
            }
            @Override public void writeExternal(ObjectOutput out) throws IOException {
                out.writeUTF(id); out.writeInt(qty); out.writeObject(price);
            }
            @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
                id = in.readUTF(); qty = in.readInt(); price = (BigDecimal) in.readObject();
            }
            @Override public String toString() { return id + "/" + qty + "/" + price; }
        }

        public static void run() throws Exception {
            System.out.println("\n===== 演示3：Serializable vs Externalizable 对比 =====");

            int count = 20000;
            SBean s = new SBean("S1", 10, new BigDecimal("12.34"));
            EBean e = new EBean("E1", 10, new BigDecimal("12.34"));

            // 预热
            roundTrip(s);
            roundTrip(e);

            // 字节大小
            byte[] sb = ser(s);
            byte[] eb = ser(e);
            System.out.println("  Serializable  字节数: " + sb.length);
            System.out.println("  Externalizable 字节数: " + eb.length);

            // 耗时对比
            long t1 = System.nanoTime();
            for (int i = 0; i < count; i++) roundTrip(s);
            long tSer = System.nanoTime() - t1;

            long t2 = System.nanoTime();
            for (int i = 0; i < count; i++) roundTrip(e);
            long tExt = System.nanoTime() - t2;

            System.out.println("  Serializable  " + count + " 次读写: " + (tSer / 1_000_000) + " ms");
            System.out.println("  Externalizable " + count + " 次读写: " + (tExt / 1_000_000) + " ms");

            System.out.println("  [结论]");
            System.out.println("    - Serializable：反射扫描字段，包含类描述/字段名，字节较大");
            System.out.println("    - Externalizable：直接方法调用，无字段元数据，字节更少、速度更快");
            System.out.println("    - 通用场景选 Serializable；对性能/格式敏感选 Externalizable");
        }

        private static byte[] ser(Object o) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) { oos.writeObject(o); }
            return baos.toByteArray();
        }

        private static Object roundTrip(Object o) throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) { oos.writeObject(o); }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                return ois.readObject();
            }
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 6.1 — Java 原生序列化 实战演示       ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        SerializableDemo.run();   // 演示1：Serializable
        ExternalizableDemo.run(); // 演示2：Externalizable
        CompareDemo.run();        // 演示3：对比

        System.out.println("\n===== 全部演示结束 =====");
    }
}
