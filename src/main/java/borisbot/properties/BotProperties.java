package borisbot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "")
public record BotProperties(
        Bot bot,
        Telegram telegram,
        Dialog dialog
) {
    public record Bot(String token, String username) {}

    public record Telegram(String groupChatId, String ownerChatId) {}

    public record Dialog(
            Messages messages,
            List<String> categories,
            List<String> volumes
    ) {
        public record Messages(
                String welcome,
                String askVolume,
                String askFiles,
                String done,
                String forwarded
        ) {}
    }
}