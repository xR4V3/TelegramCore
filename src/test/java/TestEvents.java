import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import ru.xr4v3.bot.events.TelegramEvent;
import ru.xr4v3.bot.events.annotations.OnCallbackQuery;
import ru.xr4v3.bot.events.annotations.OnInlineQuery;
import ru.xr4v3.bot.events.annotations.OnMessage;
import java.util.Arrays;
import java.util.List;

public class TestEvents implements TelegramEvent {

    @OnInlineQuery
    public void handleOnInlineQuery(Update update){
        if(update.callbackQuery().data().contains("data1")){
            TestMain.getInstance().sendMessage(update.callbackQuery().from().id(), "Press InlineButton");
        }
    }

    @OnCallbackQuery
    public void handleCallbackQuery(Update update){
        if(update.message().text().contains("Button")){
            TestMain.getInstance().sendMessage(update.message().chat().id(), "Press Button");
        }
    }

    @OnMessage
    public void handleTextMessage(Update update) {
        if("/start".equals(update.message().text())) {
            String username = update.message().from().username();
            String reply = "Hello, " + (username != null ? "@" + username : "there!");
            System.out.println(reply);
            TestMain.getInstance().sendMessage(update.message().chat().id(), reply);

            List<List<InlineKeyboardButton>> buttonsInline = Arrays.asList(
                    Arrays.asList(
                            new InlineKeyboardButton("Button 1").callbackData("data1"),
                            new InlineKeyboardButton("Button 2").callbackData("data2")
                    ), // Первый ряд кнопок
                    Arrays.asList(
                            new InlineKeyboardButton("Button 3").callbackData("data3"),
                            new InlineKeyboardButton("Button 4").callbackData("data4")
                    ), // Второй ряд кнопок
                    Arrays.asList(
                            new InlineKeyboardButton("Button 5").callbackData("data5")
                    )  // Третий ряд с одной кнопкой
            );

            TestMain.getInstance().sendInlineKeyboard(update.message().chat().id(), buttonsInline, "Create InlineKeyboard:");

            List<List<KeyboardButton>> buttons = Arrays.asList(
                    Arrays.asList(new KeyboardButton("Button 1"), new KeyboardButton("Button 1")), // Первый ряд кнопок
                    Arrays.asList(new KeyboardButton("Button 1"), new KeyboardButton("Button 1")), // Второй ряд кнопок
                    List.of(new KeyboardButton("Button 1"))                // Третий ряд с одной кнопкой
            );
            TestMain.getInstance().sendKeyboard(update.message().chat().id(), "Create UserKeyboard:", buttons, true, true);
        }
    }
}
