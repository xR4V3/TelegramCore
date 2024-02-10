
# TelegramCore API

## Описание
TelegramCore - это обертка для Telegram Bot API, основанная на библиотеке pengrad. Этот API предназначен для упрощения создания ботов в Telegram, обеспечивая легкую настройку обработчиков для различных типов сообщений и запросов, а также удобное создание пользовательских клавиатур.

## Основные функции
- Обработка текстовых сообщений, inline-запросов и callback-запросов.
- Создание inline и обычных клавиатур с кнопками.
- Упрощенная отправка сообщений и клавиатур пользователям.

## Как использовать

### Подключение зависимостей
Для использования TelegramCore необходимо добавить зависимость библиотеки pengrad в ваш проект.

### Создание бота
1. Создайте класс, расширяющий `TelegramCore`, и инициализируйте его с вашим токеном бота.
2. Создайте классы событий, реализующие интерфейс `TelegramEvent`, и определите методы с аннотациями `@OnMessage`, `@OnInlineQuery`, и `@OnCallbackQuery` для обработки соответствующих событий.
3. Зарегистрируйте обработчики событий в вашем экземпляре `TelegramCore`.

### Примеры классов событий
#### Класс для обработки текстовых сообщений
```java
import com.pengrad.telegrambot.model.Update;
import ru.xr4v3.bot.events.TelegramEvent;
import ru.xr4v3.bot.events.annotations.OnMessage;

public class MessageHandler implements TelegramEvent {

    @OnMessage
    public void onMessageReceived(Update update) {
        // Ваша логика обработки сообщений
    }
}
```

#### Класс для обработки inline-запросов
```java
import com.pengrad.telegrambot.model.Update;
import ru.xr4v3.bot.events.TelegramEvent;
import ru.xr4v3.bot.events.annotations.OnInlineQuery;

public class InlineQueryHandler implements TelegramEvent {

    @OnInlineQuery
    public void onInlineQueryReceived(Update update) {
        // Ваша логика обработки inline-запросов
    }
}
```

#### Класс для обработки callback-запросов
```java
import com.pengrad.telegrambot.model.Update;
import ru.xr4v3.bot.events.TelegramEvent;
import ru.xr4v3.bot.events.annotations.OnCallbackQuery;

public class CallbackQueryHandler implements TelegramEvent {

    @OnCallbackQuery
    public void onCallbackQueryReceived(Update update) {
        // Ваша логика обработки callback-запросов
    }
}
```

## Получение доступа
Для использования этого API вам необходимо создать бота через BotFather в Telegram и получить токен.

## Поддержка
Для получения помощи и поддержки вы можете создать issue в репозитории проекта на GitHub или связаться со мной напрямую через Telegram.
