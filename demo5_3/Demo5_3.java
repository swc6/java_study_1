import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Demo 5.3 — Path 与 Files 实战演示
 *
 * 涵盖内容：
 *   5.3.1 Path 操作（组合/绝对化/规范化/resolve/relativize/subpath）
 *   5.3.2 Files 工具类（写/读/复制/建目录/遍历）
 *
 * 编译：javac Demo5_3.java
 * 运行：java Demo5_3
 */
public class Demo5_3 {

    // ============================================================
    // 演示1：Path 操作
    // ============================================================
    static class PathDemo {

        public static void run() {
            System.out.println("===== 演示1：Path 操作 =====");

            Path base = Paths.get(System.getProperty("user.home"), "data");
            System.out.println("  Paths.get(user.home, data) = " + base);
            System.out.println("  toAbsolutePath()           = " + base.toAbsolutePath());
            System.out.println("  getParent()                = " + base.getParent());
            System.out.println("  getFileName()              = " + base.getFileName());
            System.out.println("  subpath(0,1)               = " + base.subpath(0, 1));

            Path messy = Paths.get("a", "b", "..", "c", ".", "d");
            System.out.println("  Paths.get(a,b,..,c,.,d)    = " + messy);
            System.out.println("    normalize()              = " + messy.normalize()); // a/c/d

            System.out.println("  base.resolve(input.txt)    = " + base.resolve("input.txt"));
            System.out.println("  base.relativize(other)     = "
                    + base.relativize(Paths.get(System.getProperty("user.home"), "other")));
        }
    }

    // ============================================================
    // 演示2：Files 工具类（在临时目录操作，结束自动清理）
    // ============================================================
    static class FilesDemo {

        public static void run() throws IOException {
            System.out.println("\n===== 漓示2：Files 工具类 =====");

            Path dir = Files.createTempDirectory("demo5_3_");
            System.out.println("  createTempDirectory        = " + dir);

            // 写入
            Path file = dir.resolve("hello.txt");
            Files.write(file, List.of("第一行", "第二行"), StandardCharsets.UTF_8);
            System.out.println("  Files.write 行写入         = " + file);

            // 读取所有行
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            System.out.println("  Files.readAllLines         = " + lines);

            // 读取所有字节 / 字符串
            String content = Files.readString(file, StandardCharsets.UTF_8);
            System.out.println("  Files.readString           = " + content.replace("\n", "|"));

            // 复制
            Path copy = dir.resolve("hello.bak");
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  Files.copy(REPLACE)        = " + copy);

            // 多级目录
            Path subDir = dir.resolve("sub/deep");
            Files.createDirectories(subDir);
            Files.write(subDir.resolve("nested.txt"),
                    List.of("嵌套文件"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE);
            System.out.println("  createDirectories         = " + subDir);

            // 遍历
            System.out.println("  Files.walk 常规文件:");
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .forEach(p -> System.out.println("    " + dir.relativize(p)));
            }

            // 清理：先删子项再删目录（倒序）
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignore) {} });
            }
            System.out.println("  已清理临时目录");
            System.out.println("  [说明] Files 还支持 lines() 流式读、probeContentType()、getFileStore() 等");
        }
    }

    // ============================================================
    // main 入口
    // ============================================================
    public static void main(String[] args) throws IOException {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo 5.3 — Path 与 Files 实战演示        ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        PathDemo.run();   // 演示1：Path
        FilesDemo.run();  // 演示2：Files

        System.out.println("\n===== 全部演示结束 =====");
    }
}
