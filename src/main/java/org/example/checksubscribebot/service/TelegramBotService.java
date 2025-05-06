package org.example.checksubscribebot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.CreateChatInviteLink;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.GetChat;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.GetChatResponse;
import lombok.RequiredArgsConstructor;
import org.example.checksubscribebot.config.BotConfig;
import org.example.checksubscribebot.entity.Channel;
import org.example.checksubscribebot.entity.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@EnableAsync
public class TelegramBotService {
    private final BotService botService;
    private final BotConfig botConfig;
    private TelegramBot bot;

    public void initBot() {
        if (botConfig.getBotToken() == null || botConfig.getBotToken().isEmpty()) {
            throw new IllegalStateException("Bot token is not configured in application.properties");
        }
        bot = new TelegramBot(botConfig.getBotToken());
    }

    public TelegramBot getBot() {
        if (bot == null) {
            initBot();
        }
        return bot;
    }

    @Transactional
    public void handleUpdate(Update update) {
        if (update.message() != null && update.message().text() != null) {
            Message message = update.message();
            String text = message.text();
            Long chatId = message.chat().id();
            Long userId = message.from().id();

            if (text.equals("/start")) {
                handleStartCommand(chatId, userId);
            } else if (text.equals("/getid")) {
                handleGetIdCommand(chatId, message);
            } else if (text.equals("/admin") && userId == 1327166079) {
                adminPanel(chatId, userId);
            } else if (text.startsWith("/addchannel ") && userId == 1327166079) {
                handleAddChannel(chatId, text);
            } else if (text.equals("Add Channel") && userId == 1327166079) {
                handleAddChannelRequest(chatId);
            } else if (text.equals("Delete Channel") && userId == 1327166079) {
                showChannelsForDeletion(chatId);
            } else if (text.equals("Show Channels") && userId == 1327166079) {
                handleShowChannels(chatId);
            } else if (text.equals("Mark as Special Channel") && userId == 1327166079) {
                showChannelsForMarkingSpecial(chatId);
            }
        } else if (update.callbackQuery() != null) {
            handleCallbackQuery(update.callbackQuery());
        }
    }

    @Transactional
    protected void handleStartCommand(Long chatId, Long userId) {
        User user = botService.getOrCreateUser(userId);
        if (userId == 1327166079) {
            adminPanel(chatId, userId);
        } else {
            List<Channel> nonSubscribedChannels = getChannels(userId);

            if (nonSubscribedChannels.isEmpty()) {
                Channel specialChannel = botService.getSpecialChannel();
                if (specialChannel != null && (user.getHasReceivedSpecialLink() == null || !user.getHasReceivedSpecialLink())) {
                    int expireDate = (int) (System.currentTimeMillis() / 1000L + 60);
                    CreateChatInviteLink createLink = new CreateChatInviteLink(specialChannel.getId().toString())
                            .memberLimit(1)
                            .expireDate(expireDate);

                    ChatInviteLink inviteLink = bot.execute(createLink).chatInviteLink();

                    if (user.getLastMessageId() != null) {
                        bot.execute(new DeleteMessage(chatId, user.getLastMessageId()));
                        user.setLastMessageId(null);
                    }

                    String text1 = "\uD83C\uDF89 Congratulations! You've joined all channels. Here's your special link:\n" + inviteLink.inviteLink();
                    SendMessage message = new SendMessage(chatId, text1);
                    bot.execute(message);

                    user.setHasReceivedSpecialLink(true);
                    botService.saveUser(user);
                } else {
                    if (user.getLastMessageId() != null) {
                        bot.execute(new DeleteMessage(chatId, user.getLastMessageId()));
                        user.setLastMessageId(null);
                    }

                    String text1 = "You've already received your special link. Enjoy the mock! 🎉";
                    SendMessage message = new SendMessage(chatId, text1);
                    bot.execute(message);
                }
            } else {
                String text = """
                   Join these channels""";
                SendMessage message = new SendMessage(chatId, text);
                message.replyMarkup(createChannelButtons(user));
                Message sentMessage = bot.execute(message).message();

                user.setLastMessageId(sentMessage.messageId());
                botService.saveUser(user);
            }
        }
    }

    private void adminPanel(Long chatId, Long userId) {
        String text = "Admin Panel: Select an action";
        SendMessage message = new SendMessage(chatId, text);
        message.replyMarkup(createAdminPanelKeyboard());
        bot.execute(message);
    }

    private ReplyKeyboardMarkup createAdminPanelKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(
                new String[]{"Add Channel", "Delete Channel"},
                new String[]{"Show Channels", "Mark as Special Channel"}
        );
        keyboard.resizeKeyboard(true);
        keyboard.oneTimeKeyboard(false);
        return keyboard;
    }

    private void handleAddChannelRequest(Long chatId) {
        bot.execute(new SendMessage(chatId, "Please enter the channel details in the format: /addchannel <@username> <name> [special]"));
    }

    private void handleAddChannel(Long chatId, String text) {
        String[] parts = text.split("\\s+", 4);
        if (parts.length >= 3) {
            try {
                String username = parts[1].startsWith("@") ? parts[1].substring(1) : parts[1];
                String name = parts[2];
                boolean isSpecial = parts.length == 4 && parts[3].equalsIgnoreCase("special");

                GetChat getChat = new GetChat("@" + username);
                GetChatResponse chatResponse = bot.execute(getChat);
                if (chatResponse.isOk()) {
                    Long channelId = chatResponse.chat().id();

                    botService.addChannel(channelId, name, username, isSpecial);
                    String response = "Channel added successfully: ID=" + channelId + ", Name=" + name + ", Invite Link=" + "https://t.me/"+username + ", Special=" + isSpecial;
                    bot.execute(new SendMessage(chatId, response));
                } else {
                    bot.execute(new SendMessage(chatId, "Failed to get channel info: " + chatResponse.description()));
                }
            } catch (NumberFormatException e) {
                bot.execute(new SendMessage(chatId, "Invalid input. Please use a valid channel username."));
            } catch (Exception e) {
                bot.execute(new SendMessage(chatId, "Failed to add channel: " + e.getMessage()));
            }
        } else {
            bot.execute(new SendMessage(chatId, "Usage: /addchannel <@username> <name> [special]"));
        }
    }

    @Transactional
    protected void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.data();
        Long chatId = callbackQuery.message().chat().id();
        Long userId = callbackQuery.from().id();

        bot.execute(new AnswerCallbackQuery(callbackQuery.id()).text("Processing...").showAlert(false));

        if (callbackData.equals("confirm")) {
            List<Channel> nonSubscribedChannels = getChannels(userId);

            User user = botService.getOrCreateUser(userId);
            if (user.getLastMessageId() != null) {
                bot.execute(new DeleteMessage(chatId, user.getLastMessageId()));
                user.setLastMessageId(null);
            }

            if (nonSubscribedChannels.isEmpty()) {
                Channel specialChannel = botService.getSpecialChannel();
                if (specialChannel != null) {
                    int expireDate = (int) (System.currentTimeMillis() / 1000L + 60);
                    CreateChatInviteLink createLink = new CreateChatInviteLink(specialChannel.getId().toString())
                            .memberLimit(1)
                            .expireDate(expireDate);

                    ChatInviteLink inviteLink = bot.execute(createLink).chatInviteLink();

                    String text1 = "\uD83C\uDF89 Congratulations! You've joined all channels. Here's your special link:\n" + inviteLink.inviteLink();
                    SendMessage message = new SendMessage(chatId, text1);
                    bot.execute(message);

                    user.setHasReceivedSpecialLink(true);
                    botService.saveUser(user);
                }
            } else {
                String text = "You are still not subscribed to the following channels:";
                SendMessage message = new SendMessage(chatId, text);
                message.replyMarkup(createChannelButtons(user));
                Message sentMessage = bot.execute(message).message();

                user.setLastMessageId(sentMessage.messageId());
                botService.saveUser(user);
            }
        } else if (userId == 1327166079 && callbackData.startsWith("delete_channel_")) {
            Long channelId = Long.parseLong(callbackData.split("_")[2]);
            handleDeleteChannel(chatId, channelId);
        } else if (userId == 1327166079 && callbackData.startsWith("mark_special_")) {
            Long channelId = Long.parseLong(callbackData.split("_")[2]);
            handleMarkSpecialChannel(chatId, channelId);
        }
    }

    private void handleDeleteChannel(Long chatId, Long channelId) {
        try {
            botService.deleteChannel(channelId);
            bot.execute(new SendMessage(chatId, "Channel deleted successfully: ID=" + channelId));
        } catch (Exception e) {
            bot.execute(new SendMessage(chatId, "Failed to delete channel: " + e.getMessage()));
        }
    }

    private void handleMarkSpecialChannel(Long chatId, Long channelId) {
        try {
            botService.markChannelAsSpecial(channelId);
            bot.execute(new SendMessage(chatId, "Channel marked as special: ID=" + channelId));
        } catch (Exception e) {
            bot.execute(new SendMessage(chatId, "Failed to mark channel as special: " + e.getMessage()));
        }
    }

    private void showChannelsForDeletion(Long chatId) {
        List<Channel> channels = botService.getAllChannels();
        if (channels.isEmpty()) {
            bot.execute(new SendMessage(chatId, "No channels found."));
        } else {
            String text = "Select a channel to delete:";
            SendMessage message = new SendMessage(chatId, text);
            message.replyMarkup(createChannelButtonsForDeletion(channels));
            bot.execute(message);
        }
    }

    private void showChannelsForMarkingSpecial(Long chatId) {
        List<Channel> channels = botService.getAllChannels();
        if (channels.isEmpty()) {
            bot.execute(new SendMessage(chatId, "No channels found."));
        } else {
            String text = "Select a channel to mark as special:";
            SendMessage message = new SendMessage(chatId, text);
            message.replyMarkup(createChannelButtonsForMarkingSpecial(channels));
            bot.execute(message);
        }
    }

    private InlineKeyboardMarkup createChannelButtonsForDeletion(List<Channel> channels) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Channel channel : channels) {
            InlineKeyboardButton button = new InlineKeyboardButton(channel.getName() + " (ID: " + channel.getId() + ")")
                    .callbackData("delete_channel_" + channel.getId());
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }
        InlineKeyboardButton[][] buttonArray = new InlineKeyboardButton[keyboard.size()][];
        for (int i = 0; i < keyboard.size(); i++) {
            List<InlineKeyboardButton> row = keyboard.get(i);
            buttonArray[i] = row.toArray(new InlineKeyboardButton[0]);
        }
        return new InlineKeyboardMarkup(buttonArray);
    }

    private InlineKeyboardMarkup createChannelButtonsForMarkingSpecial(List<Channel> channels) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Channel channel : channels) {
            InlineKeyboardButton button = new InlineKeyboardButton(channel.getName() + " (ID: " + channel.getId() + ")" + (channel.isSpecial() ? " [Special]" : ""))
                    .callbackData("mark_special_" + channel.getId());
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }
        InlineKeyboardButton[][] buttonArray = new InlineKeyboardButton[keyboard.size()][];
        for (int i = 0; i < keyboard.size(); i++) {
            List<InlineKeyboardButton> row = keyboard.get(i);
            buttonArray[i] = row.toArray(new InlineKeyboardButton[0]);
        }
        return new InlineKeyboardMarkup(buttonArray);
    }

    private void handleShowChannels(Long chatId) {
        List<Channel> channels = botService.getAllChannels();
        if (channels.isEmpty()) {
            bot.execute(new SendMessage(chatId, "No channels found."));
        } else {
            StringBuilder text = new StringBuilder("All Channels:\n");
            for (Channel channel : channels) {
                text.append("ID: ").append(channel.getId())
                        .append(", Name: ").append(channel.getName())
                        .append(", Invite Link: ").append(channel.getInviteLink())
                        .append(", Special: ").append(channel.isSpecial())
                        .append("\n");
            }
            bot.execute(new SendMessage(chatId, text.toString()));
        }
    }

    @NotNull
    private List<Channel> getChannels(Long userId) {
        List<Channel> allChannels = botService.getNonJoinedChannels(userId);
        List<Channel> nonSubscribedChannels = new ArrayList<>();

        for (Channel channel : allChannels) {
            if (!botService.isUserMember(bot, userId, channel.getId()) && !channel.isSpecial()) {
                nonSubscribedChannels.add(channel);
            }
        }
        return nonSubscribedChannels;
    }

    @Transactional
    protected void handleGetIdCommand(Long chatId, Message message) {
        String text;
        if (message.replyToMessage() != null && message.replyToMessage().forwardFromChat() != null) {
            Long forwardedChatId = message.replyToMessage().forwardFromChat().id();
            String forwardedChatTitle = message.replyToMessage().forwardFromChat().title();
            text = "Channel ID: " + forwardedChatId + "\nChannel Title: " + (forwardedChatTitle != null ? forwardedChatTitle : "Unknown");
        } else {
            text = "Please reply to a forwarded message from the channel to get its ID.\nCurrent chat ID: " + message.chat().id();
        }
        SendMessage response = new SendMessage(chatId, text);
        bot.execute(response);
    }

    protected InlineKeyboardMarkup createChannelButtons(User user) {
        List<Channel> nonSubscribedChannels = getChannels(user.getTelegramId());
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (Channel channel : nonSubscribedChannels) {
            InlineKeyboardButton button = new InlineKeyboardButton(channel.getName())
                    .url(channel.getInviteLink());
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }

        if (!nonSubscribedChannels.isEmpty()) {
            InlineKeyboardButton instagramButton = new InlineKeyboardButton("Instagram channel")
                    .url("https://www.instagram.com/muhammadaminsx");
            List<InlineKeyboardButton> instagramRow = new ArrayList<>();
            instagramRow.add(instagramButton);
            keyboard.add(instagramRow);

            InlineKeyboardButton confirmButton = new InlineKeyboardButton("Confirm")
                    .callbackData("confirm");
            List<InlineKeyboardButton> confirmRow = new ArrayList<>();
            confirmRow.add(confirmButton);
            keyboard.add(confirmRow);
        }

        InlineKeyboardButton[][] buttonArray = new InlineKeyboardButton[keyboard.size()][];
        for (int i = 0; i < keyboard.size(); i++) {
            List<InlineKeyboardButton> row = keyboard.get(i);
            buttonArray[i] = row.toArray(new InlineKeyboardButton[0]);
        }

        return new InlineKeyboardMarkup(buttonArray);
    }
}