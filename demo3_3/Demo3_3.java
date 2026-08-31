import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Demo 3.3 — 泛型与反射 实战演示
 *
 * 涵盖内容：
 *   3.3.1 通过反射获取方法泛型返回类型
 *   3.3.2 通过子类获取父类的泛型实参（运行时泛型信息保留处）
 *   3.3.3 字段泛型类型
 *
 * 编译：javac Demo3_3.java
 * 运行：java Demo3_3
 */
public class Demo3_3 {

    // 泛型父类：Repository<User> 由子类确定实参，这部分泛型信息以 Signature 属性保留到运行时
    static class Repository<T> {
        List<T> items;
        public List<T> findAll() { return items; }
        public T findById(long id) { return null; }
    }

    // 子类把 T 确定为 User —— 关键：泛型实参在此处保留
    static class UserRepository extends Repository<User> {}

    static class User {
        String name;
        User(String n) { this.name = n; }
        @Override public String toString() { return "User(" + name + ")"; }
    }

    // ============================================================
    // 演示1：方法泛型返回类型（getGenericReturnType）
    // ============================================================
    static class MethodGenericDemo {

        public static void run() throws Exception {
            System.out.println("===== 演示1：方法泛型返回类型 =====");

            Method findAll = Repository.class.getDeclaredMethod("findAll");
            Type t = findAll.getGenericReturnType(); // List<T> → ParameterizedType
            System.out.println("  Repository.findAll() getGenericReturnType = " + t
                    + "  (" + t.getClass().getSimpleName() + ")");
            if (t instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) t;
                System.out.println("    原始类型 rawType = " + pt.getRawType());
                Type[] args = pt.getActualTypeArguments();
                System.out.println("    类型参数 = " + args[0] + "  (" + args[0].getClass().getSimpleName() + ")");
                System.out.println("    → 父类中是 TypeVariable T，子类确定后才能拿到具体实参");
            }

            Method findById = Repository.class.getDeclaredMethod("findById", long.class);
            Type rt = findById.getGenericReturnType();
            System.out.println("  Repository.findById() genericReturnType = " + rt
                    + "  (" + rt.getClass().getSimpleName() + ")");
        }
    }

    // ============================================================
    // 演示2：父类泛型实参（子类保留）—— 运行时获取泛型的常用手段
    // ============================================================
    static class SuperclassGenericDemo {

        public static void run() {
            System.out.println("\n===== 漓示2：父类泛型实参（子类保留） =====");

            Type sup = UserRepository.class.getGenericSuperclass();
            System.out.println("  UserRepository.getGenericSuperclass = " + sup
                    + "  (" + sup.getClass().getSimpleName() + ")");
            if (sup instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) sup;
                Type[] args = pt.getActualTypeArguments();
                System.out.println("    父类泛型实参 = " + args[0]); // class User
                System.out.println("    rawType = " + pt.getRawType());
            }
            System.out.println("  [结论] 泛型信息仅在继承处/字段签名/方法签名以 Signature 属性保留；");
            System.out.println("         这是 Spring/Guice 等框架获取泛型实参的标准手段");
        }
    }

    // ============================================================
    // 演示3：字段泛型类型
    // ============================================================
    static class FieldGenericDemo {

        public static void run() throws Exception {
            System.out.println("\n===== 漓示3：字段泛型类型 =====");

            Field f = Repository.class.getDeclaredField("items");
            System.out.println("  Repository.items");
            System.out.println("    getGenericType() = " + f.getGenericType() + "  (List<T>)");
            System.out.println("    getType()         = " + f.getType().getName() + "  (擦除为 List)");
            System.out.println("  [说明] 字段声明处的泛型以 Signature 保留，元素类型参数仍是 TypeVariable T");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 3.3 — 泛型与反射 实战演示           ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        MethodGenericDemo.run();        // 演示1：方法泛型返回类型
        SuperclassGenericDemo.run();    // 演示2：父类泛型实参
        FieldGenericDemo.run();         // 演示3：字段泛型类型

        System.out.println("\n===== 全部演示结束 =====");
    }
}
