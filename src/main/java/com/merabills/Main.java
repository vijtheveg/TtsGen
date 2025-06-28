package com.merabills;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * You can add multiple regex to this separated by a semicolon(;) eg: (e.g., ^tip_.*;^catalog_.*)
 * - outputFolder: Root folder where generated audio files will be saved (e.g., ./output).
 * - audioPrefix: Prefix to prepend to each audio filename (e.g., "audio_").
 * <p>
 * Output:
 * - For each matching string, an `.mp3` file is created in a language-specific folder
 * like `output/raw-hi/audio_<hash>_hi.mp3`, ensuring no duplicates via MD5-based hashing.
 * <p>
 * Example:
 * java Main ./input "^tip_.*" ./output audio_
 */


public class Main {

    public static void main(String @NotNull [] args) {

        final String inputFolder;
        final String regexPattern;
        final String outputFolder;
        final String audioPrefix;

        // Validate and extract command-line arguments
        if (args.length == 4) {

            inputFolder = args[0];
            regexPattern = args[1];
            outputFolder = args[2];
            audioPrefix = args[3];
        } else
            throw new IllegalArgumentException("Expected 4 arguments: <inputFolder> <regexPattern> <outputFolder> <audioPrefix>");

        // Set up input directory path
        final Path baseInputPath = Paths.get(inputFolder);

        if (!Files.isDirectory(baseInputPath))
            throw new IllegalStateException("The provided path is not a directory. Exiting.");

        // Compile the regex pattern for filtering strings
        final List<Pattern> patterns = new ArrayList<>();
        for (String regex : regexPattern.split(";"))
            patterns.add(Pattern.compile(regex.trim()));

        // Set up output directory path
        final Path baseOutputPath = Paths.get(outputFolder);

        try {
            Files.createDirectories(baseOutputPath);
        } catch (IOException e) {
            throw new IllegalStateException("Error creating base output directory: ", e);
        }

        try (Stream<Path> subDirectories = Files.list(baseInputPath); TtsService ttsService = new TtsService()) {

            subDirectories.filter(Files::isDirectory).forEach(subDirectory -> {

                final String folderName = subDirectory.getFileName().toString();

                // Extract language code (e.g., "en" from "res-en")
                final String[] parts = folderName.split("-");
                final String languageCode = parts[parts.length - 1];

                // Prepare corresponding output directory like "raw-en"
                final Path languageOutputPath = baseOutputPath.resolve("raw-" + languageCode);
                try {
                    Files.createDirectories(languageOutputPath);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to create output directory for '" + languageCode + "'", e);
                }

                final Set<Path> existingAudioFiles = new HashSet<>();

                try (Stream<Path> audioFiles = Files.find(
                        languageOutputPath,
                        Integer.MAX_VALUE,
                        (path, attr) -> attr.isRegularFile() && path.toString().endsWith(".mp3"))
                ) {
                    audioFiles.forEach(existingAudioFiles::add);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to list audio files for language '" + languageCode + "'", e);
                }

                // Walk through all XML files inside the language folder
                try (Stream<Path> files = Files.find(
                        subDirectory,
                        Integer.MAX_VALUE,
                        (path, basicFileAttributes)
                                -> basicFileAttributes.isRegularFile() && path.toString().endsWith(".xml"))
                ) {

                    files.forEach(filePath -> {

                        AndroidStringResourceParser.ParsedResources parsed;

                        try {
                            parsed = AndroidStringResourceParser.parseStringResources(filePath.toFile());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        for (AndroidStringResourceParser.StringResource res : parsed.getStrings()) {
                            for (Pattern pattern : patterns) {
                                if (pattern.matcher(res.getName()).matches()) {

                                    String text = res.getValue().trim();
                                    if (!text.isEmpty()) {

                                        Path audioPath = ttsService.synthesizeTextToAudioFile(text, languageCode, languageOutputPath, audioPrefix);
                                        existingAudioFiles.remove(audioPath);
                                    } else
                                        System.out.println("Skipping empty value for: " + res.getName());
                                    break;
                                }
                            }
                        }

                        for (AndroidStringResourceParser.StringArrayResource arrayRes : parsed.getStringArrays()) {
                            for (final Pattern pattern : patterns) {
                                if (pattern.matcher(arrayRes.getName()).matches()) {

                                    List<String> items = arrayRes.getItems();
                                    for (int i = 0; i < items.size(); i++) {
                                        final String text = items.get(i).trim();
                                        if (!text.isEmpty()) {

                                            Path audioPath = ttsService.synthesizeTextToAudioFile(text, languageCode, languageOutputPath, audioPrefix);
                                            existingAudioFiles.remove(audioPath);
                                        } else
                                            System.out.println("Skipping empty value for: " + arrayRes.getName() + "[" + i + "]");
                                    }
                                    break;
                                }
                            }
                        }
                    });

                    for (Path unusedPath : existingAudioFiles) {
                        try {

                            Files.deleteIfExists(unusedPath);
                            System.out.println("Deleted unused audio file: " + unusedPath);
                        } catch (IOException e) {
                            System.err.println("Failed to delete unused file: " + unusedPath + ". Error: " + e.getMessage());
                        }
                    }

                } catch (IOException e) {
                    throw new IllegalStateException("Error walking subDirectory '" + subDirectory + "'", e);
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("TTS Service error: ", e);
        }
    }
}