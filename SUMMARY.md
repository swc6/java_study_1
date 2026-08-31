# Java 高级编程文档

> 本文档面向已有Java基础的开发者，涵盖并发编程、JVM、泛型、反射、NIO、序列化、设计模式与性能优化等核心主题。

---

## 目录

| 序号 | 章节 | 文件 |
|------|------|------|
| 1 | 并发编程 | [01-并发编程.md](01-并发编程.md) |
| 2 | JVM 深入 | [02-JVM深入.md](02-JVM深入.md) |
| 3 | 泛型与类型系统 | [03-泛型与类型系统.md](03-泛型与类型系统.md) |
| 4 | 反射与注解 | [04-反射与注解.md](04-反射与注解.md) |
| 5 | NIO 与网络编程 | [05-NIO与网络编程.md](05-NIO与网络编程.md) |
| 6 | 序列化 | [06-序列化.md](06-序列化.md) |
| 7 | 设计模式 | [07-设计模式.md](07-设计模式.md) |
| 8 | 性能优化 | [08-性能优化.md](08-性能优化.md) |

---

## 附录：Java 并发速查表

### 常用同步工具

| 工具类 | 类型 | 用途 |
|--------|------|------|
| `CountDownLatch` | AQS共享 | 等待多任务完成 |
| `CyclicBarrier` | ReentrantLock+Condition | 循环屏障 |
| `Semaphore` | AQS共享 | 并发控制 |
| `Phaser` | 扩展AQS | 多阶段同步 |
| `Exchanger` | CAS+ExchangeNode | 两线程数据交换 |
| `ReadWriteLock` | AQS | 读写分离锁 |
| `StampedLock` | 无锁读+CAS | 乐观读+写锁 |

### 常用并发容器

| 容器 | 特性 |
|------|------|
| `ConcurrentHashMap` | 高并发Map |
| `ConcurrentSkipListMap` | 有序并发Map |
| `ConcurrentLinkedQueue` | 无界并发队列 |
| `CopyOnWriteArrayList` | 写时复制List |
| `BlockingQueue` 实现 | 阻塞队列 |
| `ConcurrentHashMap` 的 `computeIfAbsent` | 原子计算 |

### 线程池配置建议

```
CPU 密集型：核心线程数 = CPU 核心数 + 1
IO 密集型：核心线程数 = CPU 核心数 × 2 + 磁盘数

队列选择：
- 优先使用无界队列（LinkedBlockingQueue）确保任务不丢
- 高吞吐场景使用有界队列避免OOM
- 对实时性要求高使用SynchronousQueue + 最大线程数
```

---

> 文档版本：1.0 | 更新日期：2026-08-12