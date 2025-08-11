package com.merabills.text_to_speech;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Entry point for the TTS audio generation tool.
 * <p>
 * This program reads localized `strings.xml` files from language-specific subfolders
 * (e.g., `res-hi`, `res-en`), filters the string entries based on a regex pattern,
 * and generates corresponding audio files using Google Cloud Text-to-Speech.
 * <p>
 * Usage:
 * java Main <inputFolder> <regexPattern> <outputFolder> <audioPrefix>
 * <p>
 * Parameters:
 * - inputFolder: Root folder containing subfolders with language-specific string files (e.g., values-hi, vales-en).
 * - stringNameRegex: Regular expression to filter string keys (e.g., ^tip_.*).
 * You can add multiple regex to this separated by a semicolon(;) eg: (e.g., ^tip_.*;^catalog_.*)
 * - outputFolderFormat: Root folder where generated audio files will be saved (e.g. output/res-%1$s/).
 * <p>
 * Output:
 * - For each matching string, an `.mp3` file is created in the output folder
 * like `output/res-hi/audio_<hash>_hi.mp3`, ensuring no duplicates via MD5-based hashing.
 * <p>
 * Example:
 * java Main ./input "^tip_.*" ./output audio_
 */


public class Main {

    public static void main(String @NotNull [] args) {

        final String inputFolder;
        final String stringNameRegex;
        final String outputFolderFormat;

        // Validate and extract command-line arguments
        if (args.length == 3) {

            inputFolder = args[0];
            stringNameRegex = args[1];
            outputFolderFormat = args[2];
        } else
            throw new IllegalArgumentException("Expected exactly 3 arguments: <inputFolder> <stringNameRegex> <outputFolderFormat>");

        // Validate input folder path
        final Path baseInputPath = Paths.get(inputFolder);
        if (!Files.isDirectory(baseInputPath))
            throw new IllegalStateException("The provided input folder is not valid. Exiting.");

        // Compile the regex pattern for filtering strings
        final List<Pattern> patterns = new ArrayList<>();
        for (String regex : stringNameRegex.split(";"))
            patterns.add(Pattern.compile(regex.trim()));

        try (final TtsService ttsService = new TtsService();
             final Stream<Path> subFolders = Files.find(
                 baseInputPath,
                 1,
                 (path, attributes) -> attributes.isDirectory() && path.getFileName().toString().startsWith(DEFAULT_INPUT_FOLDER_NAME)
             )
        ) {

            final Map<Path, Set<Path>> existingAudioFilesMap = new TreeMap<>();
            subFolders.forEach(stringFileInputFolder -> {

                System.out.printf("Current input folder is '%s'\n", stringFileInputFolder);

                // Extract language code from input folder name (e.g., "en" from "res-en")
                final String languageCode;
                final String folderName = stringFileInputFolder.getFileName().toString();
                if (folderName.equals(DEFAULT_INPUT_FOLDER_NAME))
                    languageCode = "en";
                else if (folderName.startsWith(INPUT_FOLDER_NAME_PREFIX))
                    languageCode = folderName.substring(INPUT_FOLDER_NAME_PREFIX.length());
                else
                    return; // We don't care about this subfolder

                // Prepare corresponding output subfolder by replacing format placeholders with the language code
                // For example, 'output/res-%1$s/' becomes 'output/res-hi/'
                final Path audioFileOutputFolder = Path.of(String.format(outputFolderFormat, languageCode));
                System.out.printf("Current output folder is '%s'\n", audioFileOutputFolder);
                try {
                    Files.createDirectories(audioFileOutputFolder);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to create output folder '" + audioFileOutputFolder + "'", e);
                }

                // List all the existing audio files in the output folder - do this only once per output folder
                Set<Path> existingAudioFiles = existingAudioFilesMap.get(audioFileOutputFolder);
                if (existingAudioFiles == null) {

                    // We have not listed files this particular output folder before
                    existingAudioFiles = new HashSet<>();
                    try (Stream<Path> audioFiles = Files.find(
                        audioFileOutputFolder,
                        Integer.MAX_VALUE,
                        (path, attributes) -> {

                            final String pathString = path.toString();
                            return
                                attributes.isRegularFile() &&
                                    pathString.startsWith(TtsService.OUTPUT_FILE_NAME_PREFIX) &&
                                    pathString.endsWith(TtsService.MP3_EXTENSION);
                        })) {
                        audioFiles.forEach(existingAudioFiles::add);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to list audio files for language '" + languageCode + "'", e);
                    }

                    // Store the audio files that we just listed, to avoid re-listing in case any other
                    // language code also maps to the same output folder
                    existingAudioFilesMap.put(audioFileOutputFolder, existingAudioFiles);
                }
                final Set<Path> existingAudioFilePaths = existingAudioFiles;

                // Walk through all XML files inside the language folder
                try (Stream<Path> files = Files.find(
                    stringFileInputFolder,
                    Integer.MAX_VALUE,
                    (path, attributes) -> attributes.isRegularFile() && path.toString().endsWith(XML_EXTENSION))
                ) {

                    files.forEach(filePath -> {

                        System.out.printf("Parsing input file '%s'\n", filePath);
                        final AndroidStringResourceParser.ParsedResources parsed;
                        try {
                            parsed = AndroidStringResourceParser.parseStringResources(filePath.toFile());
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to parse string resources in file '" + filePath + "'", e);
                        }

                        final Consumer<String> audioSynthesizer = (@NotNull String text) -> {

                            if (text.isEmpty())
                                return;

                            final Path audioPath = ttsService.synthesizeTextToAudioFile(text, languageCode, audioFileOutputFolder);
                            existingAudioFilePaths.remove(audioPath);
                        };

                        // Find matching strings
                        for (final AndroidStringResourceParser.StringResource res : parsed.getStrings())
                            for (final Pattern pattern : patterns) {

                                if (!pattern.matcher(res.getName()).matches())
                                    continue; // We don't care about this string

                                // Matching string found - synthesize audio for it
                                final String text = res.getValue();
                                audioSynthesizer.accept(text);
                                break;
                            }

                        // Find matching string arrays
                        for (AndroidStringResourceParser.StringArrayResource arrayRes : parsed.getStringArrays())
                            for (final Pattern pattern : patterns) {

                                if (!pattern.matcher(arrayRes.getName()).matches())
                                    continue;

                                // Matching string array found - synthesize audio for each array item
                                final List<String> items = arrayRes.getItems();
                                for (final String text : items)
                                    audioSynthesizer.accept(text);
                                break;
                            }
                    });

                    // Delete unused audio files
                    for (final Path unusedPath : existingAudioFilePaths) {

                        try {
                            Files.deleteIfExists(unusedPath);
                        } catch (IOException e) {
                            System.err.println("Failed to delete unused file: " + unusedPath + ". Error: " + e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Error listing subfolders of '" + stringFileInputFolder + "'", e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Error listing subfolders of '" + baseInputPath + "'", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("TTS Service error: ", e);
        }
    }

    public static final @NotNull String XML_EXTENSION = ".xml";
    public static final @NotNull String DEFAULT_INPUT_FOLDER_NAME = "values";
    public static final @NotNull String INPUT_FOLDER_NAME_PREFIX = DEFAULT_INPUT_FOLDER_NAME + "-";
}