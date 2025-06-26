package com.merabills;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to map short language codes (e.g., "hi", "te") to their
 * corresponding Google Cloud Text-to-Speech language and voice configurations.
 */
public class LanguageMapper {

    /**
     * A record that holds voice configuration details:
     * - languageCode: e.g., "hi-IN"
     * - voiceName: e.g., "hi-IN-Wavenet-A"
     */
    public record VoiceConfig(String languageCode, String voiceName) {
    }

    // A mapping from short language codes to their corresponding VoiceConfig
    private static final Map<String, VoiceConfig> languageMap = new HashMap<>();

    // Static block to initialize the voice configuration map
    static {

        // Assamese maps to Hindi Wavenet voice due to lack of native Assamese support
        languageMap.put("as", new VoiceConfig("as-IN", "hi-IN-Wavenet-A"));
        languageMap.put("bn", new VoiceConfig("bn-IN", "bn-IN-Wavenet-A"));
        languageMap.put("gu", new VoiceConfig("gu-IN", "gu-IN-Wavenet-A"));
        languageMap.put("hi", new VoiceConfig("hi-IN", "hi-IN-Wavenet-A"));
        languageMap.put("kn", new VoiceConfig("kn-IN", "kn-IN-Wavenet-A"));
        languageMap.put("ml", new VoiceConfig("ml-IN", "ml-IN-Wavenet-A"));
        languageMap.put("mr", new VoiceConfig("mr-IN", "mr-IN-Wavenet-A"));
        languageMap.put("pa", new VoiceConfig("pa-IN", "pa-IN-Wavenet-A"));
        languageMap.put("ta", new VoiceConfig("ta-IN", "ta-IN-Wavenet-A"));
        languageMap.put("te", new VoiceConfig("te-IN", "te-IN-Standard-A"));
        languageMap.put("en", new VoiceConfig("en-IN", "en-US-Wavenet-A"));
    }

    /**
     * Retrieves the VoiceConfig for the given language code.
     * If the code is not found, it returns a default English config.
     *
     * @param lanCode The short language code (e.g., "hi", "te")
     * @return VoiceConfig containing the language and voice to use
     */
    public static VoiceConfig getVoiceConfig(String lanCode) {

        return languageMap.getOrDefault(
                lanCode,
                new VoiceConfig("en-IN", "en-IN-Wavenet-A")
        );
    }
}
