package Menus;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import core.Main;
import modules.OrderLoader;
import modules.OrderStatusUpdater;
import utils.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManagerMenu {

    public void open(Update update) {
        List<List<KeyboardButton>> buttons = Arrays.asList(
                Arrays.asList(
                        new KeyboardButton(EManagerMenuBtn.DRIVERS.getButtonText())
                )
        );
        Main.getInstance().sendKeyboard(update.message().chat().id(), Messages.managerenu, buttons, true, false);
    }

    public void open(Update update, String msg) {
        List<List<KeyboardButton>> buttons = Arrays.asList(
                Arrays.asList(
                        new KeyboardButton(EManagerMenuBtn.DRIVERS.getButtonText())
                )
        );
        Main.getInstance().sendKeyboard(update.message().chat().id(), msg, buttons, true, false);
    }

    public void confirm(Update update){
        Long chatId = update.callbackQuery().message().chat().id();
        Integer messageId = update.callbackQuery().message().messageId();
        String data = update.callbackQuery().data();
        if (data.startsWith("ManagerConfirm:")) {
            String[] parts = data.split(":");
            if (parts.length < 4) {
                Main.getInstance().editMessage(chatId, messageId, "Некорректные данные.");
                return;
            }

            String statusKey = parts[1];
            String orderNum = parts[2].split(" ")[0]; // "00000013574 от 23.05.2025" → "00000013574"
            Long driverId = Long.parseLong(parts[3]);
            OrderStatus status;
            try {
                status = OrderStatus.valueOf(statusKey);
            } catch (IllegalArgumentException e) {
                Main.getInstance().editMessage(chatId, messageId, "Неизвестный статус.");
                return;
            }

            Order order = OrderLoader.orders.stream()
                    .filter(o -> o.orderNumber != null && o.orderNumber.contains(orderNum))
                    .findFirst()
                    .orElse(null);

            if (order == null) {
                Main.getInstance().editMessage(chatId, messageId, "Заказ не найден.");
                return;
            }

            order.orderStatus = status.getDisplayName();
            OrderStatusUpdater.updateOrderStatus(order.orderNumber, order.orderStatus);
            OrderStatusUpdater.updateWebOrderStatus(order.webOrderNumber, status.getCode());

            UserData manager = UserData.findUserById(update.callbackQuery().from().id());
            UserData driver = UserData.findUserById(driverId);

            String managerName = manager != null ? manager.getName() : "Неизвестный менеджер";
            String driverName = driver != null ? driver.getName() : "Неизвестный водитель";

            // Уведомляем водителя
            String driverMessage = String.format(
                    "✅ Ваш запрос на отмену заказа №%s подтвержден менеджером %s\n" +
                            "Статус изменен на: %s %s",
                    orderNum,
                    managerName,
                    OrderStatus.getEmojiByStatus(status),
                    status.getDisplayName()
            );

            if(status == OrderStatus.RESCHEDULED)
                driverMessage = String.format(
                        "✅ Ваш запрос на перенос заказа №%s подтвержден менеджером %s\n" +
                                "Статус изменен на: %s %s",
                        orderNum,
                        managerName,
                        OrderStatus.getEmojiByStatus(status),
                        status.getDisplayName()
                );
            Main.getInstance().sendMessage(driverId, driverMessage);

            // Уведомляем логистов и админов
            String notifyText = String.format(
                    "🛑 Заказ №%s отменен\n" +
                            "Водитель: %s\n" +
                            "Менеджер: %s\n" +
                            "Причина: %s %s",
                    orderNum,
                    driverName,
                    managerName,
                    OrderStatus.getEmojiByStatus(status),
                    status.getDisplayName()
            );

            if(status == OrderStatus.RESCHEDULED)
                notifyText = String.format(
                        "🛑 Заказ №%s перенесен\n" +
                                "Водитель: %s\n" +
                                "Менеджер: %s\n" +
                                "Причина: %s %s",
                        orderNum,
                        driverName,
                        managerName,
                        OrderStatus.getEmojiByStatus(status),
                        status.getDisplayName()
                );

            for (UserData user : Main.users) {
                if (user.getRole() != null) {
                    String role = user.getRole().toUpperCase();
                    if (role.equals("LOGISTIC") || role.equals("ADMIN")) {
                        if(user.getId() == null) return;
                        Main.getInstance().sendMessage(user.getId(), notifyText);
                    }
                }
            }

            if(status == OrderStatus.RESCHEDULED){
                // Обновляем сообщение менеджера
                Main.getInstance().editMessage(
                        update.callbackQuery().message().chat().id(),
                        update.callbackQuery().message().messageId(),
                        "✅ Вы подтвердили перенос заказа №" + orderNum
                );
            } else{
                // Обновляем сообщение менеджера
                Main.getInstance().editMessage(
                        update.callbackQuery().message().chat().id(),
                        update.callbackQuery().message().messageId(),
                        "✅ Вы подтвердили отмену заказа №" + orderNum
                );
            }

        }

// Обработка отклонения отмены менеджером
        if (data.startsWith("ManagerReject:")) {
            String[] parts = data.split(":");
            if (parts.length < 3) {
                Main.getInstance().editMessage(chatId, messageId, "Некорректные данные.");
                return;
            }

            String orderNum = parts[1];
            Long driverId = Long.parseLong(parts[2]);

            UserData manager = UserData.findUserById(update.callbackQuery().from().id());
            UserData driver = UserData.findUserById(driverId);

            String managerName = manager != null ? manager.getName() : "Неизвестный менеджер";
            String driverName = driver != null ? driver.getName() : "Неизвестный водитель";

            // Уведомляем водителя
            String driverMessage = String.format(
                    "❌ Ваш запрос №%s отклонен менеджером %s",
                    orderNum,
                    managerName
            );

            Main.getInstance().sendMessage(driverId, driverMessage);
            // Уведомляем менеджера
            Main.getInstance().editMessage(
                    update.callbackQuery().message().chat().id(),
                    update.callbackQuery().message().messageId(),
                    "❌ Вы отклонили запрос №" + orderNum + "\nВодитель: " + driverName
            );
        }

    }

    public static class ManagerRequestStore {
        // Ключ: "managerName:orderNum"
        private static final Map<String, OrderStatus> activeRequests = new HashMap<>();

        public static boolean hasActiveRequest(String managerName, String orderNum, OrderStatus status) {
            String key = managerName + ":" + orderNum;
            return activeRequests.containsKey(key) && activeRequests.get(key) == status;
        }

        public static void addRequest(String managerName, String orderNum, OrderStatus status) {
            String key = managerName + ":" + orderNum;
            activeRequests.put(key, status);
        }

        public static void removeRequest(String managerName, String orderNum) {
            activeRequests.remove(managerName + ":" + orderNum);
        }
    }


}
