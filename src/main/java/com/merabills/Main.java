package com.merabills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        String inputFolder;
        String regexPattern;
        String outputFolder;
        String audioPrefix;

        if (args.length == 4) {

            inputFolder = args[0];
            regexPattern = args[1];
            outputFolder = args[2];
            audioPrefix = args[3];
        } else {
//            throw new IllegalArgumentException("Expected 4 arguments: <inputFolder> <regexPattern> <outputFolder> <audioPrefix>");
            inputFolder = "/Users/rajatdhamija/Texts";
            regexPattern = "quick_tips_.*";
            outputFolder = "/Users/rajatdhamija/Output_Texts";
            audioPrefix = "merabills";
        }
        String baseInputFolderPath = inputFolder;
        Path baseInputPath = Paths.get(baseInputFolderPath);

        if (!Files.isDirectory(baseInputPath)) {

            System.out.println("The provided path is not a directory. Exiting.");
            return;
        }

        String regex = regexPattern;
        Pattern pattern = Pattern.compile(regex);

        String baseOutputFolderPath = outputFolder;
        Path baseOutputPath = Paths.get(baseOutputFolderPath);

        try {

            Files.createDirectories(baseOutputPath);
        } catch (IOException e) {

            System.err.println("Error creating base output directory: " + e.getMessage());
            return;
        }

        try {

            TtsService ttsService = new TtsService();
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

                                        if (!value.trim().isEmpty())
                                            ttsService.synthesizeToFile(audioPrefix, value, langOutputPath, langCode);
                                        else
                                            System.out.println("Skipping empty value for: " + key);
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