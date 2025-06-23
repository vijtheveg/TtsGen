package com.merabills;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Service class responsible for converting text into audio files using
 * Google Cloud Text-to-Speech API.
 */
public class TtsService {

    // Google Cloud TTS client instance
    private final TextToSpeechClient ttsClient;

    /**
     * Constructor initializes the TTS client.
     * May throw IOException if credentials are missing or malformed.
     */
    public TtsService() throws IOException {

        this.ttsClient = TextToSpeechClient.create();
    }

    /**
     * Synthesizes the given text into an MP3 audio file and stores it in the given directory.
     *
     * @param audioPrefix Prefix to add to audio filenames (e.g., "audio_")
     * @param text        The input text to synthesize
     * @param outputDir   The folder where the audio file will be saved
     * @param lanCode     Language code (e.g., "hi", "en") used for voice selection
     */
    public void synthesizeToFile(String audioPrefix, String text, Path outputDir, String lanCode) {

        // Get appropriate voice config for the language
        LanguageMapper.VoiceConfig config = LanguageMapper.getVoiceConfig(lanCode);
        try {

            // Preprocess text: remove `\n`, <sub> tags, and unescape quotes
            String cleanedText = text.replaceAll("\\\\n", "").replaceAll("<sub[^>]*>", "").replaceAll("</sub>", "").replace("\\'", "'");

            // Generate a unique filename based on hash of the cleaned text
            String hashedFileName = audioPrefix + md5Hash(cleanedText).toLowerCase(Locale.ROOT) + "_" + lanCode + ".mp3";
            Path outputPath = outputDir.resolve(hashedFileName);

            // Skip if audio already exists
            if (Files.exists(outputPath)) {

                System.out.println("File already exists, skipping: " + outputPath);
                return;
            }

            // Prepare the TTS request
            SynthesisInput input = SynthesisInput.newBuilder().setSsml(cleanedText).build(); // SSML allows advanced formatting

            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(config.languageCode())
                    .setName(config.voiceName())
                    .setSsmlGender(SsmlVoiceGender.FEMALE)
                    .build();

            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build();

            // Make the TTS API call
            SynthesizeSpeechResponse response = ttsClient.synthesizeSpeech(input, voice, audioConfig);
            ByteString audioContents = response.getAudioContent();

            // Write the result to a .mp3 file
            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {

                out.write(audioContents.toByteArray());
                System.out.println("Audio content written to: " + outputPath);
            }

        } catch (Exception e) {
            System.err.println("Failed to synthesize text: " + text);
            e.printStackTrace();
        }
    }

    /**
     * Generates an MD5 hash of the input string.
     * Used to create unique, consistent filenames for TTS outputs.
     */
    private String md5Hash(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hashBytes);
    }

    /**
     * Converts a byte array to a hexadecimal string.
     */
    public static String bytesToHex(@Nullable byte[] bytes) {
        if (bytes != null && bytes.length != 0) {
            char[] hexChars = new char[bytes.length * 2];
            int i = 0;

            for (int j = 0; i < bytes.length; ++i) {
                short byteValue = toUnsignedByte(bytes[i]);
                hexChars[j++] = getHexCharForMsn(byteValue);
                hexChars[j++] = getHexCharForLsn(byteValue);
            }

            return new String(hexChars);
        } else {
            return null;
        }
    }

    // Converts a byte to an unsigned short (0–255)
    public static char getHexCharForLsn(short byteValue) {
        return HEX_DIGITS[byteValue & 15];
    }

    // Gets the hex character for the most significant nibble
    public static short toUnsignedByte(byte value) {
        return (short) (value & 255);
    }

    // Gets the hex character for the least significant nibble
    public static char getHexCharForMsn(short byteValue) {
        return HEX_DIGITS[byteValue >>> 4];
    }

    // Hex character lookup table
    public static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /**
     * Gracefully shuts down the TTS client.
     */
    public void shutdown() {

        if (ttsClient != null)
            ttsClient.close();
    }

}
