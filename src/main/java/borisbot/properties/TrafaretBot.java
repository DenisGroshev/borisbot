package borisbot.properties;

import borisbot.service.DialogueService;
import borisbot.service.NotificationService;
import borisbot.service.ResponseTimeService;
import borisbot.service.SessionService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;

@Component
public class TrafaretBot extends TelegramLongPollingBot {

    private final BotProperties props;
    private final SessionService sessionService;
    private final DialogueService dialogService;
    private final NotificationService notificationService;
    private final ResponseTimeService responseTimeService;

    public TrafaretBot(BotProperties props,
                       SessionService sessionService,
                       DialogueService dialogService,
                       NotificationService notificationService,
                       ResponseTimeService responseTimeService) {
        this.props = props;
        this.sessionService = sessionService;
        this.dialogService = dialogService;
        this.notificationService = notificationService;
        this.responseTimeService = responseTimeService;
    }

    @PostConstruct
    public void init() {
        notificationService.setBot(this);
        dialogService.setBot(this);
    }

    @Override
    public String getBotToken() { return props.bot().token(); }

    @Override
    public String getBotUsername() { return props.bot().username(); }

    @Override
    public void onUpdateReceived(Update update) {

        // ── Нажатие inline-кнопки ────────────────────────────────────────────
        if (update.hasCallbackQuery()) {
            CallbackQuery callback = update.getCallbackQuery();
            long chatId = callback.getFrom().getId();
            String data = callback.getData();

            try {
                execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(callback.getId())
                        .build());
            } catch (Exception ignored) {}

            UserSession session = sessionService.getOrCreate(chatId, callback.getFrom());
            dialogService.handleCallback(session, data);
            return;
        }

        if (!update.hasMessage()) return;

        Message msg = update.getMessage();
        long chatId = msg.getChatId();

        // ── Сообщение из ГРУППЫ (ответ менеджера) ───────────────────────────
        if (String.valueOf(chatId).equals(props.telegram().groupChatId())) {
            Integer threadId = msg.getMessageThreadId();
            if (threadId == null) return;

            sessionService.getByTopic(threadId).ifPresent(session -> {
                if (session.waitingSince != null) {
                    responseTimeService.recordResponse(session.waitingSince);
                    session.waitingSince = null;
                }

                if (msg.hasText()) {
                    notificationService.sendToClient(session.clientChatId, "💬 " + msg.getText());
                } else if (msg.hasPhoto()) {
                    String fileId = msg.getPhoto().get(msg.getPhoto().size() - 1).getFileId();
                    try {
                        execute(SendPhoto.builder()
                                .chatId(String.valueOf(session.clientChatId))
                                .photo(new InputFile(fileId))
                                .caption(msg.getCaption() != null ? "💬 " + msg.getCaption() : "💬 Менеджер прислал фото")
                                .build());
                    } catch (Exception e) {
                        notificationService.sendToClient(session.clientChatId, "💬 Менеджер прислал фото");
                    }
                } else if (msg.hasDocument()) {
                    try {
                        execute(SendDocument.builder()
                                .chatId(String.valueOf(session.clientChatId))
                                .document(new InputFile(msg.getDocument().getFileId()))
                                .caption(msg.getCaption() != null ? "💬 " + msg.getCaption() : "💬 Менеджер прислал файл")
                                .build());
                    } catch (Exception e) {
                        notificationService.sendToClient(session.clientChatId, "💬 Менеджер прислал файл");
                    }
                }
            });
            return;
        }

        // ── Сообщение от КЛИЕНТА ─────────────────────────────────────────────
        UserSession session = sessionService.getOrCreate(chatId, msg.getFrom());

        if (msg.hasText()) {
            dialogService.handleText(session, msg.getText().trim());

        } else if (msg.hasPhoto()) {
            String fileId = msg.getPhoto().get(msg.getPhoto().size() - 1).getFileId();
            String caption = msg.getCaption();
            handleClientPhoto(session, fileId, caption);

        } else if (msg.hasDocument()) {
            String fileId = msg.getDocument().getFileId();
            String name = msg.getDocument().getFileName() != null
                    ? msg.getDocument().getFileName() : "документ";
            handleClientDocument(session, fileId, name);
        }
    }

    private void handleClientPhoto(UserSession session, String fileId, String caption) {
        if (session.state == borisbot.service.DialogState.DONE) {
            if (session.topicId != -1) {
                try {
                    execute(SendPhoto.builder()
                            .chatId(props.telegram().groupChatId())
                            .messageThreadId(session.topicId)
                            .photo(new InputFile(fileId))
                            .caption(caption != null ? "📸 Клиент: " + caption : "📸 Клиент прислал фото")
                            .build());
                } catch (Exception e) {
                    System.err.println("sendPhoto to topic error: " + e.getMessage());
                }
            }
        } else if (session.state == borisbot.service.DialogState.ASK_FILES) {
            // Сохраняем fileId — DialogueService использует его для editMessageMedia
            session.photoFileId = fileId;
            String desc = caption != null ? "[фото] " + caption : "[фото]";
            dialogService.handleFile(session, desc);
            // Дополнительно пересылаем фото в топик
            forwardPhotoToTopic(session, fileId, caption);
        }
    }

    private void handleClientDocument(UserSession session, String fileId, String name) {
        if (session.state == borisbot.service.DialogState.DONE) {
            if (session.topicId != -1) {
                try {
                    execute(SendDocument.builder()
                            .chatId(props.telegram().groupChatId())
                            .messageThreadId(session.topicId)
                            .document(new InputFile(fileId))
                            .caption("📎 Клиент прислал файл: " + name)
                            .build());
                } catch (Exception e) {
                    System.err.println("sendDocument to topic error: " + e.getMessage());
                }
            }
        } else if (session.state == borisbot.service.DialogState.ASK_FILES) {
            dialogService.handleFile(session, name);
            forwardDocumentToTopic(session, fileId, name);
        }
    }

    private void forwardPhotoToTopic(UserSession session, String fileId, String caption) {
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            if (session.topicId == -1) return;
            try {
                execute(SendPhoto.builder()
                        .chatId(props.telegram().groupChatId())
                        .messageThreadId(session.topicId)
                        .photo(new InputFile(fileId))
                        .caption(caption != null ? "📸 Макет клиента: " + caption : "📸 Макет клиента")
                        .build());
            } catch (Exception e) {
                System.err.println("forwardPhoto error: " + e.getMessage());
            }
        }).start();
    }

    private void forwardDocumentToTopic(UserSession session, String fileId, String name) {
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            if (session.topicId == -1) return;
            try {
                execute(SendDocument.builder()
                        .chatId(props.telegram().groupChatId())
                        .messageThreadId(session.topicId)
                        .document(new InputFile(fileId))
                        .caption("📎 Макет клиента: " + name)
                        .build());
            } catch (Exception e) {
                System.err.println("forwardDocument error: " + e.getMessage());
            }
        }).start();
    }
}