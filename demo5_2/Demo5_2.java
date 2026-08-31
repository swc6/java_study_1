import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demo 5.2 — NIO 核心组件 实战演示
 *
 * 涵盖内容：
 *   5.2.1 Buffer（position/limit/capacity、flip/clear/compact、mark/reset、直接内存）
 *   5.2.2 Channel（FileChannel 文件复制）
 *   5.2.3 Selector（多路复用回显服务）
 *
 * 编译：javac Demo5_2.java
 * 运行：java Demo5_2
 */
public class Demo5_2 {

    // ============================================================
    // 演示1：Buffer
    // ============================================================
    static class BufferDemo {

        public static void run() {
            System.out.println("===== 演示1：Buffer =====");

            ByteBuffer buf = ByteBuffer.allocate(16);
            System.out.println("  allocate: position=" + buf.position()
                    + " limit=" + buf.limit() + " capacity=" + buf.capacity());

            buf.put("Hello".getBytes());
            System.out.println("  put 后  : position=" + buf.position() + " limit=" + buf.limit());

            buf.flip(); // 写→读
            System.out.println("  flip 后: position=" + buf.position() + " limit=" + buf.limit());
            byte[] dst = new byte[buf.remaining()];
            buf.get(dst);
            System.out.println("  读出: " + new String(dst));

            buf.clear();
            System.out.println("  clear 后: position=" + buf.position() + " limit=" + buf.limit());

            // compact：把未读数据移到开头，准备继续写入
            ByteBuffer c = ByteBuffer.allocate(8);
            c.put("ABC".getBytes()).put("DE".getBytes()); // position=5
            c.flip();                                      // position=0 limit=5
            c.get();                                       // 读掉 'A'，position=1
            c.compact();                                   // "BCDE" 移到前，position=4 limit=8
            System.out.println("  compact 后: position=" + c.position() + " limit=" + c.limit());

            // mark / reset
            ByteBuffer m = ByteBuffer.allocate(8);
            m.put(new byte[]{1, 2, 3}).flip();
            m.mark();
            m.get(); m.get();
            m.reset();
            System.out.println("  mark/reset 后 position=" + m.position()); // 1

            // 堆内 vs 堆外（直接内存）
            ByteBuffer heap = ByteBuffer.allocate(16);
            ByteBuffer direct = ByteBuffer.allocateDirect(16);
            System.out.println("  heap.isDirect=" + heap.isDirect()
                    + "  direct.isDirect=" + direct.isDirect());
            System.out.println("  [结论] position ≤ limit ≤ capacity；direct 减少一次内核拷贝，适合 IO");
        }
    }

    // ============================================================
    // 演示2：Channel（FileChannel 文件复制）
    // ============================================================
    static class ChannelDemo {

        public static void run() throws IOException {
            System.out.println("\n===== 漓示2：Channel（FileChannel 文件复制） =====");

            Path src = Files.createTempFile("demo5_2_src", ".txt");
            Path dst = Files.createTempFile("demo5_2_dst", ".txt");
            Files.write(src, "Hello NIO Channel".getBytes());

            try (FileChannel in = FileChannel.open(src, StandardOpenOption.READ);
                 FileChannel out = FileChannel.open(dst, StandardOpenOption.WRITE)) {
                ByteBuffer buf = ByteBuffer.allocate(8192);
                while (in.read(buf) != -1) {
                    buf.flip();
                    out.write(buf);
                    buf.clear();
                }
            }

            System.out.println("  源  : " + new String(Files.readAllBytes(src)));
            System.out.println("  目标: " + new String(Files.readAllBytes(dst)));
            Files.deleteIfExists(src);
            Files.deleteIfExists(dst);
            System.out.println("  [说明] transferTo(in.position, count, out) 可零拷贝直达，更高效");
        }
    }

    // ============================================================
    // 演示3：Selector（一个线程管理多个连接）
    // ============================================================
    static class SelectorDemo {

        public static void run() throws Exception {
            System.out.println("\n===== 漓示3：Selector（多路复用） =====");

            Selector selector = Selector.open();
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking(false);
            ssc.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = ((InetSocketAddress) ssc.getLocalAddress()).getPort();
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("  NIO 服务端监听 127.0.0.1:" + port);

            AtomicBoolean running = new AtomicBoolean(true);
            Thread serverThread = new Thread(() -> {
                while (running.get()) {
                    try {
                        if (selector.select(300) == 0) continue; // 300ms 无事件，回去检查 running
                        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey key = it.next();
                            it.remove();
                            if (key.isAcceptable()) {
                                SocketChannel sc = ssc.accept();
                                sc.configureBlocking(false);
                                sc.register(selector, SelectionKey.OP_READ);
                                System.out.println("    [server] 接受新连接");
                            } else if (key.isReadable()) {
                                SocketChannel sc = (SocketChannel) key.channel();
                                ByteBuffer buf = ByteBuffer.allocate(64);
                                int n = sc.read(buf);
                                if (n == -1) { sc.close(); continue; }
                                buf.flip();
                                System.out.println("    [server] 读到: " + new String(buf.array(), 0, buf.remaining()));
                                buf.rewind();
                                sc.write(buf); // 回显
                                sc.close();
                            }
                        }
                    } catch (IOException e) {
                        break;
                    }
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            // 客户端：连接、写、读
            try (SocketChannel client = SocketChannel.open(new InetSocketAddress("127.0.0.1", port))) {
                client.write(ByteBuffer.wrap("hello NIO".getBytes()));
                ByteBuffer resp = ByteBuffer.allocate(64);
                client.read(resp);
                resp.flip();
                System.out.println("  客户端收到回显: " + new String(resp.array(), 0, resp.remaining()));
            }

            Thread.sleep(100);
            running.set(false);
            selector.close();
            ssc.close();
            System.out.println("  [结论] 一个线程 + Selector 监听多个 Channel 的就绪事件，线程数远小于连接数");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 5.2 — NIO 核心组件 实战演示         ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        BufferDemo.run();    // 演示1：Buffer
        ChannelDemo.run();    // 演示2：Channel
        SelectorDemo.run();   // 演示3：Selector

        System.out.println("\n===== 全部演示结束 =====");
    }
}
