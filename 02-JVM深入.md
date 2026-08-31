# 2. JVM 深入

## 2.1 内存结构

### 2.1.1 运行时数据区

```
┌─────────────────────────────────────────────────────┐
│                    JVM 运行时数据区                  │
├──────────────┬──────────────┬───────────────────────┤
│  线程共享    │  线程共享    │     线程私有           │
├──────────────┼──────────────┼───────────────────────┤
│  方法区      │  堆          │  虚拟机栈             │
│ (MetaSpace)  │              │  本地方法栈           │
│              │              │  程序计数器           │
└──────────────┴──────────────┴───────────────────────┘
```

| 区域 | 线程共享 | 说明 |
|------|----------|------|
| **堆** | 是 | 存放对象实例和数组，GC主要区域 |
| **方法区** | 是 | 存储类信息、常量、静态变量（JDK8改为Metaspace） |
| **虚拟机栈** | 否 | 存储局部变量、操作数栈、动态链接、方法返回值 |
| **本地方法栈** | 否 | 为Native方法服务 |
| **程序计数器** | 否 | 当前线程执行的字节码行号指示器（唯一不会OOM的区域） |

### 2.1.2 对象内存布局

```java
// 对象内存结构
public class ObjectLayout {
    // Mark Word (8字节)：哈希码、GC年龄、锁状态、偏向线程ID
    // Klass Pointer (4/8字节)：指向方法区中类元数据
    // 实例数据：各字段值
    // 对齐填充：保证8字节对齐
}
```

**Mark Word 状态转换**：

| 锁状态 | Mark Word 存储内容 |
|--------|-------------------|
| 无锁 | 哈希码、GC年龄、01 |
| 偏向锁 | 偏向线程ID、epoch、GC年龄、01 |
| 轻量级锁 | 指向栈中锁记录的指针、00 |
| 重量级锁 | 指向Monitor的指针、10 |

---

## 2.2 垃圾回收

### 2.2.1 判断对象存活

**引用计数法**（JVM未采用）：
- 每个对象维护一个引用计数器，+1/-1
- 无法解决循环引用问题

**可达性分析算法**（JVM采用）：
- 从GC Roots出发，沿引用链遍历
- 不可达的对象判定为可回收
- **GC Roots**包括：
  - 虚拟机栈中的引用（局部变量）
  - 方法区中静态变量的引用
  - 方法区中常量的引用
  - 本地方法栈中JNI的引用

### 2.2.2 四种引用类型

```java
// 强引用：Object o = new Object()
// 不会被GC回收

// 软引用：SoftReference<Object>
// 内存不足时才被回收
SoftReference<Object> softRef = new SoftReference<>(obj);

// 弱引用：WeakReference<Object>
// 只要GC就会回收
WeakReference<Object> weakRef = new WeakReference<>(obj);

// 虚引用：PhantomReference<Object>
// 唯一作用：收到GC回收通知
PhantomReference<Object> phantomRef = new PhantomReference<>(obj, queue);
```

### 2.2.3 垃圾回收算法

| 算法 | 区域 | 说明 |
|------|------|------|
| **标记-清除** | 老年代 | 标记存活对象，清除未标记对象（产生碎片） |
| **复制算法** | 新生代 | 将存活对象复制到另一块区域（主流） |
| **标记-整理** | 老年代 | 标记后整理存活对象到一端（无碎片） |
| **分代收集** | 全堆 | 根据各代特点采用不同算法 |

**分代收集理论**：
- **新生代**（Eden + S0 + S1）：对象朝生夕灭，使用复制算法
- **老年代**：对象存活率高，使用标记-清除或标记-整理

### 2.2.4 垃圾收集器

| 收集器 | 类型 | 算法 | 适用场景 |
|--------|------|------|----------|
| **Serial** | 单线程 | 复制算法 | 客户端模式、小内存 |
| **ParNew** | 多线程 | 复制算法 | 配合CMS |
| **Parallel Scavenge** | 多线程 | 复制算法 | 吞吐量优先 |
| **CMS** | 并发 | 标记-清除 | 低延迟（已废弃） |
| **G1** | 并发 | Region分区 | JDK 9+默认 |
| **ZGC** | 并发 | 着色指针+读屏障 | 亚毫秒延迟 |
| **Shenandoah** | 并发 | Brooks Pointer | 低延迟 |

**G1 收集器核心**：
- 将堆划分为等大小的`Region`（1MB~32MB）
- 维护四个Region集合：`Eden`、`Survivor`、`Old`、`Humongous`
- 可预测停顿模型：`-XX:MaxGCPauseMillis=200`

```bash
# G1 常用JVM参数
-XX:+UseG1GC                    # 使用G1收集器
-XX:MaxGCPauseMillis=200        # 目标停顿时间
-XX:G1HeapRegionSize=8m         # Region大小
-XX:G1HeapMetaspaceSize=256m    # Metaspace大小
-XX:+HeapDumpOnOutOfMemoryError # OOM时生成dump
```

---

## 2.3 类加载机制

### 2.3.1 类的生命周期

```
加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载
      └──────────────链接──────────────┘
```

| 阶段 | 说明 |
|------|------|
| **加载** | 通过全限定名获取二进制字节流 |
| **验证** | 检查字节流正确性、安全性 |
| **准备** | 为静态变量分配内存并赋零值 |
| **解析** | 将符号引用替换为直接引用 |
| **初始化** | 执行`<clinit>()`方法（静态变量赋值、静态代码块） |

### 2.3.2 类加载器

```java
// 启动类加载器（Bootstrap ClassLoader）
// - 加载rt.jar等核心类库
// - 由C++实现，Java中无法获取引用

// 扩展类加载器（Extension ClassLoader）
// - 加载jre/lib/ext目录
// - 已被Platform ClassLoader替代（JDK 9+）

// 应用类加载器（App ClassLoader）
// - 加载classpath指定的类
// - 开发者常用

// 自定义类加载器
public class CustomClassLoader extends ClassLoader {
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 自定义加载逻辑
        byte[] classData = loadClassData(name);
        if (classData == null) throw new ClassNotFoundException();
        return defineClass(name, classData, 0, classData.length);
    }
    
    private byte[] loadClassData(String className) {
        // 从自定义来源读取字节码
    }
}
```

### 2.3.3 双亲委派模型

类加载过程：
1. 当前ClassLoader收到加载请求
2. 委托给父ClassLoader加载
3. 父ClassLoader无法加载时，才由自己加载

**破坏双亲委派**的场景：
- SPI机制（JDBC、JNDI）：使用线程上下文类加载器
- OSGi模块化：每个Bundle有自己的ClassLoader
- 热部署：Tomcat每个WebApp独立ClassLoader
- 自定义ClassLoader：加密、热替换

---

## 2.4 字节码与 Instrumentation

```java
// 使用 Instrumentation 修改字节码
public class ClassTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer
    ) {
        // 使用ASM/Javassist修改字节码
        return classfileBuffer;
    }
}

// agentmain 方法
public static void agentmain(String agentArgs, Instrumentation inst) {
    inst.addTransformer(new ClassTransformer(), true);
    // 重新加载已加载的类
    inst.retransformClasses(TargetClass.class);
}
```

---

> [← 返回目录](SUMMARY.md) | [上一章：并发编程 →](01-并发编程.md) | [下一章：泛型与类型系统 →](03-泛型与类型系统.md)