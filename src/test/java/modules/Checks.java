package modules;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.ParseMode;
import core.Main;
import utils.Order;
import utils.UserData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class Checks {

    public static void handleChecksCallback(Update update) {
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
            try {
                date = LocalDate.parse(parts[2]);
            } catch (Exception ignore) {}
        }
        long userID = 0;
        if (data.startsWith("rc:")) {
            userID = Long.parseLong(parts[1]);
            UserData driver = UserData.findUserById(userID);
            if (driver == null) return;

            List<SupplierOrderWithParent> checks = findSupplierOrdersByLoadingDate(OrderLoader.orders, date);

            // Только SupplierOrder, где текущий водитель — водитель погрузки
            List<SupplierOrderWithParent> loadingDriverOrders = checks.stream()
                    .filter(so -> {
                        String loadingDriver = Optional.ofNullable(so.supplierOrder.loadingDriver).orElse("");
                        return loadingDriver.contains(driver.getName());
                    })
                    .toList();

            // Только для формирования блока "Забрать товар" проверяем Order.driver
            List<SupplierOrderWithParent> orderDriverOrders = checks.stream()
                    .filter(so -> {
                        String orderDriver = Optional.ofNullable(so.order.driver).orElse("");
                        String loadingDriver = Optional.ofNullable(so.supplierOrder.loadingDriver).orElse("");
                        return orderDriver.contains(driver.getName()) && !orderDriver.equals(loadingDriver);
                    })
                    .toList();

            // кнопка назад
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            if(user.getRole().equalsIgnoreCase("DRIVER")) {
                keyboard.add(List.of(new InlineKeyboardButton("⬅️ Назад").callbackData("routes:" + date)));
            } else {
                keyboard.add(List.of(new InlineKeyboardButton("⬅️ Назад").callbackData("gr:" + driver.getId() + ":" + date)));
            }
            if (loadingDriverOrders.isEmpty() && orderDriverOrders.isEmpty()) {
                Main.getInstance().editMessage(chatId, messageId,
                        "❌ Нет заказов для водителя " + driver.getName() + " на дату " + date, keyboard);
                return;
            }

            // группировка: поставщик -> склад -> список заказов (только для погрузок текущего водителя)
            Map<String, Map<String, List<SupplierOrderWithParent>>> grouped =
                    loadingDriverOrders.stream()
                            .collect(Collectors.groupingBy(
                                    so -> Optional.ofNullable(so.supplierOrder.supplier).orElse("❓ Неизвестный поставщик"),
                                    TreeMap::new,
                                    Collectors.groupingBy(
                                            so -> Optional.ofNullable(so.supplierOrder.supplierWarehouse).orElse("❓ Неизвестный склад"),
                                            TreeMap::new,
                                            Collectors.toList()
                                    )
                            ));

            StringBuilder sb = new StringBuilder();
            int supplierCount = 1;

            for (var supplierEntry : grouped.entrySet()) {
                sb.append(supplierCount++).append(". Поставщик: ").append(supplierEntry.getKey()).append("\n");

                for (var warehouseEntry : supplierEntry.getValue().entrySet()) {
                    sb.append("   - Склад: ").append(warehouseEntry.getKey()).append("\n");

                    for (SupplierOrderWithParent sop : warehouseEntry.getValue()) {
                        String orderDriver = Optional.ofNullable(sop.order.driver).orElse("—");

                        sb.append("     🚚 <b>Заказ №")
                                .append(sop.order.getCleanOrderNumber())
                                .append("</b>\n");
                        sb.append("      📄 Счет: ").append(Optional.ofNullable(sop.supplierOrder.supplierInvoice).orElse("—")).append("\n");
                        String composition = Optional.ofNullable(sop.supplierOrder.productComposition).orElse("—");
                        if (!composition.equals("—")) {
                            String[] items = composition.split("\\r?\\n");
                            for (String item : items) {
                                sb.append("         📦 ").append(item.trim()).append("\n");
                            }
                        } else {
                            sb.append("         📦 —\n");
                        }

                        // если текущий водитель — водитель погрузки, но заказ принадлежит другому водителю
                        if (!orderDriver.equals("—") && !orderDriver.contains(driver.getName())) {
                            sb.append("         ⚠️ Передать водителю заказа: ").append(orderDriver).append("\n");
                        }
                        sb.append("\n");
                    }
                }
            }

            // блок "Забрать товар" для текущего водителя (Order.driver = он, а погрузку делает другой)
            if (!orderDriverOrders.isEmpty()) {
                sb.append("\n📌 Забрать товар:\n");
                for (SupplierOrderWithParent sop : orderDriverOrders) {
                    String loadingDriver = Optional.ofNullable(sop.supplierOrder.loadingDriver).orElse("—");
                    sb.append("   - У ").append(loadingDriver)
                            .append(" для заказа №").append(sop.order.getCleanOrderNumber()).append("\n");
                    String composition = Optional.ofNullable(sop.supplierOrder.productComposition).orElse("—");
                    if (!composition.equals("—")) {
                        String[] items = composition.split("\\r?\\n");
                        for (String item : items) {
                            sb.append("         📦 ").append(item.trim()).append("\n");
                        }
                    } else {
                        sb.append("         📦 —\n");
                    }


                }
            }

            Main.getInstance().editMessage(chatId, messageId, sb.toString(), ParseMode.HTML, keyboard);
        }
    }


    public static List<SupplierOrderWithParent> findSupplierOrdersByLoadingDate(List<Order> orders, LocalDate date) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<SupplierOrderWithParent> result = new ArrayList<>();

        for (Order order : orders) {
            if (order.supplierOrders == null || order.supplierOrders.isEmpty()) continue;

            for (Order.SupplierOrder so : order.supplierOrders) {
                if (so.loadingDate == null || so.loadingDate.isBlank()) continue;

                try {
                    // парсим в LocalDateTime
                    LocalDateTime ldt = LocalDateTime.parse(so.loadingDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    LocalDate orderDate = ldt.toLocalDate(); // оставляем только дату

                    if (orderDate.equals(date)) {
                        result.add(new SupplierOrderWithParent(order, so));
                    }
                } catch (DateTimeParseException e) {
                    // пропускаем неверный формат даты
                }
            }
        }
        return result;
    }

    public static class SupplierOrderWithParent {
        public final Order order; // сам заказ
        public final Order.SupplierOrder supplierOrder; // заказ поставщику

        public SupplierOrderWithParent(Order order, Order.SupplierOrder supplierOrder) {
            this.order = order;
            this.supplierOrder = supplierOrder;
        }
    }


}
