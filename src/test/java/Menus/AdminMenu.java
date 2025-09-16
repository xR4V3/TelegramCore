package Menus;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import core.Main;
import utils.EAdminMenuBtn;
import utils.EDriverMenuBtn;
import utils.Messages;
import utils.UserData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdminMenu {

    public void open(Update update) {
        List<List<KeyboardButton>> buttons = Arrays.asList(
                Arrays.asList(
                        new KeyboardButton(EAdminMenuBtn.USERS.getButtonText()),
                        new KeyboardButton(EAdminMenuBtn.ORDERS.getButtonText())
                ),
                Arrays.asList(
                        new KeyboardButton(EAdminMenuBtn.OTHER.getButtonText()),
                        new KeyboardButton(EAdminMenuBtn.ROUTES.getButtonText()) // перенесли сюда
                )
        );
        Main.getInstance().sendKeyboard(update.message().chat().id(), Messages.adminMenu, buttons, true, false);
    }

    public void open(Update update, String msg) {
        List<List<KeyboardButton>> buttons = Arrays.asList(
                Arrays.asList(
                        new KeyboardButton(EAdminMenuBtn.USERS.getButtonText()),
                        new KeyboardButton(EAdminMenuBtn.ORDERS.getButtonText())
                ),
                Arrays.asList(
                        new KeyboardButton(EAdminMenuBtn.OTHER.getButtonText()),
                        new KeyboardButton(EAdminMenuBtn.ROUTES.getButtonText()) // перенесли сюда
                )
        );
        Main.getInstance().sendKeyboard(update.message().chat().id(), msg, buttons, true, false);
    }

    public void users(Update update) {
        Long chatId = update.callbackQuery().message().chat().id();
        Integer messageId = update.callbackQuery().message().messageId();
        String data = update.callbackQuery().data();
        Long userId = update.callbackQuery().from().id();
        UserData currentUser = UserData.findUserById(userId);
        // Главное меню пользователей
        if (data.equals("user:back")) {
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            // Список пользователей
            for (UserData user : Main.users) {
                String text = (user.getName() != null ? user.getName() : "—") + " • " +
                        (user.getRole() != null ? user.getRole() : "—");
                if(currentUser.getRole().equals("LOGISTIC")) {
                    if (user.getRole().equals("DRIVER")) {
                        keyboard.add(Collections.singletonList(
                                new InlineKeyboardButton(text).callbackData("user:view:" + user.getPhone())
                        ));
                    }
                } else{
                    keyboard.add(Collections.singletonList(
                            new InlineKeyboardButton(text).callbackData("user:view:" + user.getPhone())
                    ));
                }
            }

            // Кнопки управления
            keyboard.add(Arrays.asList(
                    new InlineKeyboardButton("➕ Добавить").callbackData("user:add"),
                    new InlineKeyboardButton("❌ Удалить").callbackData("user:delete")
            ));
            if(!currentUser.getRole().equals("LOGISTIC")) {
                Main.getInstance().editMessage(chatId, messageId, "👥 Список пользователей:", keyboard);
            } else {
                Main.getInstance().editMessage(chatId, messageId, "👥 Список Водителей:", keyboard);
            }
            return;
        }

        if (data.equals("user:add")) {
            String msg;
            if(!currentUser.getRole().equals("LOGISTIC")) {
                 msg = """
                        ➕ Для добавления пользователя отправьте сообщение в формате:
                        
                        Телефон;ФИО;Роль
                        
                        Пример:
                        +79991234567;Иван Иванов;DRIVER
                        
                        Список ролей:
                        ADMIN (Админ),
                        LOGISTIC (Логист),
                        MANAGER (Менеджер),
                        DRIVER (Водитель),
                        COURIER (Курьер)
                        """;
            } else {
                 msg = """
                        ➕ Для добавления водителя отправьте сообщение в формате:
                        
                        Телефон;ФИО
                        
                        Пример:
                        +79991234567;Иван Иванов

                        """;
            }

            List<List<InlineKeyboardButton>> backButton = List.of(
                    List.of(new InlineKeyboardButton("◀️ Назад").callbackData("user:back"))
            );

            Main.getInstance().editMessage(chatId, messageId, msg, backButton);
            return;
        }

        if (data.equals("user:delete")) {
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
            for (UserData user : Main.users) {
                String text = (user.getName() != null ? user.getName() : "—") + " • " +
                        (user.getRole() != null ? user.getRole() : "—");
                if(currentUser.getRole().equals("LOGISTIC")){
                    if(user.getRole().equals("DRIVER")) {
                        buttons.add(Collections.singletonList(
                                new InlineKeyboardButton("🗑 " + text).callbackData("user:delete:" + user.getPhone())
                        ));
                    }
                } else {
                    buttons.add(Collections.singletonList(
                            new InlineKeyboardButton("🗑 " + text).callbackData("user:delete:" + user.getPhone())
                    ));
                }

            }

            // Добавляем кнопку "Назад"
            buttons.add(List.of(new InlineKeyboardButton("◀️ Назад").callbackData("user:back")));

            Main.getInstance().editMessage(chatId, messageId, "Выберите пользователя для удаления:", buttons);
            return;
        }

        if (data.startsWith("user:delete:")) {
            String phone = data.substring("user:delete:".length());

            boolean removed = Main.users.removeIf(u -> phone.equals(u.getPhone()));
            List<List<InlineKeyboardButton>> backButton = List.of(
                    List.of(new InlineKeyboardButton("◀️ Назад").callbackData("user:back"))
            );
            if (removed) {
                UserData.saveUsersToFile();
                Main.getInstance().editMessage(chatId, messageId, "✅ Пользователь с номером " + phone + " удалён.", backButton);
            } else {
                Main.getInstance().editMessage(chatId, messageId, "⚠ Пользователь не найден.", backButton);
            }
            return;
        }

        if (data.startsWith("user:view:")) {
            String phone = data.substring("user:view:".length());

            UserData user = Main.users.stream()
                    .filter(u -> phone.equals(u.getPhone()))
                    .findFirst()
                    .orElse(null);

            if (user != null) {
                String info = String.format("""
            🧑 ФИО: %s
            📱 Телефон: %s
            🆔 Telegram ID: %s
            👑 Роль: %s
            """, user.getName(),
                        user.getPhone(),
                        user.getId() != null ? user.getId() : "—",
                        user.getRole());

                List<List<InlineKeyboardButton>> backButton = List.of(
                        List.of(new InlineKeyboardButton("◀️ Назад").callbackData("user:back"))
                );

                Main.getInstance().editMessage(chatId, messageId, info, backButton);
            } else {
                Main.getInstance().editMessage(chatId, messageId, "Пользователь не найден.");
            }
        }
    }


}
