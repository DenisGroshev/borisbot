package borisbot.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Считает среднее время ответа менеджера по последним N диалогам.
 * Обновляется каждый раз когда менеджер отвечает в топик.
 */
@Service
public class ResponseTimeService {

    // Храним последние 20 времён ответа
    private static final int WINDOW = 20;
    private final Deque<Long> responseTimes = new ArrayDeque<>();

    /**
     * Вызывается когда менеджер впервые ответил клиенту.
     * @param waitStart — момент когда клиент начал ждать
     */
    public void recordResponse(Instant waitStart) {
        long seconds = Duration.between(waitStart, Instant.now()).getSeconds();
        if (seconds < 0) return;

        responseTimes.addLast(seconds);
        if (responseTimes.size() > WINDOW) {
            responseTimes.removeFirst();
        }
    }

    /**
     * Возвращает строку вида "~5 минут" или "~2 часа".
     * Если данных ещё нет — возвращает дефолтный текст.
     */
    public String getAverageResponseText() {
        if (responseTimes.isEmpty()) {
            return "в течение нескольких минут";
        }

        long avgSeconds = (long) responseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        return formatDuration(avgSeconds);
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return "~" + seconds + " сек";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return "~" + minutes + " " + pluralMinutes(minutes);
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return "~" + hours + " " + pluralHours(hours);
        } else {
            long days = seconds / 86400;
            return "~" + days + " " + pluralDays(days);
        }
    }

    private String pluralMinutes(long n) {
        if (n % 10 == 1 && n % 100 != 11) return "минуту";
        if (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20)) return "минуты";
        return "минут";
    }

    private String pluralHours(long n) {
        if (n % 10 == 1 && n % 100 != 11) return "час";
        if (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20)) return "часа";
        return "часов";
    }

    private String pluralDays(long n) {
        if (n % 10 == 1 && n % 100 != 11) return "день";
        if (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20)) return "дня";
        return "дней";
    }
}