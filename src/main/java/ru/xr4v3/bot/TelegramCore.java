package ru.xr4v3.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.GetFileResponse;
import com.pengrad.telegrambot.response.SendResponse;
import ru.xr4v3.bot.events.EventHandler;
import ru.xr4v3.bot.events.TelegramEvent;
import ru.xr4v3.bot.events.annotations.OnCallbackQuery;
import ru.xr4v3.bot.events.annotations.OnInlineQuery;
import ru.xr4v3.bot.events.annotations.OnMessage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    public void sendDocument(Long chatId, File file, String caption) {
        SendDocument req = new SendDocument(chatId, file);
        if (caption != null && !caption.isEmpty()) {
            req.caption(caption).parseMode(ParseMode.Markdown);
        }
        bot.execute(req);
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
        SendResponse response = bot.execute(new SendMessage(chatId, message).replyMarkup(markup));
        if (!response.isOk()) {
            System.err.println("Ошибка Telegram API: " + response.description());
        }
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

    public void editMessage(Long chatId, Integer messageId, String text) {
        EditMessageText edit = new EditMessageText(chatId, messageId, text);
        bot.execute(edit);
    }

    public void editMessage(Long chatId, Integer messageId, String text, List<List<InlineKeyboardButton>> buttonRows) {
        InlineKeyboardButton[][] keyboard = buttonRows.stream()
                .map(row -> row.toArray(new InlineKeyboardButton[0]))
                .toArray(InlineKeyboardButton[][]::new);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboard);
        EditMessageText edit = new EditMessageText(chatId, messageId, text).replyMarkup(markup);
        BaseResponse response = bot.execute(edit);
        if (!response.isOk()) {
            System.err.println("Ошибка Telegram API: " + response.description());
        }
    }

    public void editKeyboard(Long chatId, Integer messageId, List<List<InlineKeyboardButton>> buttonRows) {
        InlineKeyboardButton[][] keyboard = buttonRows.stream()
                .map(row -> row.toArray(new InlineKeyboardButton[0]))
                .toArray(InlineKeyboardButton[][]::new);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboard);
        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup(chatId, messageId).replyMarkup(markup);
        bot.execute(editMarkup);
    }

    // TelegramCore.java
    public void editMessage(Long chatId, Integer messageId, String text, ParseMode parseMode, List<List<InlineKeyboardButton>> buttonRows) {
        InlineKeyboardButton[][] keyboard = buttonRows.stream()
                .map(row -> row.toArray(new InlineKeyboardButton[0]))
                .toArray(InlineKeyboardButton[][]::new);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboard);
        EditMessageText edit = new EditMessageText(chatId, messageId, text)
                .parseMode(parseMode)
                .replyMarkup(markup);
        BaseResponse response = bot.execute(edit);
        if (!response.isOk()) {
            System.err.println("Ошибка Telegram API: " + response.description());
        }
    }


    public void forwardMessage(Long toChatId, Long fromChatId, Integer messageId) {
        ForwardMessage request = new ForwardMessage(toChatId, fromChatId, messageId);
        bot.execute(request);
    }

    /**
     * Получает InputStream содержимого файла по fileId
     *
     * @param filePath Telegram file_id
     * @return InputStream с содержимым или null при ошибке
     */
    public InputStream downloadFile(String filePath) {
        GetFile getFile = new GetFile(filePath);
        GetFileResponse fileResponse = bot.execute(getFile);

        if (!fileResponse.isOk()) {
            System.out.println("Ошибка получения файла: " + fileResponse.description());
            return null;
        }

        String fullPath = fileResponse.file().filePath();
        try {
            byte[] fileBytes = bot.getFileContent(fileResponse.file());
            return new ByteArrayInputStream(fileBytes);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void sendPhoto(Long chatId, File photoFile) {
        SendPhoto sendPhoto = new SendPhoto(chatId, photoFile);
        bot.execute(sendPhoto);
    }

    /**
     * Отправляет группу фото (медиа-группу) в чат.
     *
     * @param chatId ID чата для отправки.
     * @param photos Список файлов с фотографиями.
     */
    public void sendMediaGroup(Long chatId, List<File> photos) {
        if (photos == null || photos.isEmpty()) {
            return;
        }

        List<InputMedia> mediaList = new ArrayList<>();
        for (File photo : photos) {
            mediaList.add(new InputMediaPhoto(photo));
        }

        InputMedia<?>[] mediaArray = mediaList.toArray(new InputMedia[0]);

        SendMediaGroup sendMediaGroup = new SendMediaGroup(chatId, mediaArray);
        bot.execute(sendMediaGroup);
    }


    public void deleteMessage(Long chatId, Integer messageId) {
        DeleteMessage delete = new DeleteMessage(chatId.toString(), messageId);
        bot.execute(delete);
    }

}
