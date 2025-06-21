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

public class TtsService {

    private final TextToSpeechClient ttsClient;

    public TtsService() throws IOException {

        this.ttsClient = TextToSpeechClient.create();
    }

    public void synthesizeToFile(String audioPrefix, String text, Path outputDir, String lanCode) {

        LanguageMapper.VoiceConfig config = LanguageMapper.getVoiceConfig(lanCode);
        try {
            String cleanedText = text.replaceAll("\\\\n", "");
            String hashedFileName = audioPrefix + md5Hash(cleanedText).toLowerCase(Locale.ROOT) + "_" + lanCode + ".mp3";
            Path outputPath = outputDir.resolve(hashedFileName);
            System.out.println(outputPath);
            if (Files.exists(outputPath)) {

                System.out.println("File already exists, skipping: " + outputPath);
                return;
            }
            // Build the voice request
            SynthesisInput input = SynthesisInput.newBuilder().setSsml(cleanedText).build();
            System.out.println(input);
            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(config.languageCode())
                    .setName(config.voiceName())
                    .setSsmlGender(SsmlVoiceGender.FEMALE)
                    .build();

            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build();

            SynthesizeSpeechResponse response = ttsClient.synthesizeSpeech(input, voice, audioConfig);
            ByteString audioContents = response.getAudioContent();

            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {

                out.write(audioContents.toByteArray());
                System.out.println("Audio content written to: " + outputPath);
            }

        } catch (Exception e) {
            System.err.println("Failed to synthesize text: " + text);
            e.printStackTrace();
        }
    }

    private String md5Hash(String input) throws NoSuchAlgorithmException {
        System.out.println(input.length());
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hashBytes);
    }

    public static String bytesToHex(@Nullable byte[] bytes) {
        if (bytes != null && bytes.length != 0) {
            char[] hexChars = new char[bytes.length * 2];
            int i = 0;

            for(int j = 0; i < bytes.length; ++i) {
                short byteValue = toUnsignedByte(bytes[i]);
                hexChars[j++] = getHexCharForMsn(byteValue);
                hexChars[j++] = getHexCharForLsn(byteValue);
            }

            return new String(hexChars);
        } else {
            return null;
        }
    }
    public static char getHexCharForLsn(short byteValue) {
        return HEX_DIGITS[byteValue & 15];
    }
    public static short toUnsignedByte(byte value) {
        return (short)(value & 255);
    }

    public static char getHexCharForMsn(short byteValue) {
        return HEX_DIGITS[byteValue >>> 4];
    }

    public static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    public void shutdown() {

        if (ttsClient != null)
            ttsClient.close();
    }

}
