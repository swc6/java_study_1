import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo 4.2 — 注解 实战演示
 *
 * 涵盖内容：
 *   4.2.1 自定义注解（元注解 @Target / @Retention / @Documented）
 *   4.2.2 Retention 策略（SOURCE / CLASS / RUNTIME）
 *   4.2.3 运行时注解处理（反射读取 + 模拟 ORM）
 *   4.2.4 编译时注解处理器（APT）原理
 *
 * 编译：javac Demo4_2.java
 * 运行：java Demo4_2
 */
public class Demo4_2 {

    // ---- 自定义注解 ----
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Table { String value(); }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Column { String name() default ""; }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation {
        String value() default "";
        int priority() default 0;
        String[] tags() default {};
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface SourceAnno {}

    @Retention(RetentionPolicy.CLASS)
    public @interface ClassAnno {}

    // 应用注解的样本类
    @Table("t_user")
    @MyAnnotation(value = "用户表", priority = 1, tags = {"db", "entity"})
    @SourceAnno
    @ClassAnno
    public static class User {
        @Column(name = "user_name")
        @MyAnnotation("主标识")
        private String name;

        @Column(name = "user_age")
        private int age;

        User(String name, int age) { this.name = name; this.age = age; }
    }

    // ============================================================
    // 演示1：Retention 策略 —— 运行时只有 RUNTIME 注解可见
    // ============================================================
    static class RetentionDemo {

        public static void run() {
            System.out.println("===== 演示1：Retention 策略 =====");

            System.out.println("  User 上的注解（运行时可见）：");
            for (Annotation a : User.class.getAnnotations()) {
                System.out.println("    " + a.annotationType().getSimpleName()
                        + "  (" + a.annotationType().getAnnotation(Retention.class).value() + ")");
            }
            System.out.println("  [说明]");
            System.out.println("    SOURCE：仅源码，编译丢弃 → @SourceAnno 不可见");
            System.out.println("    CLASS ：存于 .class，运行时丢弃 → @ClassAnno 不可见");
            System.out.println("    RUNTIME：运行时保留 → @Table/@MyAnnotation 可反射读取");
        }
    }

    // ============================================================
    // 演示2：运行时注解读取（含注解成员值）
    // ============================================================
    static class AnnotationReadDemo {

        public static void run() {
            System.out.println("\n===== 漓示2：运行时注解读取 =====");

            MyAnnotation ma = User.class.getAnnotation(MyAnnotation.class);
            if (ma != null) {
                System.out.println("  @MyAnnotation value    = " + ma.value());
                System.out.println("  @MyAnnotation priority = " + ma.priority());
                System.out.println("  @MyAnnotation tags     = " + java.util.Arrays.toString(ma.tags()));
            }

            Table t = User.class.getAnnotation(Table.class);
            System.out.println("  @Table value = " + (t != null ? t.value() : "(无)"));
        }
    }

    // ============================================================
    // 演示3：模拟 ORM —— 由注解生成查询 SQL
    // ============================================================
    static class OrmDemo {

        public static String buildSelect(Class<?> clazz) {
            Table table = clazz.getAnnotation(Table.class);
            String tableName = table != null ? table.value() : clazz.getSimpleName().toLowerCase();

            List<String> columns = new ArrayList<>();
            for (Field f : clazz.getDeclaredFields()) {
                Column c = f.getAnnotation(Column.class);
                if (c != null) {
                    columns.add(c.name().isEmpty() ? f.getName() : c.name());
                }
            }
            return "SELECT " + String.join(", ", columns) + " FROM " + tableName;
        }

        public static void run() {
            System.out.println("\n===== 漓示3：模拟 ORM（注解 → SQL） =====");
            System.out.println("  " + buildSelect(User.class));
            System.out.println("  [结论] Spring/JPA/MyBatis 用注解描述元数据，运行时反射解析并据此工作");
        }
    }

    // ============================================================
    // 演示4：编译时注解处理器（APT）原理
    // ============================================================
    static class AptDemo {

        public static void run() {
            System.out.println("\n===== 漓示4：编译时注解处理器（APT）原理 =====");
            System.out.println("  编译期由 javac 调用 javax.annotation.processing.Processor：");
            System.out.println("    @SupportedAnnotationTypes(\"com.example.MyAnnotation\")");
            System.out.println("    class MyProcessor extends AbstractProcessor {");
            System.out.println("        boolean process(Set<? extends TypeElement> annos, RoundEnvironment env) {");
            System.out.println("            // roundEnv.getElementsAnnotatedWith(MyAnnotation.class)");
            System.out.println("            // 生成新源码/字节码（如 Lombok @Data、Dagger @Module）");
            System.out.println("        }");
            System.out.println("    }");
            System.out.println("  [对比]");
            System.out.println("    运行时注解：反射读取，有运行期开销（本演示 demo4_2 的方式）");
            System.out.println("    编译时注解：编译期生成代码，无运行期反射开销（Lombok/MapStruct）");
            System.out.println("  [说明] APT 需 javax.annotation.processing，须打成 jar 用 -processor 加载");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 4.2 — 注解 实战演示                 ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        RetentionDemo.run();      // 演示1：Retention 策略
        AnnotationReadDemo.run(); // 演示2：运行时注解读取
        OrmDemo.run();           // 演示3：模拟 ORM
        AptDemo.run();           // 演示4：APT 原理

        System.out.println("\n===== 全部演示结束 =====");
    }
}
