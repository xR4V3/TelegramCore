package modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import core.Main;
import utils.Order;
import utils.OrderStatus;
import utils.UserData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


import static utils.OrderStatus.getEmojiByStatus;

public class OrderLoader {

    public static List<Order> orders;

    public static void load() {
        try {
            long start = System.nanoTime();
            orders = loadOrders("orders", false);
            long end = System.nanoTime();

            int count = orders != null ? orders.size() : 0;
            double durationSec = (end - start) / 1_000_000_000.0;
            System.out.printf("Загружено заказов: %d за %.3f сек%n", count, durationSec);

            if(Main.users != null) {
                if (orders != null && !orders.isEmpty()) {
                    for (UserData user : Main.users) {
                        try {
                            if (user != null && user.getRole() != null && user.getRole().equalsIgnoreCase("driver")) {
                                for (LocalDate date : List.of(LocalDate.now(), LocalDate.now().plusDays(1))) {
                                    Routes.notifyDriverIfRouteChanged(user, date);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Ошибка при уведомлении водителя " + user.getName() + ": " + e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при загрузке заказов: " + e);
            e.printStackTrace();
        }

        updateDriverRoutes(orders, Main.users);

    }

    public static Order loadSingleOrder(String folderPath, String orderNumber) {
        ObjectMapper mapper = new ObjectMapper();

        // Дополняем номер до 11 символов нулями слева, если нужно
        String paddedOrderNumber = String.format("%011d", Long.parseLong(orderNumber));

        Path filePath = Paths.get(folderPath, paddedOrderNumber + ".json");

        if (!Files.exists(filePath)) {
            System.err.println("Файл заказа не найден: " + filePath.getFileName());
            return null;
        }

        try {
            return mapper.readValue(filePath.toFile(), Order.class);
        } catch (Exception e) {
            System.err.println("Ошибка при чтении заказа " + paddedOrderNumber + ": " + e.getMessage());
            return null;
        }
    }

    public static List<Order> loadOrders(String folderPath, boolean loadAll) throws IOException {
        List<Order> orderList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        Path dir = Paths.get(folderPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IOException("Папка не найдена: " + folderPath);
        }


        LocalDate today = adjustSunday(LocalDate.now());

        LocalDate yesterday = adjustSunday(today.minusDays(1));
        LocalDate dayBeforeYesterday = adjustSunday(yesterday.minusDays(1));

        LocalDate tomorrow = adjustSunday(today.plusDays(1));
        LocalDate dayAfterTomorrow = adjustSunday(tomorrow.plusDays(1));

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try {
                    Order order = mapper.readValue(file.toFile(), Order.class);

                    if (!loadAll) {
                        String dateStr = order.deliveryDate; // замените на актуальное поле
                        LocalDate orderDate = parseDateSafely(dateStr);

                        if (orderDate == null ||
                                (!orderDate.equals(today) &&
                                        !orderDate.equals(yesterday) &&
                                        !orderDate.equals(dayBeforeYesterday) &&
                                        !orderDate.equals(tomorrow) &&
                                        !orderDate.equals(dayAfterTomorrow))) {
                            continue;
                        }
                    }

                    orderList.add(order);
                } catch (Exception e) {
                    System.err.println("Ошибка при чтении файла " + file.getFileName() + ": " + e.getMessage());
                }
            }
        }

        return orderList;
    }

    public static void updateDriverRoutes(List<Order> orders, List<UserData> drivers) {
        if (orders == null || orders.isEmpty() || drivers == null || drivers.isEmpty()) return;

        LocalDate today = adjustSunday(LocalDate.now());
        LocalDate yesterday = adjustSunday(today.minusDays(1));
        LocalDate dayBeforeYesterday = adjustSunday(yesterday.minusDays(1));
        LocalDate tomorrow = adjustSunday(today.plusDays(1));
        LocalDate dayAfterTomorrow = adjustSunday(tomorrow.plusDays(1));

        List<LocalDate> targetDates = Arrays.asList(
                dayBeforeYesterday, yesterday, today, tomorrow, dayAfterTomorrow
        );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (UserData driver : drivers) {
            for (LocalDate date : targetDates) {
                // Находим заказы для этого водителя на эту дату
                List<Order> driverOrders = orders.stream()
                        .filter(o -> o.deliveryDate != null && o.driver != null)
                        .filter(o -> {
                            try {
                                LocalDate orderDate = LocalDate.parse(o.deliveryDate.trim(), formatter);
                                return orderDate.equals(date) && o.driver.trim().contains(driver.getName().trim());
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .collect(Collectors.toList());

                if (driverOrders.isEmpty()) {
                    if(driver.getRoutes().containsKey(date)) {
                        driver.removeRoute(date);
                        System.out.println("Удален маршрут у " + driver.getName() + " на " + date.format(formatter));
                    }
                } else {
                    if(!driver.getRoutes().containsKey(date)) {
                        driver.addRoute(date);
                        System.out.println("Добавлен маршрут у " + driver.getName() + " на " + date.format(formatter) +
                                ", заказов: " + driverOrders.size());
                    }
                }

            }
            List<LocalDate> oldRoutes = driver.getRoutes().keySet().stream()
                    .filter(d -> d.isBefore(dayBeforeYesterday))
                    .collect(Collectors.toList());

            for (LocalDate oldDate : oldRoutes) {
                driver.removeRoute(oldDate);
                System.out.println("Удален старый маршрут у " + driver.getName() + " на " + oldDate.format(formatter));
            }
        }
    }


    private static LocalDate adjustSunday(LocalDate date) {
        if (date != null && date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.plusDays(1); // переносим на понедельник
        }
        return date;
    }

    private static LocalDate parseDateSafely(String dateStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            return LocalDate.parse(dateStr, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    public static void startAutoReload(long intervalSeconds) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            load();
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public static List<File> getOrderPhotos(String orderNum) {
        File folder = new File("img");
        File[] files = folder.listFiles((dir, name) -> name.startsWith(orderNum));
        return files != null ? List.of(files) : List.of();
    }

    public static boolean hasPhotoInOrder(String orderNum) {
        File folder = new File("img");
        File[] files = folder.listFiles((dir, name) -> name.startsWith(orderNum));
        return files != null && files.length > 0;
    }

    public static boolean hasDriverProblemOrders(List<Order> orders, String driverName, LocalDate dateToCheck) {
        if (orders == null || driverName == null || dateToCheck == null) return false;

        // Если дата попадет на воскресенье, автоматически берём субботу
        LocalDate targetDate = dateToCheck;
        if (targetDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            targetDate = targetDate.minusDays(1);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (Order order : orders) {
            if (order.driver == null || order.deliveryDate == null) continue;
            if (!order.driver.trim().contains(driverName.trim())) continue;

            LocalDate orderDate;
            try {
                orderDate = LocalDate.parse(order.deliveryDate.trim(), formatter);
            } catch (Exception e) {
                continue;
            }

            if (orderDate.equals(targetDate)) {
                boolean noStatus = (order.orderStatus == null || order.orderStatus.trim().isEmpty());
                if (noStatus) {
                    return true; // проблема найдена
                }
            }
        }

        return false; // проблем нет
    }

    public static List<String> getDriverProblemOrderNumbers(List<Order> orders, String driverName, LocalDate dateToCheck) {
        List<String> problemOrders = new ArrayList<>();
        if (orders == null || driverName == null || dateToCheck == null) return problemOrders;

        // Если дата попадет на воскресенье, автоматически берём субботу
        LocalDate targetDate = dateToCheck;
        if (targetDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            targetDate = targetDate.minusDays(1);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (Order order : orders) {
            if (order.driver == null || order.deliveryDate == null) continue;
            if (!order.driver.trim().equalsIgnoreCase(driverName.trim())) continue;

            LocalDate orderDate;
            try {
                orderDate = LocalDate.parse(order.deliveryDate.trim(), formatter);
            } catch (Exception e) {
                continue;
            }

            if (orderDate.equals(targetDate)) {
                boolean noStatus = (order.orderStatus == null || order.orderStatus.trim().isEmpty());
                if (noStatus) {
                    problemOrders.add(order.orderNumber != null ? order.orderNumber.trim() : "Неизвестно");
                }
            }
        }

        return problemOrders;
    }


    public static void savePhotoToLocal(String fileId, String orderNum) {
        try {
            // Скачиваем файл от Telegram как InputStream
            InputStream inputStream = Main.getInstance().downloadFile(fileId);

            // Получаем расширение файла (если известно, можно захардкодить как ".jpg")
            String extension = ".jpg"; // можно также получить с помощью getFilePath(fileId)

            // Создаём директорию, если не существует
            String folderPath = "img/";
            new File(folderPath).mkdirs();

            // Формируем путь к файлу
            String baseName = folderPath + orderNum;
            File outFile = new File(baseName + extension);
            int i = 1;
            while (outFile.exists()) {
                outFile = new File(baseName + "_" + i + extension);
                i++;
            }

            // Записываем файл
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
            }

            System.out.println("✅ Файл сохранён: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<List<InlineKeyboardButton>> buildOrderButtons(List<Order> orders, String driverName, LocalDate dateToCheck) {
        if (orders == null || driverName == null || dateToCheck == null) {
            return new ArrayList<>();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Фильтруем заказы по дате и водителю
        List<Order> filteredOrders = orders.stream()
                .filter(o -> {
                    try {
                        return o.deliveryDate != null &&
                                LocalDate.parse(o.deliveryDate.trim(), formatter).equals(dateToCheck) &&
                                o.driver != null &&
                                o.driver.trim().contains(driverName.trim());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        // Иначе показываем все заказы за эту дату
        return buildButtonsFromOrders(filteredOrders, driverName, dateToCheck);
    }

    /**
     * Вспомогательный метод для генерации кнопок из списка заказов
     */
    private static List<List<InlineKeyboardButton>> buildButtonsFromOrders(List<Order> orders, String driverName, LocalDate date) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        int count = 0;

        for (Order order : orders) {
            String cleanNumber = order.getCleanOrderNumber();
            String orderNum = order.orderNumber != null ? order.orderNumber.trim() : "Неизвестно";
            String emoji = getEmojiByStatus(OrderStatus.fromDisplayName(order.orderStatus));
            String photoEmoji = hasPhotoInOrder(order.orderNumber) ? " \uD83D\uDCF8" : "";

            InlineKeyboardButton button = new InlineKeyboardButton("№" + cleanNumber + " " + emoji + photoEmoji)
                    .callbackData("order:" + orderNum +":" + date);

            row.add(button);
            count++;

            if (count % 2 == 0) {
                keyboard.add(row);
                row = new ArrayList<>();
            }
        }

        if (!row.isEmpty()) {
            keyboard.add(row);
        }

        // --- добавляем нижний ряд кнопок маршрута ---
        UserData driver = UserData.findUserByName(driverName);

        List<InlineKeyboardButton> routeRow3 = new ArrayList<>();
        routeRow3.add(new InlineKeyboardButton("\uD83E\uDDFE Счета").callbackData("rc:"  + driver.getId() + ":" + date));
        keyboard.add(routeRow3);

        if(!driver.getRouteStatus(date).isFinished()) {
            List<InlineKeyboardButton> routeRow = new ArrayList<>();
            routeRow.add(new InlineKeyboardButton("🏁 Завершить маршрут").callbackData("route:finish:" + date));
            keyboard.add(routeRow);
        }

        List<InlineKeyboardButton> routeRow2 = new ArrayList<>();
        routeRow2.add(new InlineKeyboardButton("⬅️ Назад").callbackData("routes_menu:" + driver.getId() + ":" + date));
        keyboard.add(routeRow2);
        return keyboard;
    }


    public static void drivers(Update update) {
        List<String> driverNames = orders.stream()
                .map(o -> o.driver)
                .filter(name -> name != null && !name.trim().isEmpty())
                .distinct()
                .toList();

        if (driverNames.isEmpty()) {
            if (update.callbackQuery() != null) {
                Main.getInstance().editMessage(update.callbackQuery().message().chat().id(),
                        update.callbackQuery().message().messageId(),
                        "🚫 Водители не найдены.");
            } else {
                Main.getInstance().sendMessage(update.message().chat().id(), "🚫 Водители не найдены.");
            }
            return;
        }



        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();



        for (String name : driverNames) {
            String nameOnly = name.replaceAll("[(@+].*$", "").trim();

            String[] parts = nameOnly.split("\\s+");
            String shortName = parts.length >= 2 ? parts[0] + " " + parts[1] : nameOnly;
            UserData driver = UserData.findUserByName(shortName);
            if(driver == null) continue;
            InlineKeyboardButton button = new InlineKeyboardButton("🚚 " + shortName)
                    .callbackData("driver:" + driver.getId());
            row.add(button);

            if (row.size() == 2) {
                keyboard.add(new ArrayList<>(row)); // добавляем строку из 2 кнопок
                row.clear();
            }
        }

        if (!row.isEmpty()) {
            keyboard.add(new ArrayList<>(row));
        }

        Long userId;
        if (update.message() != null && update.message().from() != null) {
            userId = update.message().from().id();
        } else if (update.callbackQuery() != null && update.callbackQuery().from() != null) {
            userId = update.callbackQuery().from().id();
        } else {
            // fallback или выброс исключения
            throw new RuntimeException("Не удалось определить пользователя.");
        }

        UserData currentUser = UserData.findUserById(userId);
        if(currentUser.getRole().equals("LOGISTIC")){
            keyboard.add(Arrays.asList(
                    new InlineKeyboardButton("➕ Добавить водителя").callbackData("drivers:add"),
                    new InlineKeyboardButton("🔍 Найти заказ").callbackData("order:find")
            ));
        } else{
            keyboard.add(Collections.singletonList(
                    new InlineKeyboardButton("🔍 Найти заказ").callbackData("order:find")
            ));

        }

        if (update.callbackQuery() != null) {
            Main.getInstance().editMessage(
                    update.callbackQuery().message().chat().id(),
                    update.callbackQuery().message().messageId(),
                    "👷 Выберите водителя:",
                    keyboard);
        } else {
            Main.getInstance().sendInlineKeyboard(
                    update.message().chat().id(),
                    keyboard,
                    "👷 Выберите водителя:");
        }
    }

    public static void getDriverOrders(Update update) {
        String data = update.callbackQuery().data();

        if (data.startsWith("driver:list") || data.startsWith("get_routes_back")) {
            drivers(update);
            Main.waitingForOrderNumber.remove(update.callbackQuery().message().chat().id());
            return;
        }

        if(data.startsWith("gr:")){
            String[] parts = data.split(":");
            long driverID = 0;
            LocalDate date = null;
            if (parts.length > 2) {
                try {
                    date = LocalDate.parse(parts[2]);
                    driverID = Long.parseLong(parts[1]);
                } catch (Exception ignore) {}
            }
            UserData driver = UserData.findUserById(driverID);
            if(driver.getRouteStatus(date).isStarted() || driver.getRouteStatus(date).isFinished()){
                if (OrderLoader.orders.isEmpty()) {
                    Main.getInstance().editMessage(update.callbackQuery().message().chat().id(), update.callbackQuery().message().messageId(), "Нет доступных заказов.");
                    return;
                }
                List<List<InlineKeyboardButton>> buttonsInline =
                        OrderLoader.buildOrderButtons(OrderLoader.orders, driver.getName(), date);
                if (buttonsInline.size() < 3) {
                    Main.getInstance().editMessage(update.callbackQuery().message().chat().id(), update.callbackQuery().message().messageId(), "Нет доступных заказов.");
                    return;
                }
                Main.getInstance().editMessage(update.callbackQuery().message().chat().id(), update.callbackQuery().message().messageId(), "Выберите заказ:", buttonsInline);
                return;
            }else{
                Routes.showOrdersForDriver(update, driver.getName(), date);

            }
        }

        if (data.startsWith("driver:")) {
            long driverID = Long.parseLong(data.substring("driver:".length()));
            UserData driver = UserData.findUserById(driverID);
            Routes.showDriverRoutes(driver.getName(), update);

        }
    }

}
