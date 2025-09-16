package events;

import Menus.*;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import core.Main;
import modules.Checks;
import modules.OrderLoader;
import modules.Routes;
import ru.xr4v3.bot.events.TelegramEvent;
import ru.xr4v3.bot.events.annotations.OnCallbackQuery;
import ru.xr4v3.bot.events.annotations.OnInlineQuery;
import ru.xr4v3.bot.events.annotations.OnMessage;
import utils.*;

import java.util.*;

public class GlobalEvents implements TelegramEvent {

    private DriverMenu driverMenu;
    private LogistMenu logistMenu;
    private AdminMenu adminMenu;
    private ManagerMenu managerMenu;
    private CourierMenu courierMenu;

    public GlobalEvents(DriverMenu driverMenu, LogistMenu logistMenu,ManagerMenu managerMenu, AdminMenu adminMenu, CourierMenu courierMenu) {
        this.driverMenu = driverMenu;
        this.logistMenu = logistMenu;
        this.managerMenu = managerMenu;
        this.adminMenu = adminMenu;
        this.courierMenu = courierMenu;
    }

    @OnInlineQuery
    public void handleOnInlineQuery(Update update){
        driverMenu.orders(update);
        adminMenu.users(update);
        managerMenu.confirm(update);
        logistMenu.drivers_add(update);
        OrderLoader.getDriverOrders(update);
        Routes.handleRouteCallback(update);
        Checks.handleChecksCallback(update);
    }



    @OnCallbackQuery
    public void handleCallbackQuery(Update update){
        if(update.message().text() == null) return;
        Long userId = update.message().from().id();
        UserData user = UserData.findUserById(userId);

        if(update.message().text().contains(ELogistMenuBtn.DRIVERS.getButtonText()) || update.message().text().contains(EManagerMenuBtn.DRIVERS.getButtonText())){
            OrderLoader.drivers(update);
            return;
        }

        if (update.message().text().contains(EDriverMenuBtn.ROUTES.getButtonText())) {
            if(user.getRole().equals("DRIVER")){
                Routes.showOrdersMenu(update);
            }
        }

        if(update.message().text().contains(EAdminMenuBtn.ROUTES.getButtonText()) || update.message().text().contains(ELogistMenuBtn.ROUTES.getButtonText())){
            if(user.getRole().equals("LOGISTIC") || user.getRole().equals("ADMIN")){
                Routes.showDriversRoutesList(update);
            }
        }

        if(update.message().text().contains(EAdminMenuBtn.USERS.getButtonText())) {
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            // Список юзеров (имя + роль)
            for (UserData user2 : Main.users) {
                String text = (user2.getName() != null ? user2.getName() : "—") + " • " +
                        (user2.getRole() != null ? user2.getRole() : "—");
                keyboard.add(Collections.singletonList(
                        new InlineKeyboardButton(text).callbackData("user:view:" + user2.getPhone())
                ));
            }

            // Кнопки "Добавить" и "Удалить"
            keyboard.add(Arrays.asList(
                    new InlineKeyboardButton("➕ Добавить").callbackData("user:add"),
                    new InlineKeyboardButton("❌ Удалить").callbackData("user:delete")
            ));

            Main.getInstance().sendInlineKeyboard(update.message().chat().id(), keyboard, "👥 Список пользователей:");
            return;
        }

        if(update.message().text().contains(EAdminMenuBtn.ORDERS.getButtonText())) {
            OrderLoader.drivers(update);
        }

        if(update.message().text().contains(ECourierMenuBtn.ROUTES.getButtonText())) {
            String msg = CourierMenu.getOrdersForTomorrowOrWeekend(OrderLoader.orders);
            Main.getInstance().sendMessage(update.message().chat().id(), msg);
        }

    }

    @OnMessage
    public void handleTextMessage(Update update) {
        if("/start".equals(update.message().text())) {
            UserData user = UserData.findUserById(update.message().from().id());
            if(user != null) {
                if (user.getRole() != null) {
                    String role = user.getRole().toUpperCase();
                    String greeting = "Привет, " + user.getName() + " \uD83D\uDC4B";

                    switch (role) {
                        case "ADMIN" -> adminMenu.open(update, greeting);
                        case "LOGISTIC" -> logistMenu.open(update, greeting);
                        case "MANAGER" -> managerMenu.open(update, greeting);
                        case "DRIVER" -> driverMenu.open(update, greeting);
                        case "COURIER" -> courierMenu.open(update, greeting);
                        default -> Main.getInstance().sendMessage(update.message().chat().id(),
                                "Извините, роль не определена. Обратитесь к администратору.");
                    }
                } else {
                    Main.getInstance().sendMessage(update.message().chat().id(),
                            "Извините, роль не найдена. Обратитесь к администратору.");
                }
            }else{
                List<KeyboardButton> keyboardButtons = Collections.singletonList(
                        new KeyboardButton("Отправить номер телефона").requestContact(true)
                );

                Main.getInstance().sendKeyboard(update.message().chat().id(), "Для авторизации нажмите 'Отправить номер телефона'", Collections.singletonList(keyboardButtons), true, false);

            }
        }
        if (update.message().contact() != null) {
            String phone = UserData.normalizePhone(update.message().contact().phoneNumber());
            Long userId = update.message().from().id();
            UserData user = UserData.findUserByPhone(phone);
            if (user != null) {
                user.setId(userId);  // записываем ID Telegram пользователя
                UserData.saveUsersToFile();
                if (user.getRole() != null) {
                    String role = user.getRole().toUpperCase();
                    String greeting = "Привет, " + user.getName() + " \uD83D\uDC4B";

                    switch (role) {
                        case "ADMIN" -> adminMenu.open(update, greeting);
                        case "LOGISTIC" -> logistMenu.open(update, greeting);
                        case "MANAGER" -> managerMenu.open(update, greeting);
                        case "DRIVER" -> driverMenu.open(update, greeting);
                        case "COURIER" -> courierMenu.open(update, greeting);
                        default -> Main.getInstance().sendMessage(update.message().chat().id(),
                                "Извините, роль не определена. Обратитесь к администратору.");
                    }
                } else {
                    Main.getInstance().sendMessage(update.message().chat().id(),
                            "Извините, роль не найдена. Обратитесь к администратору.");
                }
            } else {
                Main.getInstance().sendMessage(update.message().chat().id(),
                        "Извините, номер не найден. Обратитесь к администратору.");
            }
        }

        if (update.message().text() != null && update.message().text().contains(";")) {
            String[] parts = update.message().text().split(";");
            if (parts.length == 3) {
                String phone = parts[0].trim();
                String name = parts[1].trim();
                String role = parts[2].trim().toUpperCase();

                try {
                    Users userRole = Users.valueOf(role);

                    boolean exists = Main.users.stream().anyMatch(u -> UserData.normalizePhone(u.getPhone()).equals(UserData.normalizePhone(phone)));

                    if (exists) {
                        Main.getInstance().sendMessage(update.message().chat().id(), "⚠ Пользователь с таким номером уже существует.");
                    } else {
                        UserData newUser = new UserData();
                        newUser.setPhone(UserData.normalizePhone(phone));
                        newUser.setName(name);
                        newUser.setRole(userRole.name());

                        Main.users.add(newUser);
                        UserData.saveUsersToFile();
                        Main.getInstance().sendMessage(update.message().chat().id(), "✅ Пользователь добавлен:\n" +
                                "ФИО: " + name + "\nТел: " + phone + "\nРоль: " + userRole.name());
                    }
                } catch (IllegalArgumentException e) {
                    Main.getInstance().sendMessage(update.message().chat().id(), "❌ Роль некорректна. Допустимые:\n" + Arrays.toString(Users.values()));
                }
            } else if (parts.length == 2) {
                String phone = parts[0].trim();
                String name = parts[1].trim();
                try {

                    boolean exists = Main.users.stream().anyMatch(u -> UserData.normalizePhone(u.getPhone()).equals(UserData.normalizePhone(phone)));

                    if (exists) {
                        Main.getInstance().sendMessage(update.message().chat().id(), "⚠ Пользователь с таким номером уже существует.");
                    } else {
                        UserData newUser = new UserData();
                        newUser.setPhone(UserData.normalizePhone(phone));
                        newUser.setName(name);
                        newUser.setRole("DRIVER");

                        Main.users.add(newUser);
                        UserData.saveUsersToFile();
                        Main.getInstance().sendMessage(update.message().chat().id(), "✅ Пользователь добавлен:\n" +
                                "ФИО: " + name + "\nТел: " + phone + "\nРоль: " + "DRIVER");
                    }
                } catch (IllegalArgumentException e) {
                    Main.getInstance().sendMessage(update.message().chat().id(), "❌ Роль некорректна. Допустимые:\n" + Arrays.toString(Users.values()));
                }
            } else {
                Main.getInstance().sendMessage(update.message().chat().id(), "⚠ Неверный формат. Используйте `Телефон;Имя;Роль`");
            }
        }

        Long chatId = update.message().chat().id();
        String state = Main.waitingForOrderNumber.get(chatId);

        if ("WAITING_FOR_ORDER_NUMBER".equals(state)) {
            String enteredOrderNumber = update.message().text().trim();
            String fileOrderNumber = enteredOrderNumber.replaceAll("[^0-9]", ""); // на случай, если пользователь ввёл с пробелами или лишними символами

            // Загружаем напрямую и добавляем, если найден
            Order order = OrderLoader.loadSingleOrder("orders", fileOrderNumber);
            if (order != null) {
                System.out.println("Заказ найден: " + order.orderNumber);

                // Проверим, не добавлен ли уже
                boolean alreadyInList = OrderLoader.orders.stream()
                        .anyMatch(o -> o.orderNumber.equals(order.orderNumber));

                if (!alreadyInList) {
                    OrderLoader.orders.add(order);
                }
            } else {
                System.out.println("Заказ не найден или ошибка при чтении.");
            }

            List<Order> driverOrders = OrderLoader.orders.stream()
                    .filter(o -> o.orderNumber != null && o.orderNumber.contains(enteredOrderNumber))
                    .toList();

            if (!driverOrders.isEmpty()) {
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                for (Order o : driverOrders) {
                    InlineKeyboardButton btn = new InlineKeyboardButton("📦 Заказ №" + o.orderNumber + " " + OrderStatus.getEmojiByStatus(OrderStatus.fromDisplayName(o.orderStatus)))
                            .callbackData("order:" + o.orderNumber);
                    keyboard.add(Collections.singletonList(btn));
                }

                Main.getInstance().sendInlineKeyboard(
                        chatId,
                        keyboard,
                        "🔍 Найдены заказы по номеру: " + enteredOrderNumber + ":"
                );
            } else {
                Main.getInstance().sendMessage(chatId,
                        "❌ Заказ с номером " + enteredOrderNumber + " не найден.", true);
            }

            // Сброс состояния
            Main.waitingForOrderNumber.remove(chatId);
        }

        if (update.message().text() != null) {
            String userText = update.message().text().trim();

            Set<String> buttonSet = new HashSet<>();
            for (var btn : EDriverMenuBtn.values()) buttonSet.add(btn.getButtonText());
            for (var btn : EAdminMenuBtn.values()) buttonSet.add(btn.getButtonText());
            for (var btn : ELogistMenuBtn.values()) buttonSet.add(btn.getButtonText());
            for (var btn : ECourierMenuBtn.values()) buttonSet.add(btn.getButtonText());
            for (var btn : EManagerMenuBtn.values()) buttonSet.add(btn.getButtonText());

            if (buttonSet.contains(userText)) {
                Main.getInstance().deleteMessage(update.message().chat().id(), update.message().messageId());
            }
        }

        if (update.message() != null && update.message().text() != null
                && update.message().text().trim().toLowerCase().startsWith("/setrole")) {

            String text = update.message().text().trim();
            UserData actor = UserData.findUserById(update.message().from().id());

            // Только ADMIN может менять роли
            if (actor == null || actor.getRole() == null || !actor.getRole().equalsIgnoreCase("ADMIN")) {
                Main.getInstance().sendMessage(update.message().chat().id(),
                        "⛔ У вас нет прав для смены роли.");
                return;
            }

            // Ожидаем формат: /setrole <телефон_или_ФИО>;<роль>
            String payload = text.substring("/setrole".length()).trim();
            if (!payload.contains(";")) {
                Main.getInstance().sendMessage(update.message().chat().id(),
                        "⚠ Неверный формат. Используйте:\n/setrole +79991234567;DRIVER\nили\n/setrole Иван Иванов;MANAGER");
                return;
            }

            String[] prts = payload.split(";", 2);
            String keyRaw  = prts[0].trim();            // телефон ИЛИ ФИО
            String roleRaw = prts[1].trim().toUpperCase();

            // Валидируем роль по enum Users
            Users newRole;
            try {
                newRole = Users.valueOf(roleRaw);
            } catch (IllegalArgumentException e) {
                Main.getInstance().sendMessage(update.message().chat().id(),
                        "❌ Роль некорректна. Допустимые: " + Arrays.toString(Users.values()));
                return;
            }

            // Пытаемся найти пользователя СНАЧАЛА по ФИО, затем по телефону
            UserData target = null;

            // 1) По ФИО (как есть) — через findUserByName
            target = UserData.findUserByName(keyRaw);

            // 2) Если не нашли по ФИО — пробуем по телефону (нормализуем и ищем в Main.users)
            if (target == null) {
                String normalizedPhone = UserData.normalizePhone(keyRaw);
                if (!normalizedPhone.isEmpty()) {
                    for (UserData u : Main.users) {
                        String up = (u.getPhone() != null) ? UserData.normalizePhone(u.getPhone()) : "";
                        if (!up.isEmpty() && up.equals(normalizedPhone)) {
                            target = u;
                            break;
                        }
                    }
                }
            }

            if (target == null) {
                Main.getInstance().sendMessage(update.message().chat().id(),
                        "❌ Пользователь не найден по \"" + keyRaw + "\".\nПопробуйте точное ФИО или номер телефона.");
                return;
            }

            // Применяем роль и сохраняем
            target.setRole(newRole.name());
            UserData.saveUsersToFile();

            Main.getInstance().sendMessage(update.message().chat().id(),
                    "✅ Роль изменена:\n" +
                            "ФИО: " + (target.getName() != null ? target.getName() : "—") + "\n" +
                            "Телефон: " + (target.getPhone() != null ? target.getPhone() : "—") + "\n" +
                            "Новая роль: " + target.getRole());

            return; // важно: чтобы далее не сработали другие обработчики текста с ';'
        }


    }
}
