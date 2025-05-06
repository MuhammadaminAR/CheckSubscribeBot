package org.example.checksubscribebot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.ChatMember;
import com.pengrad.telegrambot.request.GetChatMember;
import com.pengrad.telegrambot.response.GetChatMemberResponse;
import org.example.checksubscribebot.config.BotConfig;
import org.example.checksubscribebot.entity.Channel;
import org.example.checksubscribebot.entity.User;
import org.example.checksubscribebot.repos.ChannelRepository;
import org.example.checksubscribebot.repos.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BotService {
    private final BotConfig botConfig;
    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;

    @Transactional
    public User getOrCreateUser(Long telegramId) {
        try {
            return userRepository.findById(telegramId).orElseGet(() -> {
                User user = new User();
                user.setTelegramId(telegramId);
                return userRepository.save(user);
            });
        } catch (Exception e) {
            System.err.println("Failed to get or create user: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public User saveUser(User user) {
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            System.err.println("Failed to save user: " + e.getMessage());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<Channel> getNonJoinedChannels(Long userId) {
        try {
            User user = userRepository.findById(userId).orElseThrow(() ->
                    new IllegalStateException("User not found: " + userId));
            List<Channel> allChannels = channelRepository.findAll();
            if (allChannels.isEmpty()) {
                System.err.println("No channels found in the database for user " + userId);
                return Collections.emptyList();
            }
            return allChannels.stream()
                    .filter(channel -> !isUserMember(null, userId, channel.getId()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Failed to get non-joined channels: " + e.getMessage());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public boolean isUserMember(TelegramBot bot, Long userId, Long channelId) {
        try {
            GetChatMember request = new GetChatMember(channelId, userId);
            GetChatMemberResponse response = (bot != null ? bot : new TelegramBot(botConfig.getBotToken())).execute(request);
            if (response.isOk()) {
                ChatMember chatMember = response.chatMember();
                if (chatMember == null) {
                    System.err.println("Chat member is null for user " + userId + " in channel " + channelId);
                    return false;
                }
                String status = chatMember.status().name();
                System.out.println("Membership status for user " + userId + " in channel " + channelId + ": " + status);
                return status.equals("member") || status.equals("administrator") || status.equals("creator");
            } else {
                System.err.println("Failed to check membership for user " + userId + " in channel " + channelId + ": " +
                        "Error code: " + response.errorCode() + ", Description: " + response.description());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error checking membership: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void addChannel(Long channelId, String name, String username, boolean isSpecial) {
        try {
            if (channelRepository.existsById(channelId)) {
                throw new IllegalStateException("Channel with ID " + channelId + " already exists.");
            }
            String inviteLink="https://t.me/"+username;
            Channel channel = new Channel();
            channel.setId(channelId);
            channel.setName(name);
            channel.setInviteLink(inviteLink);
            channel.setSpecial(isSpecial);
            Channel savedChannel = channelRepository.save(channel);
            System.out.println("Channel added successfully: ID=" + savedChannel.getId() + ", Name=" + savedChannel.getName() + ", Special=" + savedChannel.isSpecial());
        } catch (Exception e) {
            System.err.println("Failed to add channel: " + e.getMessage());
            throw new RuntimeException("Failed to add channel: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteChannel(Long channelId) {
        try {
            if (!channelRepository.existsById(channelId)) {
                throw new IllegalStateException("Channel with ID " + channelId + " does not exist.");
            }
            channelRepository.deleteById(channelId);
            System.out.println("Channel deleted: ID=" + channelId);
        } catch (Exception e) {
            System.err.println("Failed to delete channel: " + e.getMessage());
            throw new RuntimeException("Failed to delete channel: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<Channel> getAllChannels() {
        try {
            List<Channel> channels = channelRepository.findAll();
            System.out.println("Fetched channels: " + channels.size());
            return channels;
        } catch (Exception e) {
            System.err.println("Failed to get all channels: " + e.getMessage());
            throw new RuntimeException("Failed to get all channels: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Channel getSpecialChannel() {
        try {
            Channel specialChannel = channelRepository.findAll().stream()
                    .filter(Channel::isSpecial)
                    .findFirst()
                    .orElse(null);
            System.out.println("Special channel: " + (specialChannel != null ? specialChannel.getId() : "none"));
            return specialChannel;
        } catch (Exception e) {
            System.err.println("Failed to get special channel: " + e.getMessage());
            throw new RuntimeException("Failed to get special channel: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void markChannelAsSpecial(Long channelId) {
        try {
            // Avval barcha kanallarni special=false qilamiz
            List<Channel> allChannels = channelRepository.findAll();
            for (Channel channel : allChannels) {
                if (channel.isSpecial()) {
                    channel.setSpecial(false);
                    channelRepository.save(channel);
                }
            }
            // Keyin tanlangan kanalni special qilamiz
            Channel channel = channelRepository.findById(channelId)
                    .orElseThrow(() -> new IllegalStateException("Channel with ID " + channelId + " not found."));
            channel.setSpecial(true);
            channelRepository.save(channel);
            System.out.println("Channel marked as special: ID=" + channelId);
        } catch (Exception e) {
            System.err.println("Failed to mark channel as special: " + e.getMessage());
            throw new RuntimeException("Failed to mark channel as special: " + e.getMessage(), e);
        }
    }
}