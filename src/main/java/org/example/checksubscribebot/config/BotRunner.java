package org.example.checksubscribebot.config;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import lombok.RequiredArgsConstructor;
import org.example.checksubscribebot.service.TelegramBotService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BotRunner {
    private final TelegramBotService botService;

    @EventListener(ApplicationReadyEvent.class)
    public void startBot() {
        TelegramBot bot = botService.getBot();
        bot.setUpdatesListener(updates -> {
            for (com.pengrad.telegrambot.model.Update update : updates) {
                botService.handleUpdate(update);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, e -> {
            e.printStackTrace();
        });

        System.out.println("Bot is running...");
    }
}