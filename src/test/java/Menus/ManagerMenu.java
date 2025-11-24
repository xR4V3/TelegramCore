package Menus;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import core.Main;
import modules.OrderLoader;
import modules.OrderStatusUpdater;
import utils.*;

import java.time.LocalDate;
import java.util.*;

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

            if(status == OrderStatus.RESCHEDULED_BY_CLIENT ||
                    status == OrderStatus.RESCHEDULED_BY_STORE)
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

            if(status == OrderStatus.RESCHEDULED_BY_CLIENT ||
                    status == OrderStatus.RESCHEDULED_BY_STORE)
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

            if(status == OrderStatus.NOT_SHIPPED_NO_INVOICE ||
                    status == OrderStatus.NOT_SHIPPED_NO_STOCK ||
                    status == OrderStatus.NOT_SHIPPED_NO_SPACE ||
                    status == OrderStatus.PARTIALLY_DELIVERED ||
                    status == OrderStatus.NOT_SHIPPED_NOT_PICKED_FROM_DRIVER)
                notifyText = String.format(
                        "🛑 По заказу №%s изменен статус\n" +
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

            if(status == OrderStatus.RESCHEDULED_BY_CLIENT ||
                    status == OrderStatus.RESCHEDULED_BY_STORE){
                // Обновляем сообщение менеджера
                Main.getInstance().editMessage(
                        update.callbackQuery().message().chat().id(),
                        update.callbackQuery().message().messageId(),
                        "✅ Вы подтвердили перенос заказа №" + orderNum
                );

            }
            else if(status == OrderStatus.NOT_SHIPPED_NO_INVOICE ||
                    status == OrderStatus.NOT_SHIPPED_NO_STOCK ||
                    status == OrderStatus.NOT_SHIPPED_NO_SPACE ||
                    status == OrderStatus.PARTIALLY_DELIVERED ||
                    status == OrderStatus.NOT_SHIPPED_NOT_PICKED_FROM_DRIVER){
                Main.getInstance().editMessage(
                        update.callbackQuery().message().chat().id(),
                        update.callbackQuery().message().messageId(),
                        "✅ Вы подтвердили статус заказа №" + orderNum
                );
            }
            else{
                // Обновляем сообщение менеджера
                Main.getInstance().editMessage(
                        update.callbackQuery().message().chat().id(),
                        update.callbackQuery().message().messageId(),
                        "✅ Вы подтвердили отмену заказа №" + orderNum
                );
            }

            // измерим длительность
            ManagerRequestStore.RequestInfo info = ManagerRequestStore.resolveAndRemove(orderNum);
            long createdAt = (info != null ? info.createdAtMs : System.currentTimeMillis());
            long durationMs = Math.max(0, System.currentTimeMillis() - createdAt);

// найдём менеджера (вы уже нашли выше): manager
            if (manager != null) {
                LocalDate today = LocalDate.now(); // или дата запроса, если вы её храните отдельно
                UserData.ManagerStats.ManagerDailyStats ms = manager.getManagerStats().getOrCreate(today);
                // Подтверждение — считаем как "принято"
                ms.addAccepted(durationMs);
                UserData.saveUsersToFile();
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
            // измерим длительность
            ManagerRequestStore.RequestInfo info = ManagerRequestStore.resolveAndRemove(orderNum);
            long createdAt = (info != null ? info.createdAtMs : System.currentTimeMillis());
            long durationMs = Math.max(0, System.currentTimeMillis() - createdAt);

// найдём менеджера (вы уже нашли выше): manager
            if (manager != null) {
                LocalDate today = LocalDate.now(); // или дата запроса, если вы её храните отдельно
                UserData.ManagerStats.ManagerDailyStats ms = manager.getManagerStats().getOrCreate(today);
                // Подтверждение — считаем как "принято"
                ms.addAccepted(durationMs);
                UserData.saveUsersToFile();
            }

        }

    }

    public static class ManagerRequestStore {
        private static final Map<String, RequestInfo> activeRequests = new HashMap<>();
        // Ключ: orderNum (или managerName:orderNum — но orderNum уникален для простоты)
        private static String key(String orderNum) { return orderNum; }

        public static void startTimer(String managerName, String orderNum, OrderStatus requestedStatus) {
            String k = key(orderNum);
            if (!activeRequests.containsKey(k)) {
                activeRequests.put(k, new RequestInfo(managerName, orderNum, requestedStatus, System.currentTimeMillis()));
            }
        }

        public static RequestInfo resolveAndRemove(String orderNum) {
            return activeRequests.remove(key(orderNum));
        }

        public static boolean hasActiveRequest(String managerName, String orderNum, OrderStatus status) {
            RequestInfo info = activeRequests.get(key(orderNum));
            return info != null && info.requestedStatus == status && Objects.equals(info.managerName, managerName);
        }

        public static void addRequest(String managerName, String orderNum, OrderStatus status) {
            // совместимость со старым API: если кто-то вызывает — стартуем таймер
            startTimer(managerName, orderNum, status);
        }

        public static void removeRequest(String managerName, String orderNum) {
            activeRequests.remove(key(orderNum));
        }

        public static class RequestInfo {
            public final String managerName;
            public final String orderNum;
            public final OrderStatus requestedStatus;
            public final long createdAtMs;

            public RequestInfo(String managerName, String orderNum, OrderStatus requestedStatus, long createdAtMs) {
                this.managerName = managerName;
                this.orderNum = orderNum;
                this.requestedStatus = requestedStatus;
                this.createdAtMs = createdAtMs;
            }
        }
    }



}
