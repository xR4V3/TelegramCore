package Menus;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import core.Main;
import utils.ECourierMenuBtn;
import utils.Messages;
import utils.Order;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

public class CourierMenu {

    public void open(Update update) {
        List<List<KeyboardButton>> buttons = List.of(
                List.of(
                        new KeyboardButton(ECourierMenuBtn.ROUTES.getButtonText())
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
                        new KeyboardButton(ECourierMenuBtn.ROUTES.getButtonText())
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

    /**
     * Формирует текст с заказами на завтра (обычные дни) или
     * на субботу и понедельник (если сегодня пятница).
     *
     * ПВЗ определяется по адресу заказа (поле order.address),
     * если он содержит один из адресов из pvzAddresses.
     */
    public static String getOrdersForTomorrowOrWeekend(List<Order> orders, Set<String> pvzAddresses) {
        if (orders == null || orders.isEmpty()) {
            return "Заказов нет";
        }

        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();

        // даты, которые нужно проверить
        List<LocalDate> targetDates = new ArrayList<>();

        if (dayOfWeek == DayOfWeek.FRIDAY) {
            targetDates.add(today.plusDays(1)); // суббота
            targetDates.add(today.plusDays(3)); // понедельник
        } else if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return "Сегодня " + dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ru")) +
                    ", заказы не выводятся";
        } else {
            targetDates.add(today.plusDays(1)); // только завтра
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // структура: Дата -> (Водитель -> Список заказов)
        // TreeMap — чтобы даты шли по порядку (сначала суббота, потом понедельник)
        Map<LocalDate, Map<String, List<String>>> ordersByDateAndDriver = new TreeMap<>();

        for (Order order : orders) {
            if (order == null
                    || order.deliveryDate == null || order.deliveryDate.isEmpty()
                    || order.driver == null || order.driver.isEmpty()) {
                continue;
            }

            LocalDate deliveryDate;
            try {
                deliveryDate = LocalDate.parse(order.deliveryDate.trim(), formatter);
            } catch (Exception e) {
                continue;
            }

            if (targetDates.contains(deliveryDate)) {
                // короткий номер заказа (первое "слово")
                String shortNum = simplifyOrderNumber(order.orderNumber);

                boolean isPvz = hasPvzFlag(order, pvzAddresses);
                String displayNum = isPvz ? shortNum + " (ПВЗ)" : shortNum;

                ordersByDateAndDriver
                        .computeIfAbsent(deliveryDate, d -> new LinkedHashMap<>())
                        .computeIfAbsent(order.driver, d -> new ArrayList<>())
                        .add(displayNum);
            }
        }

        if (ordersByDateAndDriver.isEmpty()) {
            return "Заказов на выбранные даты нет";
        }

        // формируем сообщение
        StringBuilder sb = new StringBuilder("📦 Заказы:\n");

        for (Map.Entry<LocalDate, Map<String, List<String>>> dateEntry : ordersByDateAndDriver.entrySet()) {
            LocalDate date = dateEntry.getKey();
            sb.append("\n📅 Дата доставки: ").append(date.format(formatter)).append("\n");

            for (Map.Entry<String, List<String>> driverEntry : dateEntry.getValue().entrySet()) {
                String shortName = toShortName(driverEntry.getKey());
                sb.append("\n🚛 ").append(shortName).append("\n");
                for (String orderNum : driverEntry.getValue()) {
                    sb.append(" - ").append(orderNum).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Определяем, что заказ — ПВЗ.
     * Здесь используется адрес заказа:
     * предполагается, что в классе Order есть поле address.
     *
     * Если у тебя оно называется по-другому (например deliveryAddress),
     * просто замени order.address на нужное поле.
     */
    private static boolean hasPvzFlag(Order order, Set<String> pvzAddresses) {
        if (order == null || pvzAddresses == null || pvzAddresses.isEmpty()) {
            return false;
        }

        // !!! ЗДЕСЬ важное место: поле с адресом
        // поменяй order.address на своё название поля, если нужно
        String address = order.deliveryAddress; // <--- подстрой под свой класс Order

        if (address == null || address.isEmpty()) {
            return false;
        }

        String addrNorm = address.trim().toUpperCase(Locale.ROOT);

        for (String pvzAddress : pvzAddresses) {
            if (pvzAddress == null || pvzAddress.isEmpty()) continue;
            String pvzNorm = pvzAddress.trim().toUpperCase(Locale.ROOT);
            if (addrNorm.contains(pvzNorm)) {
                return true;
            }
        }

        return false;
    }

    private static String toShortName(String fullName) {
        if (fullName == null) return "—";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length >= 2) return parts[0] + " " + parts[1];  // Имя Фамилия
        return parts[0];
    }

    private static String simplifyOrderNumber(String orderNum) {
        if (orderNum == null) return "—";
        String[] parts = orderNum.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : orderNum.trim();
    }
}
