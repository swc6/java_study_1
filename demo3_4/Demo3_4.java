import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Demo 3.4 — 桥方法 实战演示
 *
 * 涵盖内容：
 *   3.4.1 类型擦除导致的方法签名冲突
 *   3.4.2 编译器自动生成桥方法（bridge method）
 *   3.4.3 多态调用经桥方法转发
 *
 * 编译：javac Demo3_4.java
 * 运行：java Demo3_4
 */
public class Demo3_4 {

    // 泛型父类：setData(T) / getData():T 擦除后为 setData(Object) / getData():Object
    static class Node<T> {
        T data;
        public void setData(T data) { this.data = data; }
        public T getData() { return data; }
    }

    // 子类把 T 确定为 String，协变返回 String
    static class StringNode extends Node<String> {
        @Override public void setData(String data) { super.setData(data); }
        @Override public String getData() { return super.getData(); }
    }

    // ============================================================
    // 演示1：反射查看桥方法
    // ============================================================
    static class BridgeDemo {

        public static void run() throws Exception {
            System.out.println("===== 演示：桥方法(bridge method) =====");

            StringNode sn = new StringNode();
            sn.setData("hello");
            System.out.println("  sn.setData(\"hello\"); sn.getData() = " + sn.getData());

            System.out.println("  StringNode 的方法列表（含编译器生成的桥方法）：");
            for (Method m : StringNode.class.getDeclaredMethods()) {
                System.out.println("    " + m.getName()
                        + Arrays.toString(m.getParameterTypes())
                        + " -> " + m.getReturnType().getSimpleName()
                        + (m.isBridge() ? "  [桥方法 bridge]" : "")
                        + (m.isSynthetic() ? "  [合成 synthetic]" : ""));
            }
            System.out.println("  [说明]");
            System.out.println("    setData(String) void     普通方法");
            System.out.println("    getData()    String      普通方法（协变返回）");
            System.out.println("    setData(Object) void [桥]  编译器生成，内部调用 setData(String)");
            System.out.println("    getData()    Object [桥]  编译器生成，内部调用 getData() 并返回 String");
        }
    }

    // ============================================================
    // 演示2：多态调用经桥方法转发
    // ============================================================
    static class PolymorphismDemo {

        public static void run() throws Exception {
            System.out.println("\n===== 漓示2：多态调用经桥方法转发 =====");

            StringNode sn = new StringNode();
            // 直接调用桥方法 setData(Object) —— 它会转发到 setData(String)
            Method bridgeSet = StringNode.class.getDeclaredMethod("setData", Object.class);
            System.out.println("  桥方法 setData(Object) isBridge = " + bridgeSet.isBridge()
                    + "  isSynthetic = " + bridgeSet.isSynthetic());
            bridgeSet.invoke(sn, "via bridge");
            System.out.println("  通过桥方法 setData(Object) 写入后 getData() = " + sn.getData());

            // 取出 getData() 桥方法（返回 Object）
            Method bridgeGet = null;
            for (Method m : StringNode.class.getDeclaredMethods()) {
                if (m.getName().equals("getData") && m.isBridge()) bridgeGet = m;
            }
            if (bridgeGet != null) {
                Object result = bridgeGet.invoke(sn);
                System.out.println("  桥方法 getData() 返回类型 = " + bridgeGet.getReturnType().getSimpleName()
                        + "，实际值 = " + result);
            }

            System.out.println("  [结论] 桥方法保证用父类引用指向子类时，");
            System.out.println("         按擦除签名(Object)调用能正确转发到子类具体方法(String)");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 3.4 — 桥方法 实战演示               ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        BridgeDemo.run();         // 演示1：反射查看桥方法
        PolymorphismDemo.run();   // 演示2：多态调用经桥方法转发

        System.out.println("\n===== 全部演示结束 =====");
    }
}
