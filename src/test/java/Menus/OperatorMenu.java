package Menus;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import core.Main;
import utils.ECourierMenuBtn;
import utils.EOperatorMenuBtn;
import utils.Messages;

import java.util.List;

public class OperatorMenu {

    public void open(Update update) {
        List<List<KeyboardButton>> buttons = List.of(
                List.of(
                        new KeyboardButton(EOperatorMenuBtn.ROUTES.getButtonText()),
        new KeyboardButton(EOperatorMenuBtn.PARSERS.getButtonText())
                )
        );
        Main.getInstance().sendKeyboard(
                update.message().chat().id(),
                Messages.adminMenu,
                buttons,
                true,
                false
        );
    }

    public void open(Update update, String msg) {
        List<List<KeyboardButton>> buttons = List.of(
                List.of(
                        new KeyboardButton(EOperatorMenuBtn.ROUTES.getButtonText()),
            new KeyboardButton(EOperatorMenuBtn.PARSERS.getButtonText())
                )
        );
        Main.getInstance().sendKeyboard(
                update.message().chat().id(),
                msg,
                buttons,
                true,
                false
        );
    }

}
