import java.util.ArrayList;
import java.util.List;

/**
 * Demo 7.3 — 行为型模式 实战演示
 *
 * 涵盖内容：
 *   7.3.1 策略模式（运行时切换算法）
 *   7.3.2 观察者模式（一对多通知）
 *   7.3.3 模板方法模式（流程固定，步骤可变）
 *   7.3.4 责任链模式（请求多环节处理）
 *
 * 编译：javac Demo7_3.java
 * 运行：java Demo7_3
 */
public class Demo7_3 {

    // ============================================================
    // 演示1：策略模式
    // ============================================================
    static class StrategyDemo {

        interface PaymentStrategy { void pay(double amount); }

        static class Alipay implements PaymentStrategy {
            public void pay(double amount) {
                System.out.println("    [支付宝] 扣款 " + amount + " 元");
            }
        }
        static class WechatPay implements PaymentStrategy {
            public void pay(double amount) {
                System.out.println("    [微信支付] 扣款 " + amount + " 元");
            }
        }
        static class CardPay implements PaymentStrategy {
            public void pay(double amount) {
                System.out.println("    [银行卡] 扣款 " + amount + " 元");
            }
        }

        // 上下文：持有策略，可运行时切换
        static class PaymentContext {
            private PaymentStrategy strategy;
            public void setStrategy(PaymentStrategy s) { this.strategy = s; }
            public void checkout(double amount) {
                if (strategy == null) throw new IllegalStateException("未选择支付方式");
                strategy.pay(amount);
            }
        }

        public static void run() {
            System.out.println("===== 漓示1：策略模式 =====");

            PaymentContext ctx = new PaymentContext();
            ctx.setStrategy(new Alipay());
            ctx.checkout(100);

            ctx.setStrategy(new WechatPay());
            ctx.checkout(200);

            ctx.setStrategy(new CardPay());
            ctx.checkout(300);

            System.out.println("  [结论]");
            System.out.println("    - 算法族封装为独立类，可运行时切换，避免大量 if-else");
            System.out.println("    - 新增策略不需改上下文，符合开闭原则");
        }
    }

    // ============================================================
    // 演示2：观察者模式
    // ============================================================
    static class ObserverDemo {

        interface Observer { void update(String event); }

        // 主题：维护观察者列表
        static class Subject {
            private final List<Observer> observers = new ArrayList<>();
            public void attach(Observer o) { observers.add(o); }
            public void detach(Observer o) { observers.remove(o); }
            public void notify(String event) {
                for (Observer o : observers) o.update(event);
            }
        }

        public static void run() {
            System.out.println("\n===== 漓示2：观察者模式 =====");

            Subject subject = new Subject();
            Observer email = e -> System.out.println("    [邮件] 收到: " + e);
            Observer sms   = e -> System.out.println("    [短信] 收到: " + e);
            Observer log   = e -> System.out.println("    [日志] 记录: " + e);

            subject.attach(email);
            subject.attach(sms);
            subject.attach(log);

            System.out.println("  发布事件: 订单创建");
            subject.notify("订单 #1001 创建");

            subject.detach(sms);
            System.out.println("  发布事件: 订单付款");
            subject.notify("订单 #1001 付款");

            System.out.println("  [结论]");
            System.out.println("    - 主题状态变化时自动通知所有观察者，解耦发布者与订阅者");
            System.out.println("    - 推模型（推送事件）/ 拉模型（观察者主动取数据）");
            System.out.println("    - 应用：事件总线、消息队列、GUI 监听器");
        }
    }

    // ============================================================
    // 演示3：模板方法模式
    // ============================================================
    static class TemplateDemo {

        // 抽象模板：固定流程，关键步骤延迟到子类
        abstract static class Processor {
            /** final 防止子类篡改流程 */
            public final void process() {
                init();
                doProcess();   // 钩子方法，子类实现
                cleanup();
            }
            protected void init()    { System.out.println("    [模板] 初始化资源"); }
            protected void cleanup() { System.out.println("    [模板] 释放资源"); }
            protected abstract void doProcess();
        }

        static class DataProcessor extends Processor {
            protected void doProcess() { System.out.println("    [数据] 解析数据文件"); }
        }
        static class ImageProcessor extends Processor {
            protected void doProcess() { System.out.println("    [图像] 缩放/压缩"); }
        }

        public static void run() {
            System.out.println("\n===== 漓示3：模板方法模式 =====");

            new DataProcessor().process();
            new ImageProcessor().process();

            System.out.println("  [结论]");
            System.out.println("    - 父类定义算法骨架，子类实现具体步骤");
            System.out.println("    - 复用流程，避免重复代码");
            System.out.println("    - 典型：AbstractList、HttpServlet、Spring JdbcTemplate");
        }
    }

    // ============================================================
    // 演示4：责任链模式
    // ============================================================
    static class ChainDemo {

        static class Request {
            private final String user;
            private final boolean authed;
            private final boolean permitted;
            private final String body;
            public Request(String u, boolean a, boolean p, String b) {
                user = u; authed = a; permitted = p; body = b;
            }
            public String user()      { return user; }
            public boolean authed()   { return authed; }
            public boolean permitted(){ return permitted; }
            public String body()      { return body; }
        }

        abstract static class Handler {
            protected Handler next;
            public Handler setNext(Handler n) { this.next = n; return n; }
            public abstract void handle(Request req);
        }

        static class AuthHandler extends Handler {
            public void handle(Request req) {
                if (!req.authed()) {
                    System.out.println("    [认证] 失败 - " + req.user());
                    return;  // 链终止
                }
                System.out.println("    [认证] 通过");
                if (next != null) next.handle(req);
            }
        }
        static class PermHandler extends Handler {
            public void handle(Request req) {
                if (!req.permitted()) {
                    System.out.println("    [授权] 失败 - 权限不足");
                    return;
                }
                System.out.println("    [授权] 通过");
                if (next != null) next.handle(req);
            }
        }
        static class BizHandler extends Handler {
            public void handle(Request req) {
                System.out.println("    [业务] 处理请求: " + req.body());
            }
        }

        public static void run() {
            System.out.println("\n===== 漓示4：责任链模式 =====");

            // 组装链
            Handler chain = new AuthHandler();
            chain.setNext(new PermHandler()).setNext(new BizHandler());

            System.out.println("  场景1: 全部通过");
            chain.handle(new Request("alice", true, true, "查询订单"));

            System.out.println("  场景2: 认证失败");
            chain.handle(new Request("bob", false, true, "查询订单"));

            System.out.println("  场景3: 授权失败");
            chain.handle(new Request("carol", true, false, "删除用户"));

            System.out.println("  [结论]");
            System.out.println("    - 请求沿链传递，每个处理器决定处理/转发/终止");
            System.out.println("    - 解耦发送者与接收者");
            System.out.println("    - 典型：Servlet Filter、Spring Interceptor、Netty Pipeline");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 7.3 — 行为型模式 实战演示            ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        StrategyDemo.run();    // 演示1：策略
        ObserverDemo.run();    // 演示2：观察者
        TemplateDemo.run();    // 演示3：模板方法
        ChainDemo.run();       // 演示4：责任链

        System.out.println("\n===== 全部演示结束 =====");
    }
}
