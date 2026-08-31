import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 5.1 — IO 模型演进 实战演示
 *
 * 涵盖内容：
 *   5.1.1 BIO / NIO / AIO 模型对比
 *   5.1.2 BIO 实战（一连接一线程，回显服务）
 *
 * 编译：javac Demo5_1.java
 * 运行：java Demo5_1
 */
public class Demo5_1 {

    // ============================================================
    // 演示1：IO 模型对比
    // ============================================================
    static class ModelCompareDemo {

        public static void run() {
            System.out.println("===== 演示1：IO 模型对比 =====");
            System.out.println("  ┌──────────┬──────────────┬──────────┐");
            System.out.println("  │ 模型     │ 说明         │ 线程数   │");
            System.out.println("  ├──────────┼──────────────┼──────────┤");
            System.out.println("  │ BIO      │ 同步阻塞      │ 1:1      │");
            System.out.println("  │ NIO      │ 多路复用      │ 少量     │");
            System.out.println("  │ AIO      │ 异步非阻塞    │ 回调通知 │");
            System.out.println("  └──────────┴──────────────┴──────────┘");
            System.out.println("  BIO:  每连接一线程，accept/read 阻塞，线程数 = 连接数");
            System.out.println("  NIO:  一个线程 + Selector 管理多个 Channel（见 Demo5_2）");
            System.out.println("  AIO:  由操作系统回调通知（Windows IOCP / Linux epoll 仍有争议）");
        }
    }

    // ============================================================
    // 演示2：BIO 实战（一连接一线程的回显服务）
    // ============================================================
    static class BioEchoDemo {

        public static void run() throws Exception {
            System.out.println("\n===== 漓示2：BIO 实战（一连接一线程） =====");

            ServerSocket server = new ServerSocket(0);   // 0 → OS 分配空闲端口
            int port = server.getLocalPort();
            server.setSoTimeout(2000);                    // accept 超时，避免永久阻塞
            System.out.println("  服务端监听 127.0.0.1:" + port + "（BIO）");

            AtomicBoolean running = new AtomicBoolean(true);
            AtomicInteger connCount = new AtomicInteger(0);

            // 服务线程：accept 到连接就新开一个线程处理（1:1 模型）
            Thread serverThread = new Thread(() -> {
                while (running.get()) {
                    Socket conn;
                    try {
                        conn = server.accept();
                    } catch (SocketTimeoutException ste) {
                        continue; // 超时后回去检查 running
                    } catch (IOException e) {
                        break;   // server 关闭等异常，退出
                    }
                    connCount.incrementAndGet();
                    Thread handler = new Thread(() -> handle(conn));
                    handler.setDaemon(true);
                    handler.start();
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            // 客户端：连接、发送、接收
            try (Socket client = new Socket("127.0.0.1", port)) {
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out.println("hello BIO");
                String resp = in.readLine();
                System.out.println("  客户端发送 \"hello BIO\"，收到回显: " + resp);
            }

            Thread.sleep(100);   // 等服务端处理完
            running.set(false);
            server.close();

            System.out.println("  服务端共处理连接数: " + connCount.get());
            System.out.println("  [结论] BIO 下每个连接独占一个线程，连接数=线程数，");
            System.out.println("         高并发时线程上下文切换开销巨大 → 演进到 NIO");
        }

        private static void handle(Socket conn) {
            try (Socket c = conn;
                 BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
                 PrintWriter out = new PrintWriter(c.getOutputStream(), true)) {
                String line = in.readLine();
                if (line != null) out.println("echo: " + line);
            } catch (IOException ignore) {
            }
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 5.1 — IO 模型演进 实战演示         ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        ModelCompareDemo.run(); // 演示1：模型对比
        BioEchoDemo.run();      // 演示2：BIO 实战

        System.out.println("\n===== 全部演示结束 =====");
    }
}
