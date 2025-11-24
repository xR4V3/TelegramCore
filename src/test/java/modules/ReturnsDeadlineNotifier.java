package modules;

import core.Main;
import utils.Order;
import utils.UserData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Ежедневные напоминания по возвратам:
 *  - за 2 дня до дедлайна (10 дней с даты в статусе)
 *  - в первый день просрочки
 *
 * Условия:
 *  - статус "Сдал" игнорируем
 *  - статус, который выглядит как дата dd.MM.yyyy — это "дата старта" для отсчёта 10 дней
 *
 * Отправка:
 *  - Водителю (по имени из returnDriver -> поиск пользователя)
 *  - Всем с ролью ADMIN и LOGISTIC
 */
public class ReturnsDeadlineNotifier {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int DAYS_TO_RETURN = 10;

    // Чтобы не долбить одинаковыми уведомлениями в один день, запомним, что уже отправляли.
    // Ключ: "RET:<returnNumber>:<type>:<yyyy-MM-dd>" где type=SOON|OVERDUE
    private static final Set<String> sentToday = ConcurrentHashMap.newKeySet();

    private static ScheduledExecutorService scheduler;

    /** Вызываем один раз при старте приложения */
    public static synchronized void startSchedulerAt(LocalTime runAt) {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor();

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime firstRun = now.withHour(runAt.getHour())
                .withMinute(runAt.getMinute())
                .withSecond(0).withNano(0);
        if (firstRun.isBefore(now)) firstRun = firstRun.plusDays(1);

        long initialDelay = Duration.between(now, firstRun).toMillis();
        long period = TimeUnit.DAYS.toMillis(1);

        scheduler.scheduleAtFixedRate(ReturnsDeadlineNotifier::runDailyCheck,
                initialDelay, period, TimeUnit.MILLISECONDS);
    }

    /** Можно вызвать вручную (например, после загрузки заказов) */
    public static void runDailyCheck() {
        try {
            if (OrderLoader.orders == null || OrderLoader.orders.isEmpty()) return;

            LocalDate today = LocalDate.now();

            for (Order order : OrderLoader.orders) {
                if (order == null || order.supplierOrders == null) continue;

                for (Order.SupplierOrder so : order.supplierOrders) {
                    if (so == null || so.returns == null) continue;

                    for (Order.ReturnItem r : so.returns) {
                        if (r == null) continue;

                        // Пропускаем "Сдал"
                        if (isDone(r)) continue;

                        // Статус должен быть датой старта (dd.MM.yyyy) — это вы уже проставляете в OrderLoader
                        LocalDate startDate = parseStatusDate(r.status);
                        if (startDate == null) continue;

                        LocalDate deadline = startDate.plusDays(DAYS_TO_RETURN);
                        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, deadline);

                        if (daysLeft == 2) {
                            // Напоминание "скоро дедлайн"
                            notifyAllRoles(r, so, order, "SOON", deadline, daysLeft);
                        } else if (daysLeft == -1) {
                            // Первый день просрочки
                            notifyAllRoles(r, so, order, "OVERDUE", deadline, daysLeft);
                        }
                    }
                }
            }

            // очищаем "уже отправлено сегодня" на следующий день
            cleanupSentTodayIfNewDay();
        } catch (Exception e) {
            System.err.println("ReturnsDeadlineNotifier.runDailyCheck error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== helpers =====

    private static boolean isDone(Order.ReturnItem r) {
        String st = (r == null ? null : r.status);
        return st != null && st.trim().equalsIgnoreCase("Сдал");
    }

    private static LocalDate parseStatusDate(String status) {
        if (status == null) return null;
        String s = status.trim();
        if (s.equalsIgnoreCase("Сдал")) return null;
        try {
            return LocalDate.parse(s, DF);
        } catch (Exception e) {
            return null;
        }
    }

    private static void notifyAllRoles(Order.ReturnItem r, Order.SupplierOrder so, Order order,
                                       String type, LocalDate deadline, long daysLeft) {
        String retNo = nv(r.returnNumber, "—");
        String org = nv(firstNonBlank(so.organization, order.organization), "—");
        String wh  = nv(so.supplierWarehouse, "—");
        String driverName = nv(r.returnDriver, "—");

        String key = "RET:" + retNo + ":" + type + ":" + LocalDate.now();
        if (sentToday.contains(key)) return; // уже отправляли сегодня

        String text;
        if ("SOON".equals(type)) {
            text = "⏳ <b>Скоро дедлайн по возврату</b>\n"
                    + "Возврат №: " + esc(retNo) + "\n"
                    + "Водитель: " + esc(driverName) + "\n"
                    + "Организация: " + esc(org) + "\n"
                    + "Склад поставщика: " + esc(wh) + "\n"
                    + "Дедлайн: " + deadline.format(DF) + " (осталось 2 дня)";
        } else { // OVERDUE
            text = "⚠️ <b>Возврат просрочен</b>\n"
                    + "Возврат №: " + esc(retNo) + "\n"
                    + "Водитель: " + esc(driverName) + "\n"
                    + "Организация: " + esc(org) + "\n"
                    + "Склад поставщика: " + esc(wh) + "\n"
                    + "Дедлайн был: " + deadline.format(DF);
        }

        // 1) Водителю
        UserData driver = UserData.findUserByName(driverName);
        if (driver != null) {
            Main.getInstance().sendMessage(driver.getId(), text, com.pengrad.telegrambot.model.request.ParseMode.HTML);
        }

        // 2) Всем ADMIN и LOGISTIC
        if (Main.users != null) {
            for (UserData u : Main.users) {
                String role = u.getRole();
                if (role == null) continue;
                role = role.toUpperCase();
                if ("ADMIN".equals(role) || "LOGISTIC".equals(role)) {
                    Main.getInstance().sendMessage(u.getId(), text, com.pengrad.telegrambot.model.request.ParseMode.HTML);
                }
            }
        }

        sentToday.add(key);
    }

    private static void cleanupSentTodayIfNewDay() {
        // Простая стратегия: если сет разросся, чистим по дате-ключу
        // В рамках одной сессии достаточно оставить только сегодняшние записи.
        String todayStr = LocalDate.now().toString();
        sentToday.removeIf(k -> !k.endsWith(":" + todayStr));
    }

    // utils
    private static String nv(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
