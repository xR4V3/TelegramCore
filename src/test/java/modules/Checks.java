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

        if (data.startsWith("rc:")) {
            long driverId = Long.parseLong(parts[1]);
            UserData driver = UserData.findUserById(driverId);
            if (driver == null) return;

            // Поставки ровно на день погрузки
            List<SupplierOrderWithParent> loadingDayChecks = findSupplierOrdersByLoadingDate(OrderLoader.orders, date);
            // Поставки с погрузкой <= date
            List<SupplierOrderWithParent> checksUpTo = findSupplierOrdersByLoadingDateUpTo(OrderLoader.orders, date);

            // 1) "Погрузка сегодня" — только для водителя погрузки
            List<SupplierOrderWithParent> loadingDriverOrders = loadingDayChecks.stream()
                    .filter(so -> containsName(showOrDash(so.supplierOrder.loadingDriver), driver.getName()))
                    .toList();

            // 2) "Передать товар" — водителю погрузки
            List<SupplierOrderWithParent> toPassOnLoadingDay = loadingDayChecks.stream()
                    .filter(so -> {
                        String loadingDriver = showOrDash(so.supplierOrder.loadingDriver);
                        String orderDriverRaw = so.order.driver;
                        return containsName(loadingDriver, driver.getName())
                                && !isBlank(orderDriverRaw)
                                && !containsName(orderDriverRaw, driver.getName());
                    })
                    .toList();

            // 2) "Передать товар" — показываем в любой день от погрузки до доставки включительно,
// если загрузил этот водитель, а заказ закреплён за другим водителем.
            LocalDate refDate = date;
            List<SupplierOrderWithParent> toPassWithinWindow = checksUpTo.stream()
                    .filter(so -> {
                        String loadingDriver = showOrDash(so.supplierOrder.loadingDriver);
                        String orderDriverRaw = so.order.driver;
                        boolean byThisDriverLoaded = containsName(loadingDriver, driver.getName());
                        boolean belongsToOtherDriver = !isBlank(orderDriverRaw) && !containsName(orderDriverRaw, driver.getName());
                        return byThisDriverLoaded && belongsToOtherDriver && withinTransferWindow(so.order, so.supplierOrder, refDate);
                    })
                    .toList();

// 3) "Забрать товар" — показываем в любой день от погрузки до доставки включительно,
// если заказ за этим водителем, а грузил другой.
            LocalDate refDate2 = date;
            List<SupplierOrderWithParent> pickUpWithinWindow = checksUpTo.stream()
                    .filter(so -> {
                        String orderDriverRaw   = so.order.driver;
                        String loadingDriverRaw = so.supplierOrder.loadingDriver;
                        boolean forThisDriver = containsName(orderDriverRaw, driver.getName());
                        boolean loadedByOther = !isBlank(loadingDriverRaw) && !containsName(loadingDriverRaw, driver.getName());
                        return forThisDriver && loadedByOther && withinTransferWindow(so.order, so.supplierOrder, refDate2);
                    })
                    .toList();


            // Кнопка назад
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            if (user.getRole().equalsIgnoreCase("DRIVER")) {
                keyboard.add(List.of(new InlineKeyboardButton("⬅️ Назад").callbackData("routes:" + date)));
            } else {
                keyboard.add(List.of(new InlineKeyboardButton("⬅️ Назад").callbackData("gr:" + driver.getId() + ":" + date)));
            }

            if (loadingDriverOrders.isEmpty() && toPassOnLoadingDay.isEmpty() && toPassWithinWindow.isEmpty() && pickUpWithinWindow.isEmpty()) {
                Main.getInstance().editMessage(chatId, messageId,
                        "❌ Нет заказов для водителя " + driver.getName() + " на дату " + date, keyboard);
                return;
            }

            StringBuilder sb = new StringBuilder();

            // -------- Секция: Погрузка сегодня --------
            if (!loadingDriverOrders.isEmpty()) {
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

                int supplierCount = 1;
                sb.append("📦 <b>Погрузка сегодня (").append(date).append(")</b>\n\n");
                for (var supplierEntry : grouped.entrySet()) {
                    sb.append(supplierCount++).append(". Поставщик: ").append(supplierEntry.getKey()).append("\n");
                    for (var warehouseEntry : supplierEntry.getValue().entrySet()) {
                        sb.append("   - Склад: ").append(warehouseEntry.getKey()).append("\n");

                        List<SupplierOrderWithParent> items = new ArrayList<>(warehouseEntry.getValue());
                        items.sort(Comparator
                                .comparing((SupplierOrderWithParent x) ->
                                                firstNonBlank(
                                                        Optional.ofNullable(x.supplierOrder.organization).orElse(null),
                                                        Optional.ofNullable(x.order.organization).orElse(null),
                                                        "")
                                        , String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(x -> Optional.ofNullable(x.supplierOrder.supplier).orElse(""), String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(x -> Optional.ofNullable(x.supplierOrder.supplierWarehouse).orElse(""), String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(x -> x.order.getCleanOrderNumber(), String.CASE_INSENSITIVE_ORDER));

                        for (SupplierOrderWithParent sop : items) {
                            String orderDriverRaw = sop.order.driver;
                            String orderDriverShow = showOrDash(orderDriverRaw);
                            String org = firstNonBlank(
                                    Optional.ofNullable(sop.supplierOrder.organization).orElse(null),
                                    Optional.ofNullable(sop.order.organization).orElse(null),
                                    "—"
                            );
                            String invoice = Optional.ofNullable(sop.supplierOrder.supplierInvoice).orElse("—");

                            sb.append("     🚚 Заказ №").append(sop.order.getCleanOrderNumber())
                                    .append(" · 🏢 ").append(org).append("\n");

                            sb.append("      📄 <b><u>Счёт: ").append(escape(invoice)).append("</u></b>\n");

                            String composition = Optional.ofNullable(sop.supplierOrder.productComposition).orElse("—");
                            if (!composition.equals("—")) {
                                String[] itemsLines = composition.split("\\r?\\n");
                                for (String item : itemsLines) {
                                    String trimmed = item.trim();
                                    if (!trimmed.isEmpty()) sb.append("         📦 ").append(trimmed).append("\n");
                                }
                            } else {
                                sb.append("         📦 —\n");
                            }

                            if (!isBlank(orderDriverRaw) && !containsName(orderDriverRaw, driver.getName())) {
                                sb.append("         ⚠️ Передать водителю заказа: ").append(orderDriverShow).append("\n");
                            }
                            sb.append("\n");
                        }
                    }

                }
            }

            // NEW: Перемещения — в конце всех складов, до "Передать/Забрать"
            List<MovementWithParent> todaysMovementsForDriver =
                    findMovementsForDriverOnDeliveryDate(OrderLoader.orders, date, driver.getName());

            if (!todaysMovementsForDriver.isEmpty()) {
                // Сортируем красиво: по организации (из заказа), затем по номеру заказа
                todaysMovementsForDriver.sort(Comparator
                        .comparing((MovementWithParent x) -> firstNonBlank(
                                        Optional.ofNullable(x.order.organization).orElse(null),
                                        Optional.ofNullable(x.order.organization).orElse(null), // дубль для читаемости
                                        "")
                                , String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(x -> x.order.getCleanOrderNumber(), String.CASE_INSENSITIVE_ORDER));

                sb.append("\n🚚 <b>Перемещения (дата доставки: ").append(date).append(")</b>\n");
                for (MovementWithParent m : todaysMovementsForDriver) {
                    appendMovementLine(sb, m);
                }
            }


// -------- Секция: Передать товар --------
            if (!toPassOnLoadingDay.isEmpty() || !toPassWithinWindow.isEmpty()) {
                sb.append("\n⚠️ <b>Передать товар</b>\n");

                if (!toPassOnLoadingDay.isEmpty()) {
                    sb.append("   <i>День погрузки (").append(date).append("):</i>\n");
                    for (SupplierOrderWithParent sop : toPassOnLoadingDay) {
                        appendPassLine(sb, sop);
                    }
                }

                if (!toPassWithinWindow.isEmpty()) {
                    sb.append("   <i>В период до доставки (на дату ").append(date).append("):</i>\n");
                    for (SupplierOrderWithParent sop : toPassWithinWindow) {
                        appendPassLine(sb, sop);
                    }
                }
            }

// -------- Секция: Забрать товар --------
            if (!pickUpWithinWindow.isEmpty()) {
                sb.append("\n📌 <b>Забрать товар</b> <i>(в период до доставки, на дату ")
                        .append(date).append(")</i>\n");
                for (SupplierOrderWithParent sop : pickUpWithinWindow) {
                    String loadingDriver = showOrDash(sop.supplierOrder.loadingDriver);
                    String org = firstNonBlank(
                            Optional.ofNullable(sop.supplierOrder.organization).orElse(null),
                            Optional.ofNullable(sop.order.organization).orElse(null),
                            "—"
                    );
                    sb.append("   <b>- У ").append(loadingDriver)
                            .append("</b> для заказа №").append(sop.order.getCleanOrderNumber())
                            .append(" · 🏢 ").append(org).append("\n");

                    String composition = Optional.ofNullable(sop.supplierOrder.productComposition).orElse("—");
                    if (!composition.equals("—")) {
                        String[] items = composition.split("\\r?\\n");
                        for (String item : items) {
                            String trimmed = item.trim();
                            if (!trimmed.isEmpty()) {
                                sb.append("         📦 ").append(trimmed).append("\n");
                            }
                        }
                    } else {
                        sb.append("         📦 —\n");
                    }
                }
            }


            List<String> parts1 = splitBySize(sb.toString(), 3800);

            List<List<InlineKeyboardButton>> NO_KB = Collections.emptyList();

            if (parts1.size() == 1) {
                // Одна часть — редактируем исходное и оставляем клавиатуру
                Main.getInstance().editMessage(chatId, messageId, parts1.get(0), ParseMode.HTML, keyboard);
            } else {
                // 1) Переписываем исходное сообщение первым куском БЕЗ клавиатуры
                Main.getInstance().editMessage(chatId, messageId, parts1.get(0), ParseMode.HTML, NO_KB);

                // 2) Промежуточные куски (если есть) — без клавиатуры
                for (int i = 1; i < parts1.size() - 1; i++) {
                    Main.getInstance().sendMessage(chatId, parts1.get(i), ParseMode.HTML, NO_KB);
                }

                // 3) Последний кусок — С клавиатурой (в т.ч. если частей ровно 2)
                Main.getInstance().sendMessage(chatId, parts1.get(parts1.size() - 1), ParseMode.HTML, keyboard);
            }
        }
    }

    // ---------------------------------------
    // HELPERS: поиск/парсинг дат
    // ---------------------------------------

    public static List<SupplierOrderWithParent> findSupplierOrdersByLoadingDate(List<Order> orders, LocalDate date) {
        if (orders == null || orders.isEmpty()) return Collections.emptyList();

        List<SupplierOrderWithParent> result = new ArrayList<>();
        for (Order order : orders) {
            if (order.supplierOrders == null || order.supplierOrders.isEmpty()) continue;
            for (Order.SupplierOrder so : order.supplierOrders) {
                if (so.loadingDate == null || so.loadingDate.isBlank()) continue;
                if (sameDate(so.loadingDate, date)) {
                    result.add(new SupplierOrderWithParent(order, so));
                }
            }
        }
        return result;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static List<SupplierOrderWithParent> findSupplierOrdersByLoadingDateUpTo(List<Order> orders, LocalDate date) {
        if (orders == null || orders.isEmpty()) return Collections.emptyList();

        List<SupplierOrderWithParent> result = new ArrayList<>();
        for (Order order : orders) {
            if (order.supplierOrders == null || order.supplierOrders.isEmpty()) continue;

            for (Order.SupplierOrder so : order.supplierOrders) {
                if (so.loadingDate == null || so.loadingDate.isBlank()) continue;

                try {
                    LocalDateTime ldt = LocalDateTime.parse(so.loadingDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    LocalDate orderDate = ldt.toLocalDate();
                    if (!orderDate.isAfter(date)) { // <= date
                        result.add(new SupplierOrderWithParent(order, so));
                    }
                } catch (DateTimeParseException ignored) {}
            }
        }
        return result;
    }

    private static boolean sameDate(String isoLocalDateTime, LocalDate date) {
        try {
            LocalDateTime ldt = LocalDateTime.parse(isoLocalDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return ldt.toLocalDate().equals(date);
        } catch (Exception e) {
            return false;
        }
    }

    private static Optional<LocalDate> getDeliveryDate(Order order) {
        // 1) Подставьте реальные поля вашей модели:
        String dt = null;
        if (!isBlank(order.deliveryDate)) {
            dt = order.deliveryDate;
        }
        // Если есть дата-время, тоже попробуем
        if (dt == null && !isBlank(order.deliveryDate)) {
            dt = order.deliveryDate;
        }
        if (dt == null) return Optional.empty();

        return parseFlexibleDateToLocalDate(dt);
    }

    private static Optional<LocalDate> parseFlexibleDateToLocalDate(String raw) {
        if (isBlank(raw)) return Optional.empty();

        // Чистим пробелы
        String s = raw.trim();

        // Кандидаты форматов (порядок важен: от наиболее специфичных к общим)
        DateTimeFormatter[] fmts = new DateTimeFormatter[] {
                // ISO c секундной частью
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                // ISO только дата
                DateTimeFormatter.ISO_LOCAL_DATE,
                // dd.MM.yyyy
                DateTimeFormatter.ofPattern("d.M.uuuu"),
                // dd/MM/yyyy
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                // dd-MM-yyyy
                DateTimeFormatter.ofPattern("d-M-uuuu"),
                // Иногда встречается dd.MM.yy
                DateTimeFormatter.ofPattern("d.M.uu")
        };

        for (DateTimeFormatter fmt : fmts) {
            try {
                if (fmt == DateTimeFormatter.ISO_LOCAL_DATE_TIME) {
                    return Optional.of(LocalDateTime.parse(s, fmt).toLocalDate());
                } else {
                    return Optional.of(LocalDate.parse(s, fmt));
                }
            } catch (Exception ignore) { /* пробуем следующий формат */ }
        }

        // Небольшая эвристика: если строка похожа на "20.10.2025 14:30"
        // — попробуем вытащить только дату до пробела и распарсить её.
        int sp = s.indexOf(' ');
        if (sp > 0) {
            return parseFlexibleDateToLocalDate(s.substring(0, sp));
        }

        return Optional.empty();
    }



    private static boolean belongsToDeliveryDay(Order order, LocalDate date) {
        // Показать только когда доставка именно В ЭТУ дату
        return getDeliveryDate(order)
                .map(d -> !d.isBefore(date))
                .orElse(false); // если даты нет — не считаем днем доставки
    }

    // ---------------------------------------
    // STRING utils
    // ---------------------------------------
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String showOrDash(String s) {
        return isBlank(s) ? "—" : s.trim();
    }

    private static boolean containsName(String haystack, String needle) {
        if (isBlank(haystack) || isBlank(needle)) return false;
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static String firstNonBlank(String a, String b, String def) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return def;
    }

    private static Optional<LocalDate> getLoadingDate(Order.SupplierOrder so) {
        if (so == null || isBlank(so.loadingDate)) return Optional.empty();
        try {
            return Optional.of(LocalDateTime.parse(so.loadingDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** true, если refDate ∈ [loadingDate ; deliveryDate] (обе границы включительно) */
    private static boolean withinTransferWindow(Order order, Order.SupplierOrder so, LocalDate refDate) {
        Optional<LocalDate> ldOpt = getLoadingDate(so);
        Optional<LocalDate> ddOpt = getDeliveryDate(order);
        if (ldOpt.isEmpty() || ddOpt.isEmpty()) return false;
        LocalDate ld = ldOpt.get();
        LocalDate dd = ddOpt.get();
        return !refDate.isBefore(ld) && !refDate.isAfter(dd);
    }


    // ---------------------------------------
    // RENDER helpers
    // ---------------------------------------

    private static void appendPassLine(StringBuilder sb, SupplierOrderWithParent sop) {
        String orderDriverRaw = sop.order.driver;
        String orderDriver = showOrDash(orderDriverRaw);
        String org = firstNonBlank(
                Optional.ofNullable(sop.supplierOrder.organization).orElse(null),
                Optional.ofNullable(sop.order.organization).orElse(null),
                "—"
        );
        sb.append("   <b>- Водителю ").append(orderDriver)
                .append("</b> для заказа №").append(sop.order.getCleanOrderNumber())
                .append(" · 🏢 ").append(org).append("\n");

        String composition = Optional.ofNullable(sop.supplierOrder.productComposition).orElse("—");
        if (!composition.equals("—")) {
            String[] items = composition.split("\\r?\\n");
            for (String item : items) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    sb.append("         📦 ").append(trimmed).append("\n");
                }
            }
        } else {
            sb.append("         📦 —\n");
        }
    }

    // ---------------------------------------
    // DTO
    // ---------------------------------------

    public static class SupplierOrderWithParent {
        public final Order order;
        public final Order.SupplierOrder supplierOrder;

        public SupplierOrderWithParent(Order order, Order.SupplierOrder supplierOrder) {
            this.order = order;
            this.supplierOrder = supplierOrder;
        }
    }

    // NEW: находим перемещения для конкретного водителя на дату доставки заказа
    public static List<MovementWithParent> findMovementsForDriverOnDeliveryDate(
            List<Order> orders, LocalDate date, String driverName
    ) {
        if (orders == null || orders.isEmpty() || isBlank(driverName)) return Collections.emptyList();

        List<MovementWithParent> result = new ArrayList<>();
        for (Order order : orders) {
            if (order.movements == null || order.movements.isEmpty()) continue;

            Optional<LocalDate> ddOpt = getDeliveryDate(order);
            if (ddOpt.isEmpty() || !ddOpt.get().equals(date)) continue; // только в день доставки заказа

            for (Order.Movement mv : order.movements) {
                String mvDriver = showOrDash(mv.loadingDriver);
                if (containsName(mvDriver, driverName)) {
                    result.add(new MovementWithParent(order, mv));
                }
            }
        }
        return result;
    }

    // NEW: отрисовка одной строки перемещения
    private static void appendMovementLine(StringBuilder sb, MovementWithParent m) {
        String sender    = showOrDash(m.movement.sender);

        sb.append(" · Заказ №").append(m.order.getCleanOrderNumber()).append("\n");
        sb.append("      Забрать товар : ").append(escape(sender)).append("\n");

        String composition = Optional.ofNullable(m.movement.productComposition).orElse("—");
        if (!composition.equals("—")) {
            String[] items = composition.split("\\r?\\n");
            for (String item : items) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) sb.append("         📦 ").append(escape(trimmed)).append("\n");
            }
        } else {
            sb.append("         📦 —\n");
        }
    }


    // NEW: перемещение + родительский заказ
    public static class MovementWithParent {
        public final Order order;
        public final Order.Movement movement;

        public MovementWithParent(Order order, Order.Movement movement) {
            this.order = order;
            this.movement = movement;
        }
    }

    private static final int TG_TEXT_LIMIT = 4096;

    /** Режем по \n, стараясь не превышать maxLen.  */
    private static List<String> splitBySize(String text, int maxLen) {
        if (text == null || text.isEmpty()) return List.of("");

        // небольшой запас, чтобы не упереться в лимит из-за HTML-сущностей
        maxLen = Math.min(maxLen, TG_TEXT_LIMIT - 50);

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            if (end == text.length()) {
                chunks.add(text.substring(start));
                break;
            }

            // Ищем ближайшую "границу" строки назад
            int cut = text.lastIndexOf('\n', end);
            if (cut <= start) {
                // нет переноса — режем жестко по maxLen
                cut = end;
            }
            chunks.add(text.substring(start, cut));
            start = cut + 1; // пропускаем '\n'
        }
        return chunks.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
