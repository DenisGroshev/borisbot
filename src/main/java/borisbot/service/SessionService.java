package borisbot.service;

import borisbot.properties.UserSession;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class SessionService {

    private final Map<Long, UserSession> sessionsByClient = new HashMap<>();
    private final Map<Integer, Long> topicToClient = new HashMap<>();

    public UserSession getOrCreate(long chatId, User from) {
        return sessionsByClient.computeIfAbsent(chatId, id -> {
            UserSession s = new UserSession();
            s.clientChatId = chatId;
            s.username = from.getUserName() != null ? "@" + from.getUserName() : "(нет username)";
            s.fullName = from.getFirstName()
                    + (from.getLastName() != null ? " " + from.getLastName() : "");
            return s;
        });
    }

    public Optional<UserSession> getByTopic(int topicId) {
        Long clientChatId = topicToClient.get(topicId);
        if (clientChatId == null) return Optional.empty();
        return Optional.ofNullable(sessionsByClient.get(clientChatId));
    }

    public void bindTopic(int topicId, long clientChatId) {
        topicToClient.put(topicId, clientChatId);
    }
}