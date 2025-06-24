package com.merabills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Entry point for the TTS audio generation tool.
 * <p>
 * This program reads localized `strings.xml` files from language-specific subdirectories
 * (e.g., `res-hi`, `res-en`), filters the string entries based on a regex pattern,
 * and generates corresponding audio files using Google Cloud Text-to-Speech.
 * <p>
 * Usage:
 * java Main <inputFolder> <regexPattern> <outputFolder> <audioPrefix>
 * <p>
 * Parameters:
 * - inputFolder: Root directory containing subdirectories for each language (e.g., res-hi, res-en).
 * - regexPattern: Regular expression to filter string keys (e.g., ^tip_.*).
 *   You can add multiple regex to this separated by a semicolon(;) eg: (e.g., ^tip_.*;^catalog_.*)
 * - outputFolder: Root folder where generated audio files will be saved (e.g., ./output).
 * - audioPrefix: Prefix to prepend to each audio filename (e.g., "audio_").
 * <p>
 * Output:
 * - For each matching string, an `.mp3` file is created in a language-specific folder
 *   like `output/raw-hi/audio_<hash>_hi.mp3`, ensuring no duplicates via MD5-based hashing.
 * <p>
 * Example:
 * java Main ./input "^tip_.*" ./output audio_
 */


public class Main {

    public static void main(String[] args) {
        String inputFolder;
        String regexPattern;
        String outputFolder;
        String audioPrefix;

        // Validate and extract command-line arguments
        if (args.length == 4) {

            inputFolder = args[0];
            regexPattern = args[1];
            outputFolder = args[2];
            audioPrefix = args[3];
        } else {
            throw new IllegalArgumentException("Expected 4 arguments: <inputFolder> <regexPattern> <outputFolder> <audioPrefix>");
        }
        String baseInputFolderPath = inputFolder;

        // Set up input directory path
        Path baseInputPath = Paths.get(baseInputFolderPath);

        if (!Files.isDirectory(baseInputPath)) {

            System.out.println("The provided path is not a directory. Exiting.");
            return;
        }

        String regex = regexPattern;

        // Compile the regex pattern for filtering strings
        List<Pattern> patterns = new ArrayList<>();
        for (String reg : regex.split(";")) {
            patterns.add(Pattern.compile(reg.trim()));
        }

        String baseOutputFolderPath = outputFolder;

        // Set up output directory path
        Path baseOutputPath = Paths.get(baseOutputFolderPath);

        try {

            Files.createDirectories(baseOutputPath);
        } catch (IOException e) {

            System.err.println("Error creating base output directory: " + e.getMessage());
            return;
        }

        try {

            // Initialize TTS service (Google Cloud Text-to-Speech wrapper)
            TtsService ttsService = new TtsService();

            // Traverse subdirectories (e.g., inputFolder/en-IN, inputFolder/hi-IN)
            try (Stream<Path> subDirs = Files.list(baseInputPath)) {

                subDirs.filter(Files::isDirectory).forEach(subDir -> {

                    String folderName = subDir.getFileName().toString();

                    // Extract language code (e.g., "en" from "res-en")
                    String[] parts = folderName.split("-");
                    String langCode = parts[parts.length - 1];

                    // Prepare corresponding output directory like "raw-en"
                    Path langOutputPath = baseOutputPath.resolve("raw-" + langCode);
                    try {

                        Files.createDirectories(langOutputPath);
                    } catch (IOException e) {

                        System.err.println("Failed to create output directory for " + langCode);
                        return;
                    }

                    // Walk through all XML files inside the language folder
                    try (Stream<Path> files = Files.walk(subDir)) {

                        files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".xml"))
                                .forEach(filePath -> {

                                    // Extract key-value string pairs that match the regex
                                    Map<String, String> results =
                                            XmlStringExtractor.extractMatchingStrings(filePath.toFile(), patterns);

                                    // Synthesize audio for each matching string
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
            // Cleanly shut down TTS service (e.g., release clients, threads)
            ttsService.shutdown();

        } catch (IOException e) {

            System.err.println("TTS Service error: " + e.getMessage());
        }
    }
}