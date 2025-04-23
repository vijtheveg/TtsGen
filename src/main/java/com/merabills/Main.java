package com.merabills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {

//        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter the path to the base input folder: ");
        String baseInputFolderPath = args[0];
        Path baseInputPath = Paths.get(baseInputFolderPath);

        if (!Files.isDirectory(baseInputPath)) {
            System.out.println("The provided path is not a directory. Exiting.");
            return;
        }

        System.out.print("Enter regex to match string names (e.g., ^label_.*): ");
        String regex = args[1];
        Pattern pattern = Pattern.compile(regex);

        System.out.print("Enter path to output folder for audio files: ");
        String baseOutputFolderPath =args[2];
        Path baseOutputPath = Paths.get(baseOutputFolderPath);

        try {
            Files.createDirectories(baseOutputPath);
        } catch (IOException e) {
            System.err.println("Error creating base output directory: " + e.getMessage());
            return;
        }

        try {
            TtsService ttsService = new TtsService();

            // Traverse subdirectories like values-en, values-bn, etc.
            try (Stream<Path> subDirs = Files.list(baseInputPath)) {
                subDirs.filter(Files::isDirectory).forEach(subDir -> {
                    String folderName = subDir.getFileName().toString();
                    String[] parts = folderName.split("-");
                    String langCode = parts[parts.length - 1];

                    Path langOutputPath = baseOutputPath.resolve("raw-" + langCode);
                    try {
                        Files.createDirectories(langOutputPath);
                    } catch (IOException e) {
                        System.err.println("Failed to create output directory for " + langCode);
                        return;
                    }

                    try (Stream<Path> files = Files.walk(subDir)) {
                        files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".xml"))
                                .forEach(filePath -> {
                                    Map<String, String> results =
                                            XmlStringExtractor.extractMatchingStrings(filePath.toFile(), pattern);
                                    results.forEach((key, value) -> {
                                        if (!value.trim().isEmpty()) {
                                            ttsService.synthesizeToFile(value, langOutputPath, langCode);
                                        } else {
                                            System.out.println("Skipping empty value for: " + key);
                                        }
                                    });
                                });
                    } catch (IOException e) {
                        System.err.println("Error walking subDir " + subDir + ": " + e.getMessage());
                    }
                });
            }

            ttsService.shutdown();

        } catch (IOException e) {
            System.err.println("TTS Service error: " + e.getMessage());
        }
    }
}