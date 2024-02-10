package ru.xr4v3.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.SendMessage;
import ru.xr4v3.bot.events.EventHandler;
import ru.xr4v3.bot.events.TelegramEvent;
import ru.xr4v3.bot.events.annotations.OnCallbackQuery;
import ru.xr4v3.bot.events.annotations.OnInlineQuery;
import ru.xr4v3.bot.events.annotations.OnMessage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public abstract class TelegramCore {

    protected final TelegramBot bot;

    public TelegramCore(String token) {
        this.bot = new TelegramBot(token);
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                // Для каждого зарегистрированного обработчика
                for (TelegramEvent handler : EventHandler.getInstance().getEventHandlers()) {
                    // Перебор методов обработчика
                    for (Method method : handler.getClass().getMethods()) {
                        // Вызов методов на основе типа события и наличия аннотаций
                        if (update.message() != null && method.isAnnotationPresent(OnMessage.class)) {
                            invokeEventHandlerMethod(method, handler, update);
                        }
                        if (update.message() != null && method.isAnnotationPresent(OnCallbackQuery.class)) {
                            invokeEventHandlerMethod(method, handler, update);
                        }
                        if (update.callbackQuery() != null && method.isAnnotationPresent(OnInlineQuery.class)) {
                            invokeEventHandlerMethod(method, handler, update);
                        }
                    }
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private void invokeEventHandlerMethod(Method method, TelegramEvent handler, Update update) {
        try {
            method.setAccessible(true);
            method.invoke(handler, update);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(Long chatId, String text) {
        bot.execute(new SendMessage(chatId, text));
    }

    public void sendKeyboard(Long chatId, String text, List<List<KeyboardButton>> buttonRows, boolean resizeKeyboard, boolean oneTimeKeyboard) {
        // Преобразование списка списков кнопок в массив массивов KeyboardButton
        KeyboardButton[][] keyboard = buttonRows.stream()
                .map(row -> row.toArray(new KeyboardButton[0]))
                .toArray(KeyboardButton[][]::new);

        // Создание клавиатуры с указанными параметрами
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(keyboard)
                .resizeKeyboard(resizeKeyboard)
                .oneTimeKeyboard(oneTimeKeyboard);

        // Отправка сообщения с клавиатурой
        bot.execute(new SendMessage(chatId, text).replyMarkup(replyKeyboardMarkup));
    }

    public void sendInlineKeyboard(Long chatId, List<List<InlineKeyboardButton>> buttonRows, String message) {
        // Создание списка строк для клавиатуры
        List<InlineKeyboardButton[]> keyboard = new ArrayList<>();

        // Преобразование списка списков кнопок в массив массивов InlineKeyboardButton
        for (List<InlineKeyboardButton> row : buttonRows) {
            keyboard.add(row.toArray(new InlineKeyboardButton[0]));
        }

        // Создание клавиатуры с указанными параметрами
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboard.toArray(new InlineKeyboardButton[0][0]));

        // Отправка сообщения с инлайн-клавиатурой
        bot.execute(new SendMessage(chatId, message).replyMarkup(markup));
    }

    /**
     * Упрощенная отправка текстовых сообщений с настройкой форматирования и клавиатуры.
     *
     * @param chatId ID чата для отправки сообщения.
     * @param text Текст сообщения для отправки.
     * @param parseMode Форматирование текста сообщения (Markdown, HTML или null).
     * @param replyMarkup Опциональная клавиатура для сообщения (может быть ReplyKeyboardMarkup или InlineKeyboardMarkup, или null).
     */
    public void sendMessage(Long chatId, String text, ParseMode parseMode, Object replyMarkup) {
        SendMessage request = new SendMessage(chatId, text);

        // Установка формата текста, если указан
        if (parseMode != null) {
            request.parseMode(parseMode);
        }

        // Установка клавиатуры, если указана
        if (replyMarkup != null) {
            if (replyMarkup instanceof InlineKeyboardMarkup) {
                request.replyMarkup((InlineKeyboardMarkup) replyMarkup);
            } else if (replyMarkup instanceof ReplyKeyboardMarkup) {
                request.replyMarkup((ReplyKeyboardMarkup) replyMarkup);
            }
        }

        bot.execute(request);
    }

    /**
     * Упрощенная отправка текстовых сообщений с настройкой форматирования и клавиатуры.
     *
     * @param chatId ID чата для отправки сообщения.
     * @param text Текст сообщения для отправки.
     * @param parseMode Форматирование текста сообщения (Markdown, HTML или null).
     */
    public void sendMessage(Long chatId, String text, ParseMode parseMode) {
        SendMessage request = new SendMessage(chatId, text);

        // Установка формата текста, если указан
        if (parseMode != null) {
            request.parseMode(parseMode);
        }

        bot.execute(request);
    }

    /**
     * Упрощенная отправка текстовых сообщений с настройкой форматирования и клавиатуры.
     *
     * @param chatId ID чата для отправки сообщения.
     * @param text Текст сообщения для отправки.
     * @param replyMarkup Опциональная клавиатура для сообщения (может быть ReplyKeyboardMarkup или InlineKeyboardMarkup, или null).
     */
    public void sendMessage(Long chatId, String text, Object replyMarkup) {
        SendMessage request = new SendMessage(chatId, text);

        // Установка клавиатуры, если указана
        if (replyMarkup != null) {
            if (replyMarkup instanceof InlineKeyboardMarkup) {
                request.replyMarkup((InlineKeyboardMarkup) replyMarkup);
            } else if (replyMarkup instanceof ReplyKeyboardMarkup) {
                request.replyMarkup((ReplyKeyboardMarkup) replyMarkup);
            }
        }

        bot.execute(request);
    }

}
