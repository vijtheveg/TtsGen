package com.merabills;

import java.util.HashMap;
import java.util.Map;

public class LanguageMapper {

    public record VoiceConfig(String languageCode, String voiceName) {
    }

    private static final Map<String, VoiceConfig> languageMap = new HashMap<>();

    static {

        languageMap.put("as", new VoiceConfig("as-IN", "as-IN-Wavenet-A"));
        languageMap.put("bn", new VoiceConfig("bn-IN", "bn-IN-Wavenet-A"));
        languageMap.put("gu", new VoiceConfig("gu-IN", "gu-IN-Wavenet-A"));
        languageMap.put("hi", new VoiceConfig("hi-IN", "hi-IN-Wavenet-A"));
        languageMap.put("kn", new VoiceConfig("kn-IN", "kn-IN-Wavenet-A"));
        languageMap.put("ml", new VoiceConfig("ml-IN", "ml-IN-Wavenet-A"));
        languageMap.put("mr", new VoiceConfig("mr-IN", "mr-IN-Wavenet-A"));
        languageMap.put("pa", new VoiceConfig("pa-IN", "pa-IN-Wavenet-A"));
        languageMap.put("ta", new VoiceConfig("ta-IN", "ta-IN-Wavenet-A"));
        languageMap.put("te", new VoiceConfig("te-IN", "te-IN-Wavenet-A"));
        languageMap.put("en", new VoiceConfig("en-IN", "en-IN-Wavenet-A"));
    }

    public static VoiceConfig getVoiceConfig(String lanCode) {

        return languageMap.getOrDefault(
                lanCode,
                new VoiceConfig("en-IN", "en-IN-Wavenet-A")
        );
    }
}
