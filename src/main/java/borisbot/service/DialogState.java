package borisbot.service;

public enum DialogState {
    START,
    ASK_CATEGORY,   // выбор: мерч или сувенирка (кнопки)
    ASK_VOLUME,     // выбор тиража (кнопки)
    ASK_FILES,      // ждём файл или текст с макетом
    DONE            // передан менеджеру
}