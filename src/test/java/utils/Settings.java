package utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public class Settings {
    @JsonProperty("bot_token")
    private String bot_token;

    @JsonProperty("api_url")
    private String api_url;

    @JsonProperty("api_key")
    private String api_key;

    private static Settings instance;

    public static void load(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        instance = mapper.readValue(new File(filePath), Settings.class);
    }

    public static Settings get() {
        return instance;
    }

    public String getBotToken() {
        return bot_token;
    }

    public String getApiUrl() {
        return api_url;
    }

    public String getApiKey() {
        return api_key;
    }
}
