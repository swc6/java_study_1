# 5. NIO 与网络编程

## 5.1 IO 模型演进

| 模型 | 说明 | 线程数 |
|------|------|--------|
| **BIO** (Blocking IO) | 每个连接一个线程，同步阻塞 | 1:1 |
| **NIO** (Non-blocking IO) | 多路复用，同步非阻塞 | 少量线程 |
| **AIO** (Asynchronous IO) | 异步非阻塞 | 回调通知 |

```
BIO:    Thread1 → Conn1 (阻塞等待)
        Thread2 → Conn2 (阻塞等待)
        Thread3 → Conn3 (阻塞等待)
        线程数 = 连接数

NIO:    Selector → 监听所有Channel
        一个线程处理多个连接
        线程数远小于连接数
```

---

## 5.2 NIO 核心组件

### 5.2.1 Buffer

```java
public class BufferExample {
    public static void main(String[] args) {
        // 创建Buffer
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        // 直接内存（堆外）
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        
        // 写入模式
        buffer.put("Hello".getBytes());
        
        // 切换到读取模式
        buffer.flip();
        
        // 读取
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        
        // 清空（准备再次写入）
        buffer.clear();
        
        // 压缩（将未读数据移到开头）
        buffer.compact();
        
        // 标记/重置
        buffer.mark();
        // ...读取若干数据...
        buffer.reset(); // 回到mark位置
    }
}
```

**Buffer 核心属性**：
```
┌────────────────────────────────────────┐
│ position ≤ limit ≤ capacity            │
│                                        │
│ [ 已读 ][ 可写 ][ 未使用 ]             │
│        ↑        ↑                      │
│      position  limit                   │
└────────────────────────────────────────┘
```

### 5.2.2 Channel

```java
public class ChannelExample {
    public static void fileCopy(String src, String dest) throws IOException {
        // 文件复制使用Channel
        try (FileChannel srcChannel = new FileInputStream(src).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            
            while (srcChannel.read(buffer) != -1) {
                buffer.flip();
                destChannel.write(buffer);
                buffer.clear();
            }
        }
    }
}
```

### 5.2.3 Selector

```java
public class NioServer {
    public void start(int port) throws IOException {
        // 1. 打开Selector
        Selector selector = Selector.open();
        
        // 2. 打开ServerSocketChannel并注册到Selector
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        while (true) {
            // 3. 选择就绪的Channel
            selector.select();
            
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();
            
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();
                
                if (key.isAcceptable()) {
                    // 接受新连接
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    SocketChannel client = server.accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ);
                } else if (key.isReadable()) {
                    // 读取数据
                    SocketChannel client = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    client.read(buffer);
                    // 处理数据...
                }
            }
        }
    }
}
```

---

## 5.3 Path 与 Files

```java
public class NioFileOperations {
    public static void main(String[] args) throws IOException {
        // Path 操作
        Path path = Paths.get("data", "input.txt");
        Path absolutePath = path.toAbsolutePath();
        Path normalizedPath = path.normalize();
        
        // Files 工具类
        // 读取所有行
        List<String> lines = Files.readAllLines(path);
        
        // 写入文件
        Files.write(path, "content".getBytes(), StandardOpenOption.CREATE);
        
        // 创建目录
        Files.createDirectories(Paths.get("output"));
        
        // 文件复制
        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
        
        // 遍历目录
        try (Stream<Path> walk = Files.walk(Paths.get("."))) {
            walk.filter(Files::isRegularFile)
                .forEach(System.out::println);
        }
    }
}
```

---

> [← 返回目录](SUMMARY.md) | [上一章：反射与注解 →](04-反射与注解.md) | [下一章：序列化 →](06-序列化.md)