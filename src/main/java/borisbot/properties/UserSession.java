package borisbot.properties;

import borisbot.service.DialogState;

import java.time.Instant;

public class UserSession {
    public long clientChatId;
    public String username;
    public String fullName;

    public DialogState state = DialogState.START;

    // ID сообщения которое редактируем на каждом шаге диалога
    public int dialogMessageId = 0;

    public String category;
    public String volume;
    public String filesDescription;

    // fileId фото-макета (если клиент прислал фото, а не текст)
    public String photoFileId = null;

    public int topicId = -1;
    public Instant waitingSince;
}