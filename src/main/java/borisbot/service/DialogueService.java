package borisbot.service;

import borisbot.properties.BotProperties;
import borisbot.properties.UserSession;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Service
public class DialogueService {

    private final BotProperties props;
    private final NotificationService notificationService;
    private final SessionService sessionService;
    private org.telegram.telegrambots.bots.TelegramLongPollingBot bot;

    public DialogueService(BotProperties props, NotificationService notificationService,
                           SessionService sessionService) {
        this.props = props;
        this.notificationService = notificationService;
        this.sessionService = sessionService;
    }

    public void setBot(org.telegram.telegrambots.bots.TelegramLongPollingBot bot) {
        this.bot = bot;
    }

    // ── Входящий текст/команда от клиента ────────────────────────────────────

    public void handleText(UserSession session, String text) {
        if (session.state == DialogState.DONE) {
            if (session.topicId != -1) {
                notificationService.sendToTopic(session.topicId, "📩 Клиент: " + text);
            }
            return;
        }

        switch (session.state) {
            case START -> showWelcome(session);
            case ASK_FILES -> {
                session.filesDescription = text;
                finishDialog(session);
            }
            default -> {
                // Ждём нажатия кнопки — текст игнорируем
            }
        }
    }

    // ── Входящий файл от клиента ─────────────────────────────────────────────

    public void handleFile(UserSession session, String fileDescription) {
        if (session.state == DialogState.DONE) {
            if (session.topicId != -1) {
                notificationService.sendToTopic(session.topicId, "📎 Клиент прислал файл: " + fileDescription);
            }
            return;
        }

        if (session.state == DialogState.ASK_FILES) {
            session.filesDescription = fileDescription;
            finishDialog(session);
        }
    }

    // ── Входящий callback от inline-кнопки ───────────────────────────────────

    public void handleCallback(UserSession session, String data) {
        switch (session.state) {
            case ASK_CATEGORY -> {
                session.category = data;
                session.state = DialogState.ASK_VOLUME;
                editToVolumeButtons(session);
            }
            case ASK_VOLUME -> {
                session.volume = data;
                session.state = DialogState.ASK_FILES;
                editToAskFiles(session);
            }
            default -> {}
        }
    }

    // ── Шаги диалога ─────────────────────────────────────────────────────────

    private void showWelcome(UserSession session) {
        List<List<InlineKeyboardButton>> rows = buildCategoryRows();
        try {
            Message sent = bot.execute(SendMessage.builder()
                    .chatId(String.valueOf(session.clientChatId))
                    .text(props.dialog().messages().welcome() + "\n\nВыберите категорию:")
                    .parseMode("Markdown")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                    .build());
            session.dialogMessageId = sent.getMessageId();
        } catch (Exception e) {
            System.err.println("showWelcome error: " + e.getMessage());
        }
        session.state = DialogState.ASK_CATEGORY;
    }

    private void editToVolumeButtons(UserSession session) {
        List<List<InlineKeyboardButton>> rows = buildVolumeRows();
        String text = "✅ Категория: *" + session.category + "*\n\n"
                + props.dialog().messages().askVolume();
        editMessage(session, text, rows);
    }

    private void editToAskFiles(UserSession session) {
        String text = "✅ Категория: *" + session.category + "*\n"
                + "✅ Тираж: *" + session.volume + "*\n\n"
                + props.dialog().messages().askFiles();
        editMessageNoButtons(session, text);
    }

    private void finishDialog(UserSession session) {
        session.state = DialogState.DONE;

        String caption = "✅ Категория: *" + session.category + "*\n"
                + "✅ Тираж: *" + session.volume + "*\n"
                + "✅ Макеты: получены\n\n"
                + props.dialog().messages().done();

        if (session.photoFileId != null) {
            editMessageToPhoto(session, session.photoFileId, caption);
        } else {
            editMessageNoButtons(session, caption);
        }

        int topicId = notificationService.createLeadTopic(session);
        session.topicId = topicId;
        sessionService.bindTopic(topicId, session.clientChatId);
    }

    // ── Редактирование сообщения ──────────────────────────────────────────────

    private void editMessage(UserSession session, String text, List<List<InlineKeyboardButton>> rows) {
        if (session.dialogMessageId == 0) return;
        try {
            bot.execute(EditMessageText.builder()
                    .chatId(String.valueOf(session.clientChatId))
                    .messageId(session.dialogMessageId)
                    .text(text)
                    .parseMode("Markdown")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                    .build());
        } catch (Exception e) {
            System.err.println("editMessage error: " + e.getMessage());
        }
    }

    private void editMessageNoButtons(UserSession session, String text) {
        if (session.dialogMessageId == 0) return;
        try {
            bot.execute(EditMessageText.builder()
                    .chatId(String.valueOf(session.clientChatId))
                    .messageId(session.dialogMessageId)
                    .text(text)
                    .parseMode("Markdown")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(List.of()).build())
                    .build());
        } catch (Exception e) {
            System.err.println("editMessageNoButtons error: " + e.getMessage());
        }
    }

    private void editMessageToPhoto(UserSession session, String fileId, String caption) {
        if (session.dialogMessageId == 0) return;
        try {
            InputMediaPhoto media = InputMediaPhoto.builder()
                    .media(fileId)
                    .caption(caption)
                    .parseMode("Markdown")
                    .build();
            bot.execute(EditMessageMedia.builder()
                    .chatId(String.valueOf(session.clientChatId))
                    .messageId(session.dialogMessageId)
                    .media(media)
                    .build());
        } catch (Exception e) {
            System.err.println("editMessageToPhoto error: " + e.getMessage());
            // Fallback — просто текст если фото не вышло
            editMessageNoButtons(session, caption);
        }
    }

    // ── Построение кнопок ─────────────────────────────────────────────────────

    private List<List<InlineKeyboardButton>> buildCategoryRows() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String cat : props.dialog().categories()) {
            rows.add(List.of(
                    InlineKeyboardButton.builder().text(cat).callbackData(cat).build()
            ));
        }
        return rows;
    }

    private List<List<InlineKeyboardButton>> buildVolumeRows() {
        List<String> volumes = props.dialog().volumes();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < volumes.size(); i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                    .text(volumes.get(i)).callbackData(volumes.get(i)).build());
            if (i + 1 < volumes.size()) {
                row.add(InlineKeyboardButton.builder()
                        .text(volumes.get(i + 1)).callbackData(volumes.get(i + 1)).build());
            }
            rows.add(row);
        }
        return rows;
    }
}