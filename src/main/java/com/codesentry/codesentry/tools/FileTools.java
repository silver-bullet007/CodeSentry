package com.codesentry.codesentry.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class FileTools {

    private static final Path BASE_DIR = Paths.get("src/main/java/com/codesentry").toAbsolutePath().normalize();

    @Tool(description = "List all Java source files under the project's src directory")
    public String listFiles() {
        try (Stream<Path> paths = Files.walk(BASE_DIR)) {
            return paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(BASE_DIR::relativize)
                    .map(Path::toString)
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error listing files: " + e.getMessage();
        }
    }

    @Tool(description = "Read the full contents of a Java source file, given its relative path from listFiles")
    public String readFile(String relativePath) {
        Path resolved = BASE_DIR.resolve(relativePath).normalize();

        if (!resolved.startsWith(BASE_DIR)) {
            return "Error: access outside the project directory is not allowed";
        }

        try {
            return Files.readString(resolved);
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}