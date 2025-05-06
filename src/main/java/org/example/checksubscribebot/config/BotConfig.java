package org.example.checksubscribebot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bot")
@Data
public class BotConfig {
    private String botToken = "8001896833:AAGJExkxlWWWDTMi0Ek0I9ItKu0rnlRZY2E";
    private String botUsername = "@sampleisworkingbot";
    private String inviteLink;
}