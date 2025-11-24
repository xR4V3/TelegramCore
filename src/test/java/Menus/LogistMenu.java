package Menus;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import core.Main;
import utils.*;
import java.util.*;

public class LogistMenu {

    public void open(Update update) {
        List<List<KeyboardButton>> buttons = Arrays.asList(
                Arrays.asList(
                        new KeyboardButton(ELogistMenuBtn.DRIVERS.getButtonText()),
                        new KeyboardButton(ELogistMenuBtn.ROUTES.getButtonText())
                ),
                Arrays.asList(
                        new KeyboardButton(ELogistMenuBtn.SALARIES.getButtonText()),
                        new KeyboardButton(ELogistMenuBtn.OTHER.getButtonText())
                )
        );
        Main.getInstance().sendKeyboard(update.message().chat().id(), Messages.logistMenu, buttons, true, false);
    }

    public void open(Update update, String msg) {
        List<List<KeyboardButton>> buttons = Arrays.asList(
                Arrays.asList(
                        new KeyboardButton(ELogistMenuBtn.DRIVERS.getButtonText()),
                        new KeyboardButton(ELogistMenuBtn.ROUTES.getButtonText())
                ),
                Arrays.asList(
                        new KeyboardButton(ELogistMenuBtn.SALARIES.getButtonText()),
                        new KeyboardButton(ELogistMenuBtn.OTHER.getButtonText())
                )
        );
        Main.getInstance().sendKeyboard(update.message().chat().id(), msg, buttons, true, false);
    }


    public void drivers_add(Update update) {
        String data = update.callbackQuery().data();
        if (data.equals("drivers:add")) {
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            // Список юзеров (имя + роль)
            for (UserData user : Main.users) {
                String text = (user.getName() != null ? user.getName() : "—") + " • " +
                        (user.getRole() != null ? user.getRole() : "—");
                if(user.getRole().equals("DRIVER")) {
                    keyboard.add(Collections.singletonList(
                            new InlineKeyboardButton(text).callbackData("user:view:" + user.getPhone())
                    ));
                }
            }

            // Кнопки "Добавить" и "Удалить"
            keyboard.add(Arrays.asList(
                    new InlineKeyboardButton("➕ Добавить").callbackData("user:add"),
                    new InlineKeyboardButton("❌ Удалить").callbackData("user:delete")
            ));

            Main.getInstance().sendInlineKeyboard(update.callbackQuery().message().chat().id(), keyboard, "👥 Список Водителей:");
        }
    }
}
