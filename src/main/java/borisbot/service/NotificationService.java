package borisbot.service;

import borisbot.properties.BotProperties;
import borisbot.properties.UserSession;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.forum.CreateForumTopic;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.forum.ForumTopic;

@Service
public class NotificationService {

    private final BotProperties props;
    private TelegramLongPollingBot bot;

    public NotificationService(BotProperties props) {
        this.props = props;
    }

    public void setBot(TelegramLongPollingBot bot) {
        this.bot = bot;
    }

    public int createLeadTopic(UserSession session) {
        String topicName = session.fullName + " · " + session.category;
        int topicId = createTopic(topicName);

        String card = String.format(
                "🆕 *Новый заказ*\n\n" +
                        "👤 Имя: %s\n" +
                        "📱 Username: %s\n" +
                        "🆔 Chat ID: `%d`\n\n" +
                        "📦 Категория: %s\n" +
                        "🔢 Тираж: %s\n" +
                        "🎨 Макеты: %s\n\n" +
                        "💡 _Отвечайте прямо в этот топик — клиент получит ваше сообщение._",
                session.fullName, session.username, session.clientChatId,
                session.category, session.volume, session.filesDescription
        );
        sendToTopicMarkdown(topicId, card);

        // Уведомление владельцу
        sendMarkdown(props.telegram().ownerChatId(),
                "🔔 *Новый лид — Trafaret!*\n" +
                        "👤 " + session.fullName + " (" + session.username + ")\n" +
                        "📦 " + session.category + " · " + session.volume
        );

        return topicId;
    }

    public void sendToClient(long chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(text)
                    .build());
        } catch (Exception e) {
            System.err.println("sendToClient error: " + e.getMessage());
        }
    }

    public void sendToClientMarkdown(long chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(text)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            System.err.println("sendToClientMarkdown error: " + e.getMessage());
        }
    }

    public void sendToTopic(int topicId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(props.telegram().groupChatId())
                    .messageThreadId(topicId)
                    .text(text)
                    .build());
        } catch (Exception e) {
            System.err.println("sendToTopic error: " + e.getMessage());
        }
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private int createTopic(String name) {
        try {
            CreateForumTopic create = CreateForumTopic.builder()
                    .chatId(props.telegram().groupChatId())
                    .name(name.length() > 128 ? name.substring(0, 128) : name)
                    .build();
            ForumTopic topic = bot.execute(create);
            return topic.getMessageThreadId();
        } catch (Exception e) {
            System.err.println("createTopic error: " + e.getMessage());
            return -1;
        }
    }

    private void sendMarkdown(String chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            System.err.println("sendMarkdown error: " + e.getMessage());
        }
    }

    private void sendToTopicMarkdown(int topicId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(props.telegram().groupChatId())
                    .messageThreadId(topicId)
                    .text(text)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            System.err.println("sendToTopicMarkdown error: " + e.getMessage());
        }
    }
}