package modules;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import core.Main;
import utils.EDriverMenuBtn;
import utils.Order;
import utils.UserData;

import java.awt.desktop.SystemEventListener;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class Routes {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static void showOrdersForDriver(Update update, LocalDate date) {
        long userId = (update.message() != null) ? update.message().from().id() : update.callbackQuery().from().id();
        long chatId = (update.message() != null) ? update.message().chat().id() : update.callbackQuery().message().chat().id();
        int messageId = update.callbackQuery().message().messageId();
        UserData user = UserData.findUserById(userId);
        if (user == null) {
            Main.getInstance().editMessage(chatId, messageId, "⚠️ Пользователь не найден.");
            return;
        }

        String driverName = user.getName();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        List<Order> allOrders = OrderLoader.orders;
        if (allOrders == null) allOrders = List.of();

        List<Order> ordersForDate = new ArrayList<>();
        for (Order order : allOrders) {
            if (order.deliveryDate == null || order.driver == null) continue;
            try {
                boolean sameDate = LocalDate.parse(order.deliveryDate.trim(), formatter).equals(date);
                boolean sameDriver = order.driver.trim().contains(driverName.trim()); // было contains
                if (sameDate && sameDriver) {
                    ordersForDate.add(order);
                }
            } catch (Exception ignore) { }
        }

        StringBuilder sb = new StringBuilder("📦 Ваш маршрут на " + date.format(DATE_FORMATTER) + ":\n\n");

        List<String> warehouses = getWarehousesForDriverDate(driverName, date);
        if (!warehouses.isEmpty()) {
            sb.append("🏭 Точки погрузки:\n");
            for (String w : warehouses) {
                sb.append("• ").append(w).append("\n");
            }
            sb.append("\n");
        }

// --- Затем блок доставок ---
        sb.append("📬 Доставки:\n\n");

        // Если заказы есть — считаем и показываем
        double totalWeight = 0.0;
        double totalVolume = 0.0;
        int orderCount = 0;
        double maxLength = 0.0;

        for (Order o : ordersForDate) {
            orderCount++;
            sb.append("• ").append(o.deliveryAddress != null ? o.deliveryAddress : "Адрес не указан").append("\n");
            sb.append("  Вес: ").append(o.weight != null ? o.weight : "не указан");

            if (o.length != null) {
                double currentLength = parseDoubleSafe(o.length);
                if (currentLength > maxLength) maxLength = currentLength;
                sb.append("  Длина: ").append(currentLength).append(" м");
            }

            if (o.volume != null) {
                sb.append("  Объем: ").append(o.volume).append(" м³");
            }

            totalWeight += parseDoubleSafe(o.weight);
            totalVolume += parseDoubleSafe(o.volume);

            // ... внутри for (Order o : ordersForDate) после блока объема:
            if (o.unloading != null && !o.unloading.isBlank()) {
                sb.append(" \n  ").append(o.unloading.trim()).append("\n");
            } else {
                sb.append(" \n  —\n");
            }
            sb.append("\n");

        }

        // Считаем уникальные склады-погрузки для этого водителя на эту дату
        int warehousePoints = (int) OrderLoader.orders.stream()
                .filter(o -> o.supplierOrders != null)
                .flatMap(o -> o.supplierOrders.stream())
                .filter(so -> {
                    try {
                        return so.loadingDate != null
                                && !so.loadingDate.isBlank()
                                && LocalDate.parse(so.loadingDate.substring(0, 10)).equals(date) // ISO_LOCAL_DATE_TIME -> yyyy-MM-dd
                                && so.loadingDriver != null
                                && so.loadingDriver.contains(driverName);
                    } catch (Exception e) { return false; }
                })
                .map(so -> so.supplierWarehouse != null ? so.supplierWarehouse.trim() : "")
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet())
                .size();

        int totalPoints = orderCount + warehousePoints;


        sb.append("📊 Итого:\n");
        sb.append("  Количество точек: ").append(totalPoints).append("\n");
        sb.append("  Вес: ").append(String.format("%.2f", totalWeight)).append(" кг\n");
        sb.append("  Объем: ").append(String.format("%.2f", totalVolume)).append(" м³\n");
        sb.append("  Длина (макс.): ").append(String.format("%.2f", maxLength)).append(" м\n");

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        if((!user.getRouteStatus(date).isConfirmed() && user.getRouteStatus(date).isRequested()) || (!user.getRouteStatus(date).isConfirmed() && date.equals(LocalDate.now()))){
            InlineKeyboardButton btnConfirm = new InlineKeyboardButton("✅ Подтвердить")
                    .callbackData("routes:confirm:" + date);
            InlineKeyboardButton btnDecline = new InlineKeyboardButton("❌ Отказаться")
                    .callbackData("routes:decline" + date);

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(btnConfirm);
            row.add(btnDecline);
            keyboard.add(row);
        } else if (user.getRouteStatus(date).isConfirmed()){
            keyboard.add(Collections.singletonList(
                    new InlineKeyboardButton("🚀 Начать маршрут").callbackData("route:start:" + date) // ← дата!
            ));
        }
        keyboard.add(Collections.singletonList(
                new InlineKeyboardButton("⬅️ Назад").callbackData("routes_menu")
        ));

        Main.getInstance().editMessage(chatId, messageId, sb.toString(), keyboard);
    }


    public static void showDriversRoutesList(Update update) {
        long chatId = (update.message() != null)
                ? update.message().chat().id()
                : update.callbackQuery().message().chat().id();

        // ==== целевые даты по требованиям ====
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();

        // воскресенье — выходной: сразу пустой список и сообщение
        if (dow == DayOfWeek.SUNDAY) {
            Main.getInstance().sendMessage(chatId, "🛌 Сегодня воскресенье — выходной. Маршрутов нет.");
            return;
        }

        List<LocalDate> targetDates = new ArrayList<>();
        if (dow == DayOfWeek.FRIDAY) {
            targetDates.add(today.plusDays(1)); // суббота
            targetDates.add(today.plusDays(3)); // понедельник
        } else {
            targetDates.add(today.plusDays(1)); // только завтра
        }

        DateTimeFormatter deliveryFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // ==== предварительно соберём: дата -> водитель(полное имя) -> есть ли заказ ====
        Map<LocalDate, Set<String>> driversWithRoutesByDate = new HashMap<>();
        for (LocalDate d : targetDates) driversWithRoutesByDate.put(d, new HashSet<>());

        OrderLoader.orders.stream()
                .filter(o -> o != null && o.driver != null && o.deliveryDate != null)
                .forEach(o -> {
                    try {
                        LocalDate d = LocalDate.parse(o.deliveryDate.trim(), deliveryFmt);
                        if (driversWithRoutesByDate.containsKey(d)) {
                            driversWithRoutesByDate.get(d).add(o.driver.trim());
                        }
                    } catch (Exception ignored) {}
                });

        // Список всех водителей по заказам (как и было)
        List<String> driverNames = OrderLoader.orders.stream()
                .map(o -> o.driver)
                .filter(name -> name != null && !name.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());

        // Оставляем только тех, у кого есть маршрут хотя бы на одну из целевых дат
        List<String> filteredDrivers = driverNames.stream()
                .filter(fullName -> targetDates.stream()
                        .anyMatch(d -> driversWithRoutesByDate.getOrDefault(d, Set.of()).contains(fullName.trim())))
                .collect(Collectors.toList());

        if (filteredDrivers.isEmpty()) {
            Main.getInstance().sendMessage(chatId, "🚫 На выбранные даты маршрутов у водителей нет.");
            return;
        }

        // ==== строим клавиатуру ====
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        for (String driverFull : filteredDrivers) {
            // короткое имя для поиска UserData
            String[] parts = driverFull.trim().split("\\s+");
            String shortName = parts.length >= 2 ? parts[0] + " " + parts[1] : driverFull.trim();

            UserData user = UserData.findUserByName(shortName);
            if (user == null) continue;

            // выберем первую (по приоритету) целевую дату, на которую у водителя есть маршрут
            Optional<LocalDate> chosenDateOpt = targetDates.stream()
                    .filter(d -> driversWithRoutesByDate.getOrDefault(d, Set.of()).contains(driverFull.trim()))
                    .findFirst();

            if (chosenDateOpt.isEmpty()) continue;  // перестраховка

            LocalDate chosenDate = chosenDateOpt.get();

            String accepted = user.getRouteStatus(chosenDate).isConfirmed() ? " ✅" : "";
            String requested = user.getRouteStatus(chosenDate).isRequested() ? " ⏳" : "";

            long driverID = user.getId();
            InlineKeyboardButton btn = new InlineKeyboardButton(accepted + requested + shortName)
                    .callbackData("routes:driver:" + driverID);
            row.add(btn);

            if (row.size() == 2) {
                keyboard.add(new ArrayList<>(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) keyboard.add(new ArrayList<>(row));

        // если после фильтрации никого не осталось
        if (keyboard.isEmpty()) {
            Main.getInstance().sendMessage(chatId, "🚫 На выбранные даты маршрутов у водителей нет.");
            return;
        }

        // Кнопка "Отправить всем" (используем next workday, в пт это суббота)
        LocalDate sendDate = getNextWorkday();
        InlineKeyboardButton sendAllBtn = new InlineKeyboardButton("📤 Отправить всем маршрут")
                .callbackData("routes:send_all:" + sendDate);
        keyboard.add(Collections.singletonList(sendAllBtn));

        // Отправляем или редактируем сообщение с клавиатурой
        if (update.callbackQuery() != null) {
            Main.getInstance().editMessage(
                    chatId,
                    update.callbackQuery().message().messageId(),
                    "👷 Выберите водителя для просмотра маршрута:",
                    keyboard
            );
        } else {
            Main.getInstance().sendInlineKeyboard(
                    chatId,
                    keyboard,
                    "👷 Выберите водителя для просмотра маршрута:"
            );
        }
    }


    public static void showOrdersForDriver(Update update, String driverName, LocalDate date) {
        long chatId = (update.message() != null)
                ? update.message().chat().id()
                : update.callbackQuery().message().chat().id();
        int messageId = (update.callbackQuery() != null)
                ? update.callbackQuery().message().messageId()
                : 0;
        UserData checker = UserData.findUserById(chatId);
        UserData user = UserData.findUserByName(driverName);
        if (user == null) {
            Main.getInstance().editMessage(chatId, messageId, "⚠️ Водитель \"" + driverName + "\" не найден.");
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        List<Order> allOrders = OrderLoader.orders;
        if (allOrders == null) allOrders = List.of();

        List<Order> ordersForDate = new ArrayList<>();
        for (Order order : allOrders) {
            if (order.deliveryDate == null || order.driver == null) continue;
            try {
                boolean sameDate = LocalDate.parse(order.deliveryDate.trim(), formatter).equals(date);
                boolean sameDriver = order.driver.trim().contains(driverName.trim());
                if (sameDate && sameDriver) {
                    ordersForDate.add(order);
                }
            } catch (Exception ignore) { }
        }

        StringBuilder sb = new StringBuilder("📦 Маршрут водителя " + driverName + " на " + date.format(DATE_FORMATTER) + ":\n\n");

        List<String> warehouses = getWarehousesForDriverDate(driverName, date);
        if (!warehouses.isEmpty()) {
            sb.append("🏭 Точки погрузки:\n");
            for (String w : warehouses) {
                sb.append("• ").append(w).append("\n");
            }
            sb.append("\n");
        }
        sb.append("📬 Доставки:\n\n");


        // Если заказы есть — считаем и показываем
        double totalWeight = 0.0;
        double totalVolume = 0.0;
        int orderCount = 0;
        double maxLength = 0.0;

        for (Order o : ordersForDate) {
            orderCount++;
            sb.append("• ").append(o.deliveryAddress != null ? o.deliveryAddress : "Адрес не указан").append("\n");
            sb.append("  Вес: ").append(o.weight != null ? o.weight : "не указан");

            if (o.length != null) {
                double currentLength = parseDoubleSafe(o.length);
                if (currentLength > maxLength) maxLength = currentLength;
                sb.append("  Длина: ").append(currentLength).append(" м");
            }

            if (o.volume != null) {
                sb.append("  Объем: ").append(o.volume).append(" м³");
            }

            totalWeight += parseDoubleSafe(o.weight);
            totalVolume += parseDoubleSafe(o.volume);

            if (o.unloading != null && !o.unloading.isBlank()) {
                sb.append(" \n  ").append(o.unloading.trim()).append("\n");
            } else {
                sb.append(" \n  —\n");
            }
            sb.append("\n");

        }

        int warehousePoints = (int) OrderLoader.orders.stream()
                .filter(o -> o.supplierOrders != null)
                .flatMap(o -> o.supplierOrders.stream())
                .filter(so -> {
                    try {
                        return so.loadingDate != null
                                && !so.loadingDate.isBlank()
                                && LocalDate.parse(so.loadingDate.substring(0, 10)).equals(date)
                                && so.loadingDriver != null
                                && so.loadingDriver.contains(driverName);
                    } catch (Exception e) { return false; }
                })
                .map(so -> so.supplierWarehouse != null ? so.supplierWarehouse.trim() : "")
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet())
                .size();

        int totalPoints = orderCount + warehousePoints;

        sb.append("📊 Итого:\n");
        sb.append("  Количество точек: ").append(totalPoints).append("\n");
        sb.append("  Вес: ").append(String.format("%.2f", totalWeight)).append(" кг\n");
        sb.append("  Объем: ").append(String.format("%.2f", totalVolume)).append(" м³\n");
        sb.append("  Длина (макс.): ").append(String.format("%.2f", maxLength)).append(" м\n");


        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Кнопки для самого водителя — но если это логист/админ, не показываем
        if (checker.getRole().equalsIgnoreCase("DRIVER")) {
            if ((!user.getRouteStatus(date).isConfirmed() && user.getRouteStatus(date).isRequested())
                    || (!user.getRouteStatus(date).isConfirmed() && date.equals(LocalDate.now()))) {
                InlineKeyboardButton btnConfirm = new InlineKeyboardButton("✅ Подтвердить")
                        .callbackData("routes:confirm:" + date);
                InlineKeyboardButton btnDecline = new InlineKeyboardButton("❌ Отказаться")
                        .callbackData("routes:decline:" + date);

                keyboard.add(List.of(btnConfirm, btnDecline));
            } else if (user.getRouteStatus(date).isConfirmed()) {
                keyboard.add(Collections.singletonList(
                        new InlineKeyboardButton("🚀 Начать маршрут").callbackData("route:start:" + date)
                ));
            }
        }

        keyboard.add(Collections.singletonList(
                new InlineKeyboardButton("⬅️ Назад").callbackData("driver:" + user.getId())
        ));

        Main.getInstance().editMessage(chatId, messageId, sb.toString(), keyboard);
    }


    public static void showDriverRoutes(String driverName, Update update) {
        long chatId = (update.message() != null)
                ? update.message().chat().id()
                : update.callbackQuery().message().chat().id();

        UserData user = UserData.findUserByName(driverName);
        if (user == null) {
            Main.getInstance().sendMessage(chatId, "⚠️ Водитель \"" + driverName + "\" не найден.");
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        user.getRoutes().forEach((date, routeStatus) -> {
            String statusIcon = "";
            if (routeStatus.isFinished()) {
                statusIcon = "\uD83C\uDFC1";
            }
            else if (routeStatus.isStarted()) {
                statusIcon = "\uD83D\uDE9B";
            }
            else if (routeStatus.isRequested() && !routeStatus.isConfirmed()) {
                statusIcon = "⏳";
            } else if (routeStatus.isConfirmed()) {
                statusIcon = "✅";
            }

            String text = date + " " + statusIcon;
            UserData driver = UserData.findUserByName(driverName);
            InlineKeyboardButton btn = new InlineKeyboardButton(text)
                    .callbackData("gr:" + driver.getId() + ":" + date);
            keyboard.add(Collections.singletonList(btn));
        });

        String messageText = "📋 Маршруты водителя: " + driverName;
        if (keyboard.isEmpty()) {
            messageText = "📋 У водителя " + driverName + " нет маршрутов!";
        }

        InlineKeyboardButton btn = new InlineKeyboardButton("◀\uFE0F Назад")
                .callbackData("get_routes_back");
        keyboard.add(Collections.singletonList(btn));

        if (update.callbackQuery() != null) {
            Main.getInstance().editMessage(
                    chatId,
                    update.callbackQuery().message().messageId(),
                    messageText,
                    keyboard
            );
        } else {
            Main.getInstance().sendInlineKeyboard(chatId, keyboard, messageText);
        }
    }

    public static void showOrdersMenu(Update update) {
        long chatId = update.message() != null
                ? update.message().chat().id()
                : update.callbackQuery().message().chat().id();

        long userId = update.message() != null
                ? update.message().from().id()
                : update.callbackQuery().from().id();

        UserData user = UserData.findUserById(userId);
        if (user == null) {
            Main.getInstance().sendMessage(chatId, "⚠️ Пользователь не найден.");
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // перебираем все маршруты пользователя
        user.getRoutes().forEach((date, routeStatus) -> {
            String statusIcon = "";
            if (routeStatus.isRequested() && !routeStatus.isConfirmed()) {
                statusIcon = "⏳";
            }

            String text = date + " " + statusIcon;
            if (!user.getRouteStatus(date).isFinished()) {
                InlineKeyboardButton btn = new InlineKeyboardButton(text)
                        .callbackData("routes:" + date);
                keyboard.add(Collections.singletonList(btn));
            }
        });

        String messageText = "📋 Ваши маршруты:";
        if (keyboard.isEmpty()) {
            messageText = "📋 У вас нет маршрутов!";
        }

        if (update.callbackQuery() != null) {
            Main.getInstance().editMessage(
                    chatId,
                    update.callbackQuery().message().messageId(),
                    messageText,
                    keyboard
            );
        } else {
            Main.getInstance().sendInlineKeyboard(chatId, keyboard, messageText);
        }
    }

    public static void showDriverRoute(Update update, String driverName) {
        long chatId = (update.message() != null) ? update.message().chat().id() : update.callbackQuery().message().chat().id();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate today = LocalDate.now();

        // Даты для маршрута
        List<LocalDate> targetDates = new ArrayList<>();
        if (today.getDayOfWeek() == DayOfWeek.FRIDAY) {
            targetDates.add(today.plusDays(1)); // суббота
            targetDates.add(today.plusDays(3)); // понедельник
        } else {
            targetDates.add(getNextWorkday()); // обычный случай (с учётом воскресенья)
        }

        List<Order> allOrders = OrderLoader.orders;
        if (allOrders == null || allOrders.isEmpty()) {
            Main.getInstance().sendMessage(chatId, "📦 Нет маршрута.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        UserData driver = UserData.findUserByName(driverName);
        boolean hasRoutesToSend = false;

        for (LocalDate targetDate : targetDates) {
            List<Order> dayOrders = allOrders.stream()
                    .filter(o -> {
                        if (o.deliveryDate == null || o.driver == null) return false;
                        try {
                            return LocalDate.parse(o.deliveryDate.trim(), formatter).equals(targetDate)
                                    && o.driver.trim().contains(driverName.trim());
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            if (dayOrders.isEmpty()) {
                sb.append("📦 У водителя ").append(driverName).append(" нет маршрута на ")
                        .append(targetDate.format(DATE_FORMATTER)).append(".\n\n");
                continue;
            }

            sb.append("📦 Маршрут ").append(driverName).append(" на ").append(targetDate.format(DATE_FORMATTER)).append(":\n\n");
            List<String> warehouses = getWarehousesForDriverDate(driverName, targetDate);
            if (!warehouses.isEmpty()) {
                sb.append("🏭 Точки погрузки:\n");
                for (String w : warehouses) {
                    sb.append("• ").append(w).append("\n");
                }
                sb.append("\n");
            }
            sb.append("📬 Доставки:\n\n");

            double totalWeight = 0.0;
            double totalVolume = 0.0;
            int orderCount = 0;
            double maxLength = 0.0;

            for (Order o : dayOrders) {
                orderCount++;
                sb.append("• ").append(o.deliveryAddress != null ? o.deliveryAddress : "Адрес не указан").append("\n");
                sb.append("  Вес: ").append(o.weight != null ? o.weight : "не указан");

                if (o.length != null) {
                    double currentLength = parseDoubleSafe(o.length);
                    if (currentLength > maxLength) {
                        maxLength = currentLength;
                    }
                    sb.append("  Габариты: ").append(currentLength).append(" м");
                }

                if (o.volume != null) {
                    sb.append("  Объем: ").append(o.volume).append(" м³");
                }


                totalWeight += parseDoubleSafe(o.weight);
                totalVolume += parseDoubleSafe(o.volume);
                if (o.unloading != null && !o.unloading.isBlank()) {
                    sb.append(" \n  ").append(o.unloading.trim()).append("\n");
                } else {
                    sb.append(" \n  —\n");
                }
                sb.append("\n");

            }

            int warehousePoints = (int) OrderLoader.orders.stream()
                    .filter(o -> o.supplierOrders != null)
                    .flatMap(o -> o.supplierOrders.stream())
                    .filter(so -> {
                        try {
                            return so.loadingDate != null
                                    && !so.loadingDate.isBlank()
                                    && LocalDate.parse(so.loadingDate.substring(0, 10)).equals(targetDate)
                                    && so.loadingDriver != null
                                    && so.loadingDriver.contains(driverName);
                        } catch (Exception e) { return false; }
                    })
                    .map(so -> so.supplierWarehouse != null ? so.supplierWarehouse.trim() : "")
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet())
                    .size();

            int totalPoints = orderCount + warehousePoints;

            sb.append("📊 Итого:\n");
            sb.append("  Количество точек: ").append(totalPoints).append("\n");
            sb.append("  Вес: ").append(String.format("%.2f", totalWeight)).append(" кг\n");
            sb.append("  Объем: ").append(String.format("%.2f", totalVolume)).append(" м³\n");
            sb.append("  Длина (макс.): ").append(String.format("%.2f", maxLength)).append(" м\n");


            // хотя бы один маршрут доступен для отправки
            if (!driver.getRouteStatus(targetDate).isRequested() && !driver.getRouteStatus(targetDate).isConfirmed()) {
                hasRoutesToSend = true;
            }
        }

        // Кнопка "Отправить маршрут" — одна на все даты
        if (hasRoutesToSend) {
            InlineKeyboardButton btnSendRoute = new InlineKeyboardButton("📤 Отправить маршрут")
                    .callbackData("routes:send:" + getNextWorkday() + ":" + driver.getId());
            keyboard.add(Collections.singletonList(btnSendRoute));
        }

        // Кнопка "Назад"
        InlineKeyboardButton btnBack = new InlineKeyboardButton("⬅️ Назад").callbackData("routes:list");
        keyboard.add(Collections.singletonList(btnBack));

        if (update.callbackQuery() != null) {
            Main.getInstance().editMessage(chatId,
                    update.callbackQuery().message().messageId(),
                    sb.toString(),
                    keyboard);
        } else {
            Main.getInstance().sendInlineKeyboard(chatId, keyboard, sb.toString());
        }
    }


    public static void sendRouteConfirmationToDriver(UserData driver, LocalDate date) {
        if (driver == null || driver.getId() == null) return;

        Long chatId = driver.getId();
        String driverName = driver.getName();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        List<Order> allOrders = OrderLoader.orders;
        if (allOrders == null || allOrders.isEmpty()) {
            Main.getInstance().sendMessage(chatId, "📦 Нет маршрута.");
            return;
        }

        // Определяем даты для маршрутов
        List<LocalDate> targetDates = new ArrayList<>();
        if (date.getDayOfWeek() == DayOfWeek.FRIDAY) {
            targetDates.add(date.plusDays(1)); // суббота
            targetDates.add(date.plusDays(3)); // понедельник
        } else {
            targetDates.add(date); // обычный случай
        }

        StringBuilder sb = new StringBuilder();
        boolean hasOrders = false;

        for (LocalDate targetDate : targetDates) {
            List<Order> dayOrders = allOrders.stream()
                    .filter(o -> {
                        if (o.deliveryDate == null || o.driver == null) return false;
                        try {
                            return LocalDate.parse(o.deliveryDate.trim(), formatter).equals(targetDate)
                                    && o.driver.trim().contains(driverName.trim());
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            if (dayOrders.isEmpty()) {
                sb.append("📦 У вас нет маршрута на ").append(targetDate.format(formatter)).append(".\n\n");
                continue;
            }

            hasOrders = true; // есть хотя бы один маршрут

            sb.append("📦 Ваш маршрут на ").append(targetDate.format(formatter)).append(":\n\n");
            List<String> warehouses = getWarehousesForDriverDate(driverName, targetDate);
            if (!warehouses.isEmpty()) {
                sb.append("🏭 Точки погрузки:\n");
                for (String w : warehouses) {
                    sb.append("• ").append(w).append("\n");
                }
                sb.append("\n");
            }
            sb.append("📬 Доставки:\n\n");

            double totalWeight = 0.0;
            double totalVolume = 0.0;
            int orderCount = 0;
            double maxLength = 0.0;

            for (Order o : dayOrders) {
                orderCount++;
                sb.append("• ").append(o.deliveryAddress != null ? o.deliveryAddress : "Адрес не указан").append("\n");
                sb.append("  Вес: ").append(o.weight != null ? o.weight : "не указан");

                if (o.length != null) {
                    double currentLength = parseDoubleSafe(o.length);
                    if (currentLength > maxLength) {
                        maxLength = currentLength;
                    }
                    sb.append("  Габариты: ").append(currentLength).append(" м");
                }

                if (o.volume != null) {
                    sb.append("  Объем: ").append(o.volume).append(" м³");
                }


                totalWeight += parseDoubleSafe(o.weight);
                totalVolume += parseDoubleSafe(o.volume);
                if (o.unloading != null && !o.unloading.isBlank()) {
                    sb.append(" \n  ").append(o.unloading.trim()).append("\n");
                } else {
                    sb.append(" \n  —\n");
                }
                sb.append("\n");

            }

            int warehousePoints = (int) OrderLoader.orders.stream()
                    .filter(o -> o.supplierOrders != null)
                    .flatMap(o -> o.supplierOrders.stream())
                    .filter(so -> {
                        try {
                            return so.loadingDate != null
                                    && !so.loadingDate.isBlank()
                                    && LocalDate.parse(so.loadingDate.substring(0, 10)).equals(targetDate)
                                    && so.loadingDriver != null
                                    && so.loadingDriver.contains(driverName);
                        } catch (Exception e) { return false; }
                    })
                    .map(so -> so.supplierWarehouse != null ? so.supplierWarehouse.trim() : "")
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet())
                    .size();

            int totalPoints = orderCount + warehousePoints;

            sb.append("📊 Итого:\n");
            sb.append("  Количество точек: ").append(totalPoints).append("\n");
            sb.append("  Вес: ").append(String.format("%.2f", totalWeight)).append(" кг\n");
            sb.append("  Объем: ").append(String.format("%.2f", totalVolume)).append(" м³\n");
            sb.append("  Длина (макс.): ").append(String.format("%.2f", maxLength)).append(" м\n");

        }

        sb.append("⏳ У вас есть 1 час, чтобы принять или отклонить маршрут.\n")
                .append("Если за это время вы не ответите — маршрут будет принят автоматически.\n\n");

        // Формируем клавиатуру с одной парой кнопок на все даты
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        if (hasOrders) {
            InlineKeyboardButton btnConfirm = new InlineKeyboardButton("✅ Подтвердить")
                    .callbackData("routes:confirm:" + date);
            InlineKeyboardButton btnDecline = new InlineKeyboardButton("❌ Отказаться")
                    .callbackData("routes:decline:" + date);
            keyboard.add(Arrays.asList(btnConfirm, btnDecline));
        }

        Main.getInstance().sendInlineKeyboard(chatId, keyboard, sb.toString());
    }



    public static void notifyDriverIfRouteChanged(UserData driver, LocalDate date) {
        if (driver == null || driver.getId() == null) return;

        List<Order> allOrders = OrderLoader.orders != null ? OrderLoader.orders : List.of();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // заказы для этого водителя на эту дату
        List<Order> currentOrders = allOrders.stream()
                .filter(o -> {
                    if (o.deliveryDate == null || o.driver == null) return false;
                    try {
                        return LocalDate.parse(o.deliveryDate.trim(), formatter).equals(date)
                                && o.driver.trim().contains(driver.getName().trim());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        // Сохраняем предыдущий список заказов у водителя (только номера)
        List<String> prevOrderNumbers = driver.getRouteStatus(date).getOrdersSnapshot();
        List<String> currentOrderNumbers = currentOrders.stream()
                .map(o -> o.orderNumber != null ? o.orderNumber.trim() : "Неизвестно")
                .collect(Collectors.toList());

        // Если первый раз сохраняем — просто запоминаем номера
        if (prevOrderNumbers == null) {
            driver.getRouteStatus(date).setOrdersSnapshot(currentOrderNumbers);
            return;
        }

        // Сравниваем по номерам заказов
        List<String> addedNumbers = new ArrayList<>(currentOrderNumbers);
        addedNumbers.removeAll(prevOrderNumbers);

        List<String> removedNumbers = new ArrayList<>(prevOrderNumbers);
        removedNumbers.removeAll(currentOrderNumbers);

        if (!addedNumbers.isEmpty() || !removedNumbers.isEmpty()) {
            StringBuilder sb = new StringBuilder("⚠️ В ваш маршрут на ")
                    .append(date.format(formatter))
                    .append(" внесены изменения:\n\n");

            if (!addedNumbers.isEmpty()) {
                sb.append("➕ Добавлены заказы:\n");
                for (String num : addedNumbers) {
                    String address = currentOrders.stream()
                            .filter(o -> num.equals(o.orderNumber))
                            .map(o -> o.deliveryAddress != null ? o.deliveryAddress : "Адрес не указан")
                            .findFirst().orElse("Адрес не указан");
                    sb.append("• №").append(num).append(" — ").append(address).append("\n");
                }
                sb.append("\n");
            }

            if (!removedNumbers.isEmpty()) {
                sb.append("➖ Убраны заказы:\n");
                for (String num : removedNumbers) {
                    String address = currentOrders.stream()
                            .filter(o -> num.equals(o.orderNumber))
                            .map(o -> o.deliveryAddress != null ? o.deliveryAddress : "Адрес не указан")
                            .findFirst().orElse("Адрес не указан");
                    sb.append("• №").append(num).append(" — ").append(address).append("\n");
                }
                sb.append("\n");
            }

            Main.getInstance().sendMessage(driver.getId(), sb.toString());
        }

        // Обновляем snapshot номеров заказов
        driver.getRouteStatus(date).setOrdersSnapshot(currentOrderNumbers);
    }

    public static void handleRouteCallback(Update update) {
        if (update.callbackQuery() == null) return;

        String data = update.callbackQuery().data();
        Long userId = update.callbackQuery().from().id();
        Long chatId = update.callbackQuery().message().chat().id();
        int messageId = update.callbackQuery().message().messageId();

        UserData user = UserData.findUserById(userId);
        if (user == null) {
            Main.getInstance().sendMessage(chatId, "⚠️ Пользователь не найден.");
            return;
        }
        LocalDate date = null;
        String[] parts = data.split(":");
        if (parts.length > 2) {
            try { date = LocalDate.parse(parts[2]); } catch (Exception ignore) {}
        }
        if (data.contains("routes_menu:")) {
                if(!user.getRole().equalsIgnoreCase("DRIVER")){
                    long driverID = Long.parseLong(parts[1]);
                    UserData driver = UserData.findUserById(driverID);
                    Routes.showDriverRoutes(driver.getName(), update);
                   return;
                }
            showOrdersMenu(update);
            return;
        } if (data.contains("routes:confirm")) {

            // Сообщения выдаём только если маршрут ещё НЕ подтверждён
            if (user.getRouteStatus(date).isConfirmed()) {
                Main.getInstance().editMessage(chatId, messageId, "⏰ Время на ответ истекло.");
                return;
            }

            // Сообщение пользователю
            Main.getInstance().editMessage(chatId, messageId, "✅ Маршрут подтверждён. Спасибо!");

            // Снимок адресов на момент подтверждения
            user.getRouteStatus(date).setOrdersSnapshot(
                    OrderLoader.orders.stream()
                            .map(o -> o.deliveryAddress != null ? o.deliveryAddress : "Адрес не указан")
                            .toList()
            );

            assert date != null;
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
                // Обновляем субботу
                {
                    UserData.RouteStatus st = user.getRouteStatus(date);
                    boolean changed = !st.isConfirmed() || st.isRequested();
                    if (changed) {
                        st.setConfirmed(true);
                        st.setRequested(false);
                        notifyAdminsAndLogistics(user, true, false, date);
                    }
                }
                // И понедельник
                {
                    LocalDate d2 = date.plusDays(2);
                    UserData.RouteStatus st2 = user.getRouteStatus(d2);
                    boolean changed2 = !st2.isConfirmed() || st2.isRequested();
                    if (changed2) {
                        st2.setConfirmed(true);
                        st2.setRequested(false);
                        notifyAdminsAndLogistics(user, true, false, d2);
                    }
                }
            } else {
                // Обычный день
                UserData.RouteStatus st = user.getRouteStatus(date);
                boolean changed = !st.isConfirmed() || st.isRequested();
                if (changed) {
                    st.setConfirmed(true);
                    st.setRequested(false);
                    notifyAdminsAndLogistics(user, true, false, date);
                }
            }
            return;

        } else if (data.contains("routes:decline")) {

            // Сообщения выдаём только если маршрут ещё НЕ подтверждён
            if (user.getRouteStatus(date).isConfirmed()) {
                Main.getInstance().editMessage(chatId, messageId, "⏰ Время на ответ истекло.");
                return;
            }

            // Сообщение пользователю
            Main.getInstance().editMessage(chatId, messageId, "❌ Вы отказались от маршрута.");

            assert date != null;
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
                // Обновляем субботу
                {
                    UserData.RouteStatus st = user.getRouteStatus(date);
                    boolean changed = st.isConfirmed() || st.isRequested();
                    if (changed) {
                        st.setConfirmed(false);
                        st.setRequested(false);
                        notifyAdminsAndLogistics(user, false, false, date);
                    }
                }
                // И понедельник
                {
                    LocalDate d2 = date.plusDays(2);
                    UserData.RouteStatus st2 = user.getRouteStatus(d2);
                    boolean changed2 = st2.isConfirmed() || st2.isRequested();
                    if (changed2) {
                        st2.setConfirmed(false);
                        st2.setRequested(false);
                        notifyAdminsAndLogistics(user, false, false, d2);
                    }
                }
            } else {
                // Обычный день
                UserData.RouteStatus st = user.getRouteStatus(date);
                boolean changed = st.isConfirmed() || st.isRequested();
                if (changed) {
                    st.setConfirmed(false);
                    st.setRequested(false);
                    notifyAdminsAndLogistics(user, false, false, date);
                }
            }
            return;

    } else if (data.equals("routes:list") || data.equals("routes:back")) {
            showDriversRoutesList(update);
            return;
        } else if (data.startsWith("routes:driver:")) {
            long driverId = Long.parseLong(data.substring("routes:driver:".length()));
            UserData driver = UserData.findUserById(driverId);
            showDriverRoute(update, driver.getName());
            return;
        } else if (data.startsWith("routes:send:")) {
            long driverId = Long.parseLong(data.substring(("routes:send:" + date + ":").length()));
            UserData driver = UserData.findUserById(driverId);
            if (driver != null) {
                sendRouteConfirmationToDriver(driver, date);
                Main.getInstance().sendMessage(chatId, "📤 Маршрут отправлен водителю " + driver.getName());
                notifyAdminsAndLogisticsSending(driver, user, date);
                if(date.getDayOfWeek() == DayOfWeek.SATURDAY){
                    driver.getRouteStatus(date).setRequested(true);
                    driver.getRouteStatus(date.plusDays(2)).setRequested(true);
                } else{
                    driver.getRouteStatus(date).setRequested(true);
                }

                RouteScheduler.scheduleRouteAutoAccept(driver, date, 60);
            } else {
                Main.getInstance().sendMessage(chatId, "⚠️ Водитель не найден.");
            }
            return;
        }
        else if (data.startsWith("routes:send_all:")) {
            // routes:send_all:YYYY-MM-DD
            LocalDate sendDateAll = LocalDate.parse(data.substring("routes:send_all:".length()));

            // Находим всех водителей, у кого есть маршрут на sendDateAll
            DateTimeFormatter deliveryFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            Set<String> driversWithRoutes = OrderLoader.orders.stream()
                    .filter(o -> o != null && o.driver != null && o.deliveryDate != null)
                    .filter(o -> {
                        try { return LocalDate.parse(o.deliveryDate.trim(), deliveryFmt).equals(sendDateAll); }
                        catch (Exception e) { return false; }
                    })
                    .map(o -> o.driver.trim())
                    .collect(Collectors.toCollection(LinkedHashSet::new)); // сохраняем порядок

            // Преобразуем в UserData (через короткое имя "Имя Фамилия", как в остальном коде)
            List<UserData> drivers = driversWithRoutes.stream()
                    .map(full -> {
                        String[] p = full.split("\\s+");
                        String shortName = (p.length >= 2) ? (p[0] + " " + p[1]) : full;
                        return UserData.findUserByName(shortName);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (drivers.isEmpty()) {
                Main.getInstance().sendMessage(chatId, "🚫 На указанную дату нет водителей с маршрутами.");
                return;
            }

            int sent = 0;
            for (UserData drv : drivers) {
                // 🔒 Не отправляем, если уже запрошено или подтверждено на ЭТУ дату
                var rs = drv.getRouteStatus(sendDateAll);
                if (rs != null && (rs.isRequested() || rs.isConfirmed())) {
                    continue; // пропускаем этого водителя
                }

                // Отправляем
                sendRouteConfirmationToDriver(drv, sendDateAll);
                notifyAdminsAndLogisticsSending(drv, user, sendDateAll);

                // Помечаем как "запрошен" (как и при одиночной отправке)
                if (sendDateAll.getDayOfWeek() == DayOfWeek.SATURDAY) {
                    drv.getRouteStatus(sendDateAll).setRequested(true);
                    drv.getRouteStatus(sendDateAll.plusDays(2)).setRequested(true); // понедельник
                } else {
                    drv.getRouteStatus(sendDateAll).setRequested(true);
                }

                // Планируем авто-принятие
                RouteScheduler.scheduleRouteAutoAccept(drv, sendDateAll, 60);
                sent++;
            }

            if (sent == 0) {
                Main.getInstance().sendMessage(chatId, "ℹ️ Никому не отправлено: у всех на эту дату уже стоит запрос или подтверждение.");
            } else {
                Main.getInstance().sendMessage(chatId, "📤 Маршруты отправлены всем (" + sent + ").");
            }
            return;
        }

        // ===== обработка выбора даты: строго routes:YYYY-MM-DD =====
        else if (data.matches("^routes:\\d{4}-\\d{2}-\\d{2}$")) {
            LocalDate dateToCheck = LocalDate.parse(data.substring("routes:".length()));
            var rs = user.getRouteStatus(dateToCheck);

            if (rs != null && rs.isFinished()) {
                Main.getInstance().editMessage(chatId, messageId,
                        "🏁 Маршрут за " + dateToCheck.format(DATE_FORMATTER) + " уже завершён.");
                return;
            }

            if (rs != null && rs.isStarted()) {
                if (OrderLoader.orders.isEmpty()) {
                    Main.getInstance().editMessage(chatId, messageId, "Нет доступных заказов.");
                    return;
                }
                List<List<InlineKeyboardButton>> buttonsInline =
                        OrderLoader.buildOrderButtons(OrderLoader.orders, user.getName(), dateToCheck);
                if (buttonsInline.isEmpty()) {
                    Main.getInstance().editMessage(chatId, messageId, "Нет доступных заказов.");
                    return;
                }
                Main.getInstance().editMessage(chatId, messageId, "Выберите заказ:", buttonsInline);
                return;
            }

            // Не начат или только запрошен — показываем экран маршрута на дату
            showOrdersForDriver(update, dateToCheck);
            return;
        }

        // ===== старт маршрута с датой =====
        if (data.startsWith("route:start")) {

            // Проверка незавершённого предыдущего рабочего дня для ЭТОЙ логики (можно оставить или убрать)
            if (user.getRoutes().containsKey(getPreviousWorkday())) {
                if (!user.getRouteStatus(getPreviousWorkday()).isFinished()) {
                    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                    keyboard.add(Collections.singletonList(
                            new InlineKeyboardButton("⬅️ Назад").callbackData("rm")
                    ));
                    Main.getInstance().editMessage(chatId, messageId,
                            "⚠️ У вас есть незавершённый маршрут за " + getPreviousWorkday().format(DATE_FORMATTER) + ".", keyboard);
                    return;
                }
            }

            user.getRouteStatus(date).setStarted(true);
            notifyAdminsAndLogisticsStartedOrFinished(user, true, date);

            if (OrderLoader.orders.isEmpty()) {
                Main.getInstance().editMessage(chatId, messageId, "Нет доступных заказов.");
                return;
            }

            List<List<InlineKeyboardButton>> buttonsInline =
                    OrderLoader.buildOrderButtons(OrderLoader.orders, user.getName(), date);

            if (buttonsInline.isEmpty()) {
                Main.getInstance().editMessage(chatId, messageId, "Нет доступных заказов.");
                return;
            }

            Main.getInstance().editMessage(chatId, messageId, "Выберите заказ:", buttonsInline);
            return;
        }

        if (data.startsWith("route:finish")) {

            if (OrderLoader.hasDriverProblemOrders(OrderLoader.orders, user.getName(), date)) {
                Main.getInstance().sendMessage(chatId,
                        "⚠️ У вас есть незавершённые заказы. Пожалуйста, проставьте статусы:\n" +
                                OrderLoader.getDriverProblemOrderNumbers(OrderLoader.orders, user.getName(), date));
                return;
            }
            user.getRouteStatus(date).setFinished(true);
            Main.getInstance().editMessage(chatId, messageId,
                    "Вы успешно завершили маршрут за " + date.format(DATE_FORMATTER) + "!");
            notifyAdminsAndLogisticsStartedOrFinished(user, false, date);
        }
    }


    public static void notifyAdminsAndLogistics(UserData driver, boolean confirmed, boolean autoAccepted, LocalDate date) {
        String status;

        if (autoAccepted) {
            status = "🤖 автоматически принял маршрут";
        } else {
            status = confirmed ? "✅ подтвердил маршрут" : "❌ отказался от маршрута";
        }

        String message = String.format("Водитель %s %s на %s.", driver.getName(), status, date);

        for (UserData u : Main.users) {
            if (u.getRole() != null) {
                String role = u.getRole().toUpperCase();
                if (role.equals("ADMIN") || role.equals("LOGISTIC")) {
                    Main.getInstance().sendMessage(u.getId(), message);
                }
            }
        }
    }

    public static void notifyAdminsAndLogisticsStartedOrFinished(UserData driver, boolean started, LocalDate date) {
        String action = started ? "начал" : "завершил";
        String message = String.format("Водитель %s %s маршрут за %s.", driver.getName(), action, date);

        for (UserData u : Main.users) {
            if (u.getRole() != null) {
                String role = u.getRole().toUpperCase();
                if (role.equals("ADMIN") || role.equals("LOGISTIC")) {
                    Main.getInstance().sendMessage(u.getId(), message);
                }
            }
        }
    }

    public static void notifyAdminsAndLogisticsSending(UserData driver, UserData logistic, LocalDate date) {
        String message = String.format("%s отправил водителю %s маршрут за %s.", logistic.getName(), driver.getName(), date);

        for (UserData u : Main.users) {
            if (u.getRole() != null && !u.getName().equals(logistic.getName())) {
                String role = u.getRole().toUpperCase();
                if (role.equals("ADMIN") || role.equals("LOGISTIC")) {
                    Main.getInstance().sendMessage(u.getId(), message);
                }
            }
        }
    }

    public static LocalDate getNextWorkday() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Если завтра воскресенье, возвращаем понедельник
        if (tomorrow.getDayOfWeek() == DayOfWeek.SUNDAY) {
            tomorrow = tomorrow.plusDays(1);
        }

        return tomorrow;
    }

    public static LocalDate getPreviousWorkday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Если вчера было воскресенье, возвращаем субботу
        if (yesterday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            yesterday = yesterday.minusDays(1);
        }

        return yesterday;
    }

    private static double parseDoubleSafe(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        s = s.replace("м3", "");
        // Заменяем запятую на точку и оставляем только цифры и одну точку
        s = s.replace(',', '.').replaceAll("[^0-9.]", "");

        // Если пусто — возвращаем 0
        if (s.isEmpty()) return 0.0;

        // Оставляем только первую точку
        int firstDot = s.indexOf('.');
        if (firstDot >= 0) {
            String beforeDot = s.substring(0, firstDot + 1);
            String afterDot = s.substring(firstDot + 1).replace(".", ""); // удаляем все последующие точки
            s = beforeDot + afterDot;
        }

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ВНИЗУ класса Routes (рядом с parseDoubleSafe)
    private static List<String> getWarehousesForDriverDate(String driverName, LocalDate date) {
        if (OrderLoader.orders == null) return List.of();

        // собираем уникальные (с сохранением порядка) подписи складов, где этот водитель — loadingDriver в указанную дату
        // подпись склада: "<Поставщик> — <Склад>" если оба есть, иначе что есть
        var set = new java.util.LinkedHashSet<String>();

        OrderLoader.orders.stream()
                .filter(o -> o.supplierOrders != null)
                .flatMap(o -> o.supplierOrders.stream())
                .filter(so -> {
                    try {
                        return so.loadingDate != null
                                && !so.loadingDate.isBlank()
                                && LocalDate.parse(so.loadingDate.substring(0, 10)).equals(date)
                                && so.loadingDriver != null
                                && so.loadingDriver.contains(driverName);
                    } catch (Exception e) { return false; }
                })
                .forEach(so -> {
                    String supplier = (so.supplier != null && !so.supplier.isBlank()) ? so.supplier.trim() : "";
                    String wh = (so.supplierWarehouse != null && !so.supplierWarehouse.isBlank()) ? so.supplierWarehouse.trim() : "";
                    String label;
                    if (!supplier.isEmpty() && !wh.isEmpty())      label = supplier + " — " + wh;
                    else if (!supplier.isEmpty())                  label = supplier;
                    else                                           label = wh.isEmpty() ? "Склад (адрес не указан)" : wh;
                    set.add(label);
                });

        return new java.util.ArrayList<>(set);
    }


}
