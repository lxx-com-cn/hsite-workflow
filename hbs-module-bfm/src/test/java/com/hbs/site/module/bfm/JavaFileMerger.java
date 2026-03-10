package com.hbs.site.module.bfm;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Collectors;

public class JavaFileMerger {

    // 硬编码的源目录路径和目标文件路径
    private static final String P_PATH ="D:\\dev\\idea2019repo\\hbs\\hbs-site\\hbs-module-bfm\\src";
    private static final String SOURCE_DIRECTORY_PATH = P_PATH + "\\main\\java\\com\\hbs\\site\\module\\bfm";
    private static final String OUTPUT_FILE_PATH = P_PATH + "\\test\\java\\com\\hbs\\site\\module\\bfm\\javaFile.txt";

    public static void main(String[] args) {
        try {
            new JavaFileMerger().run();
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void run() throws Exception {
        // 检查源目录是否存在
        Path sourceDir = Paths.get(SOURCE_DIRECTORY_PATH);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.out.println("Source directory does not exist or is not a directory.");
            return;
        }

        // 获取所有.java文件并写入目标文件
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(OUTPUT_FILE_PATH), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            Files.walk(sourceDir)
                    .filter(path -> path.toString().endsWith(".java")) // 过滤出.java文件
                    .forEach(path -> {
                        try {
                            // 读取文件内容
                            String content = Files.lines(path).collect(Collectors.joining(System.lineSeparator()));
                            // 写入文件内容
                            writer.write("\n");
                            writer.write(content);
                            writer.write("\n------\n");
                        } catch (IOException e) {
                            System.err.println("Error processing file: " + path);
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error writing to output file.");
            e.printStackTrace();
        }

        System.out.println("All Java files have been merged into: " + OUTPUT_FILE_PATH);
    }
}