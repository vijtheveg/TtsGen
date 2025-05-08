package com.merabills;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TtsService {

    private final TextToSpeechClient ttsClient;

    public TtsService() throws IOException {

        this.ttsClient = TextToSpeechClient.create();
    }

    public void synthesizeToFile(String audioPrefix, String text, Path outputDir, String lanCode) {

        LanguageMapper.VoiceConfig config = LanguageMapper.getVoiceConfig(lanCode);
        try {

            String hashedFileName = audioPrefix + md5Hash(text) + "_" + lanCode + ".mp3";
            Path outputPath = outputDir.resolve(hashedFileName);

            if (Files.exists(outputPath)) {

                System.out.println("File already exists, skipping: " + outputPath);
                return;
            }
            // Build the voice request
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
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

        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashBytes = md.digest(input.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes)
            hexString.append(String.format("%02x", b));
        return hexString.toString();
    }

    public void shutdown() {

        if (ttsClient != null)
            ttsClient.close();
    }

}
