package com.merabills;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.NotNull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.merabills.Main.mp3Extension;

/**
 * Service class responsible for converting text into audio files using
 * Google Cloud Text-to-Speech API.
 */
public class TtsService implements AutoCloseable {
    public TtsService() throws IOException, NoSuchAlgorithmException {

        ttsClient = TextToSpeechClient.create();

        md5 = MessageDigest.getInstance("MD5");

        audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3)
                .build();

        mVoiceParamsByLanguage = new TreeMap<>();
    }

    /**
     * Synthesizes the given text into an MP3 audio file and stores it in the given directory.
     *
     * @param text            The input text to synthesize
     * @param outputDirectory The folder where the audio file will be saved
     * @param languageCode    Language code (e.g., "hi", "en") used for voice selection
     * @param audioFilePrefix Prefix to add to audio filenames (e.g., "audio_")
     */
    public @NotNull Path synthesizeTextToAudioFile(
            final @NotNull String text,
            final @NotNull String languageCode,
            final @NotNull Path outputDirectory,
            final @NotNull String audioFilePrefix) {

        try {

            /*
             *  Converting the text to Lower case post MD5 Hash is important because
             *  most of the applications expect files names to be in lower case and
             *  also a prefix is needed as name cannot start with a number as well
             */
            final String hashedFileName = audioFilePrefix
                    + md5Hash(text).toLowerCase(Locale.ROOT)
                    + "_" + languageCode + mp3Extension;
            final Path outputPath = outputDirectory.resolve(hashedFileName);
            if (Files.exists(outputPath)) {

                System.err.println("File already exists, skipping: " + outputPath);
                return outputPath;
            }

            // Get voice parameters for the specified language from our cache, if possible
            VoiceSelectionParams voiceParameters;
            if ((voiceParameters = mVoiceParamsByLanguage.get(languageCode)) == null) {

                // We don't have parameters for this language already cached.
                // Create one and add it to the cache
                LanguageMapper.VoiceConfig config = LanguageMapper.getVoiceConfig(languageCode);
                voiceParameters = VoiceSelectionParams.newBuilder()
                        .setLanguageCode(config.languageCode())
                        .setName(config.voiceName())
                        .setSsmlGender(SsmlVoiceGender.FEMALE)
                        .build();
                mVoiceParamsByLanguage.put(languageCode, voiceParameters);
            }

            // Build the voice request
            final SynthesisInput input = SynthesisInput.newBuilder().setSsml(text).build();

            // Generate speech
            final SynthesizeSpeechResponse response = ttsClient.synthesizeSpeech(input, voiceParameters, audioConfig);
            final ByteString audioContents = response.getAudioContent();

            // Write the result to a .mp3 file
            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {

                out.write(audioContents.toByteArray());
                System.out.println("Audio content written to: " + outputPath);
            }

            return outputPath;
        } catch (Exception e) {

            throw new IllegalStateException("Failed to synthesize text: '" + text + "'", e);
        }
    }

    @Override
    public void close() {
        ttsClient.close();
    }

    private @NotNull String md5Hash(@NotNull String input) {

        final byte[] hashBytes = md5.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hashBytes);
    }

    private static @NotNull String bytesToHex(byte @NotNull [] bytes) {

        final char[] hexChars = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; ++i) {

            final short byteValue = toUnsignedByte(bytes[i]);
            hexChars[j++] = getHexCharForMsn(byteValue);
            hexChars[j++] = getHexCharForLsn(byteValue);
        }

        return new String(hexChars);
    }

    private static char getHexCharForLsn(short byteValue) {
        return HEX_DIGITS[byteValue & 15];
    }

    private static short toUnsignedByte(byte value) {
        return (short) (value & 255);
    }

    private static char getHexCharForMsn(short byteValue) {
        return HEX_DIGITS[byteValue >>> 4];
    }

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private final @NotNull TextToSpeechClient ttsClient;
    private final @NotNull MessageDigest md5;
    private final @NotNull AudioConfig audioConfig;
    private final @NotNull Map<String, VoiceSelectionParams> mVoiceParamsByLanguage;
}
