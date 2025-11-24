package modules;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import core.Main;
import modules.OrderLoader;
import ru.xr4v3.bot.events.annotations.OnCallbackQuery;
import utils.Order;
import utils.UserData;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ReportManager {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static void updateRouteStats(UserData driver, LocalDate date) {
        if (driver == null || date == null) return;

        List<Order> driverOrders = OrderLoader.orders.stream()
                .filter(o -> o.driver != null && o.driver.contains(driver.getName()))
                .filter(o -> {
                    try {
                        return o.deliveryDate != null &&
                                LocalDate.parse(o.deliveryDate.trim(), DATE_FORMATTER).equals(date);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        UserData.RouteStats.DailyStats dailyStats = driver.getRouteStats().getOrCreateDailyStats(date);

        // Сбрасываем прежние значения, чтобы не было повторного суммирования
        dailyStats.reset();

        for (Order order : driverOrders) {
            double orderValue = parseOrderValue(order);
            dailyStats.addOrder(orderValue);

            if (order.orderStatus != null &&
                    order.orderStatus.equalsIgnoreCase(utils.OrderStatus.DELIVERED.getDisplayName())) {
                dailyStats.addCompletedOrder(orderValue);
            }
        }

        UserData.saveUsersToFile();
    }

    public static void checkDailyCompletion(LocalDate date) {
        List<UserData> driversWithRouteToday = Main.users.stream()
                .filter(u -> u.getRole() != null && u.getRole().equalsIgnoreCase("DRIVER"))
                .filter(u -> u.getRoutes().containsKey(date))
                .collect(Collectors.toList());

        if (!driversWithRouteToday.isEmpty()) {
            sendDailyReport(date);
        }
    }

    public static void checkWeeklyCompletion(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            LocalDate weekStart = date.minusDays(6);
            sendWeeklyReport(weekStart, date);
        }
    }

    public static void checkMonthlyCompletion(LocalDate date) {
        LocalDate lastDayOfMonth = date.withDayOfMonth(date.lengthOfMonth());
        if (date.equals(lastDayOfMonth)) {
            LocalDate monthStart = date.withDayOfMonth(1);
            sendMonthlyReport(monthStart, date);
        }
    }

    private static void sendDailyReport(LocalDate date) {
        StringBuilder report = new StringBuilder();
        report.append("📊 Ежедневный отчет за ").append(date.format(DATE_FORMATTER)).append("\n\n");

        List<UserData> drivers = Main.users.stream()
                .filter(user -> user.getRole() != null && user.getRole().equalsIgnoreCase("DRIVER"))
                .filter(user -> user.getRoutes().containsKey(date))
                .collect(Collectors.toList());

        if (drivers.isEmpty()) {
            report.append("На эту дату маршрутов не найдено.\n");
            sendReportToAdminsAndLogistics(report.toString());
            return;
        }

        drivers.sort((d1, d2) -> {
            var s1 = d1.getRouteStats().getOrCreateDailyStats(date);
            var s2 = d2.getRouteStats().getOrCreateDailyStats(date);

            double k1 = wilsonScore(s1.getCompletedOrders(), s1.getTotalOrders());
            double k2 = wilsonScore(s2.getCompletedOrders(), s2.getTotalOrders());
            int byKpi = Double.compare(k2, k1);
            if (byKpi != 0) return byKpi;

            boolean f1 = d1.getRouteStatus(date).isFinished();
            boolean f2 = d2.getRouteStatus(date).isFinished();
            if (f1 != f2) return Boolean.compare(f2, f1);

            double r1 = s1.getCompletionRateByCount();
            double r2 = s2.getCompletionRateByCount();
            return Double.compare(r2, r1);
        });

        int totalOrdersSum = 0;
        int completedOrdersSum = 0;
        double totalValueSum = 0.0;
        double completedValueSum = 0.0;

        for (UserData driver : drivers) {
            boolean finished = driver.getRouteStatus(date).isFinished();
            UserData.RouteStats.DailyStats stats = driver.getRouteStats().getOrCreateDailyStats(date);
            report.append(generateDriverStatsString(driver.getName(), stats, finished));

            totalOrdersSum += stats.getTotalOrders();
            completedOrdersSum += stats.getCompletedOrders();
            totalValueSum += stats.getTotalOrderValue();
            completedValueSum += stats.getCompletedOrderValue();
        }

        double dayRate = totalOrdersSum == 0 ? 0 : (completedOrdersSum * 100.0) / totalOrdersSum;
        double dayKpi = wilsonScore(completedOrdersSum, totalOrdersSum) * 100.0;

        report.append("— — — — — — — — — —\n");
        report.append(String.format(
                "🧾 ИТОГО\n" +
                        "   Доставлено: %d из %d заказов\n" +
                        "   Выполнено на: %.1f%% (%.2f / %.2f)\n" +
                        "   KPI : %.1f%%\n",
                completedOrdersSum, totalOrdersSum,
                dayRate, completedValueSum, totalValueSum,
                dayKpi
        ));

        sendReportToAdminsAndLogistics(report.toString());
    }


    private static void sendWeeklyReport(LocalDate weekStart, LocalDate weekEnd) {
        StringBuilder report = new StringBuilder();
        report.append("📊 Недельный отчет за период с ")
                .append(weekStart.format(DATE_FORMATTER))
                .append(" по ")
                .append(weekEnd.format(DATE_FORMATTER))
                .append("\n\n");

        List<UserData> drivers = Main.users.stream()
                .filter(user -> user.getRole() != null && user.getRole().equalsIgnoreCase("DRIVER"))
                .collect(Collectors.toList());

        Map<String, WeeklyStats> weeklyStats = new HashMap<>();

        for (UserData driver : drivers) {
            WeeklyStats stats = new WeeklyStats();

            for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
                UserData.RouteStats.DailyStats daily = driver.getRouteStats().getDailyStats().get(date);
                if (daily != null) {
                    stats.totalOrders += daily.getTotalOrders();
                    stats.completedOrders += daily.getCompletedOrders();
                    stats.totalValue += daily.getTotalOrderValue();
                    stats.completedValue += daily.getCompletedOrderValue();
                }
            }

            weeklyStats.put(driver.getName(), stats);
        }

        List<Map.Entry<String, WeeklyStats>> sortedStats = weeklyStats.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(
                        e2.getValue().getWilsonKpi(),
                        e1.getValue().getWilsonKpi()
                ))
                .collect(Collectors.toList());

        for (Map.Entry<String, WeeklyStats> entry : sortedStats) {
            if (entry.getValue().totalOrders > 0) {
                report.append(generateWeeklyStatsString(entry.getKey(), entry.getValue()));
            }
        }

        int totalOrdersSum = 0;
        int completedOrdersSum = 0;
        double totalValueSum = 0.0;
        double completedValueSum = 0.0;

        for (WeeklyStats ws : weeklyStats.values()) {
            totalOrdersSum += ws.totalOrders;
            completedOrdersSum += ws.completedOrders;
            totalValueSum += ws.totalValue;
            completedValueSum += ws.completedValue;
        }
        double rateByCount = totalOrdersSum == 0 ? 0 : (completedOrdersSum * 100.0) / totalOrdersSum;
        double weekKpi = wilsonScore(completedOrdersSum, totalOrdersSum) * 100.0;

        report.append("— — — — — — — — — —\n");
        report.append(String.format(
                "🧾 ИТОГО (неделя)\n" +
                        "   Доставлено: %d из %d заказов\n" +
                        "   Выполнено на: %.1f%% (%.2f / %.2f)\n" +
                        "   KPI : %.1f%%\n",
                completedOrdersSum, totalOrdersSum,
                rateByCount, completedValueSum, totalValueSum,
                weekKpi
        ));

        sendReportToAdminsAndLogistics(report.toString());
    }


    private static void sendMonthlyReport(LocalDate monthStart, LocalDate monthEnd) {
        StringBuilder report = new StringBuilder();
        report.append("📊 Месячный отчет за период с ")
                .append(monthStart.format(DATE_FORMATTER))
                .append(" по ")
                .append(monthEnd.format(DATE_FORMATTER))
                .append("\n\n");

        List<UserData> drivers = Main.users.stream()
                .filter(user -> user.getRole() != null && user.getRole().equalsIgnoreCase("DRIVER"))
                .collect(Collectors.toList());

        Map<String, WeeklyStats> monthlyStats = new HashMap<>();

        for (UserData driver : drivers) {
            WeeklyStats stats = new WeeklyStats();

            for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
                UserData.RouteStats.DailyStats daily = driver.getRouteStats().getDailyStats().get(date);
                if (daily != null) {
                    stats.totalOrders += daily.getTotalOrders();
                    stats.completedOrders += daily.getCompletedOrders();
                    stats.totalValue += daily.getTotalOrderValue();
                    stats.completedValue += daily.getCompletedOrderValue();
                }
            }

            monthlyStats.put(driver.getName(), stats);
        }

        // сортируем по KPI (Wilson)
        List<Map.Entry<String, WeeklyStats>> sortedStats = monthlyStats.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(
                        e2.getValue().getWilsonKpi(),
                        e1.getValue().getWilsonKpi()
                ))
                .collect(Collectors.toList());

        for (Map.Entry<String, WeeklyStats> entry : sortedStats) {
            if (entry.getValue().totalOrders > 0) {
                report.append(generateWeeklyStatsString(entry.getKey(), entry.getValue()));
            }
        }

        int totalOrdersSum = 0;
        int completedOrdersSum = 0;
        double totalValueSum = 0.0;
        double completedValueSum = 0.0;

        for (WeeklyStats ws : monthlyStats.values()) {
            totalOrdersSum += ws.totalOrders;
            completedOrdersSum += ws.completedOrders;
            totalValueSum += ws.totalValue;
            completedValueSum += ws.completedValue;
        }
        double rateByCount = totalOrdersSum == 0 ? 0 : (completedOrdersSum * 100.0) / totalOrdersSum;
        double monthKpi = wilsonScore(completedOrdersSum, totalOrdersSum) * 100.0;

        report.append("— — — — — — — — — —\n");
        report.append(String.format(
                "🧾 ИТОГО (месяц)\n" +
                        "   Доставлено: %d из %d заказов\n" +
                        "   Выполнено на: %.1f%% (%.2f / %.2f)\n" +
                        "   KPI : %.1f%%\n",
                completedOrdersSum, totalOrdersSum,
                rateByCount, completedValueSum, totalValueSum,
                monthKpi
        ));

        sendReportToAdminsAndLogistics(report.toString());
    }


    private static String generateDriverStatsString(String driverName,
                                                    UserData.RouteStats.DailyStats stats,
                                                    boolean finished) {
        String statusText = finished ? "\uD83C\uDFC1 завершен" : "⏳ не завершен";
        double tv = stats.getTotalOrderValue();
        double cv = stats.getCompletedOrderValue();
        double kpi = wilsonScore(stats.getCompletedOrders(), stats.getTotalOrders()) * 100.0;

        return String.format(
                "🚚 %s (%s)\n" +
                        "   Доставлено: %d из %d заказов\n" +
                        "   Выполнено на: %.1f%% (%.2f / %.2f)\n" +
                        "   KPI : %.1f%%\n",
                driverName,
                statusText,
                stats.getCompletedOrders(),
                stats.getTotalOrders(),
                stats.getCompletionRateByCount(),
                cv, tv,
                kpi
        );
    }


    private static String generateWeeklyStatsString(String driverName, WeeklyStats stats) {
        double tv = stats.totalValue;
        double cv = stats.completedValue;
        double kpi = stats.getWilsonKpi() * 100.0;

        return String.format(
                "🚚 %s\n" +
                        "   Доставлено: %d из %d заказов\n" +
                        "   Выполнено на: %.1f%% (%.2f / %.2f)\n" +
                        "   KPI : %.1f%%\n",
                driverName,
                stats.completedOrders,
                stats.totalOrders,
                stats.getCompletionRateByCount(),
                cv, tv,
                kpi
        );
    }


    private static void sendReportToAdminsAndLogistics(String report) {
        for (UserData user : Main.users) {
            if (user.getRole() != null) {
                String role = user.getRole().toUpperCase();
                if (role.equals("ADMIN") || role.equals("LOGISTIC")) {
                    Main.getInstance().sendMessage(user.getId(), report);
                }
            }
        }
    }

    private static double parseOrderValue(Order order) {
        try {
            if (order.orderTotal != null) {
                return Double.parseDouble(order.orderTotal.replace(",", ".").replaceAll("[^0-9.]", ""));
            }
        } catch (Exception e) {
            // ignore, вернём 0
        }
        return 0.0;
    }

    // Класс для статистики за период
    private static class WeeklyStats {
        int totalOrders = 0;
        int completedOrders = 0;
        double totalValue = 0;
        double completedValue = 0;

        double getCompletionRateByCount() {
            return totalOrders == 0 ? 0 : (completedOrders * 100.0) / totalOrders;
        }

        double getWilsonKpi() {
            return wilsonScore(completedOrders, totalOrders);
        }
    }


    public static void startReportScheduler() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduleNextRunAt0800NextDay(scheduler);
    }

    private static void scheduleNextRunAt0800NextDay(ScheduledExecutorService scheduler) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime next = now.plusDays(1)
                .withHour(8).withMinute(0).withSecond(0).withNano(0);

        long delayMs = java.time.Duration.between(now, next).toMillis();

        scheduler.schedule(() -> {
            LocalDate reportDate = LocalDate.now().minusDays(1);

            ReportManager.checkDailyCompletion(reportDate);
            ReportManager.checkWeeklyCompletion(reportDate);
            ReportManager.checkMonthlyCompletion(reportDate);

            scheduleNextRunAt0800NextDay(scheduler);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private static String buildDailyReportForRole(LocalDate date, String role) {
        StringBuilder report = new StringBuilder();
        report.append("📊 Ежедневный отчет за ")
                .append(date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                .append("\n\n");

        if ("DRIVER".equalsIgnoreCase(role)) {
            List<UserData> drivers = Main.users.stream()
                    .filter(u -> u.getRole() != null && u.getRole().equalsIgnoreCase("DRIVER"))
                    .filter(u -> u.getRoutes().containsKey(date))
                    .collect(Collectors.toList());

            if (drivers.isEmpty()) {
                report.append("На эту дату маршрутов не найдено.\n");
                return report.toString();
            }

            drivers.sort((d1, d2) -> {
                var s1 = d1.getRouteStats().getOrCreateDailyStats(date);
                var s2 = d2.getRouteStats().getOrCreateDailyStats(date);

                double k1 = wilsonScore(s1.getCompletedOrders(), s1.getTotalOrders());
                double k2 = wilsonScore(s2.getCompletedOrders(), s2.getTotalOrders());
                int byKpi = Double.compare(k2, k1);
                if (byKpi != 0) return byKpi;

                boolean f1 = d1.getRouteStatus(date).isFinished();
                boolean f2 = d2.getRouteStatus(date).isFinished();
                if (f1 != f2) return Boolean.compare(f2, f1);

                return Double.compare(s2.getCompletionRateByCount(), s1.getCompletionRateByCount());
            });

            int totalOrdersSum = 0, completedOrdersSum = 0;
            double totalValueSum = 0.0, completedValueSum = 0.0;

            for (UserData d : drivers) {
                boolean finished = d.getRouteStatus(date).isFinished();
                UserData.RouteStats.DailyStats s = d.getRouteStats().getOrCreateDailyStats(date);

                String statusText = finished ? "\uD83C\uDFC1 завершен" : "⏳ не завершен";
                double kpi = wilsonScore(s.getCompletedOrders(), s.getTotalOrders()) * 100.0;

                report.append(String.format(
                        "🚚 %s (%s)\n" +
                                "   Доставлено: %d из %d заказов\n" +
                                "   Выполнено на: %.1f%% (%.2f / %.2f)\n" +
                                "   KPI : %.1f%%\n",
                        d.getName(),
                        statusText,
                        s.getCompletedOrders(), s.getTotalOrders(),
                        s.getCompletionRateByCount(),
                        s.getCompletedOrderValue(), s.getTotalOrderValue(),
                        kpi
                ));

                totalOrdersSum += s.getTotalOrders();
                completedOrdersSum += s.getCompletedOrders();
                totalValueSum += s.getTotalOrderValue();
                completedValueSum += s.getCompletedOrderValue();
            }

            double dayRateByCount = totalOrdersSum == 0 ? 0 : (completedOrdersSum * 100.0) / totalOrdersSum;
            double dayKpi = wilsonScore(completedOrdersSum, totalOrdersSum) * 100.0;

            report.append("— — — — — — — — — —\n");
            report.append(String.format(
                    "🧾 ИТОГО\n" +
                            "   Доставлено: %d из %d заказов\n" +
                            "   Выполнено на: %.1f%% (%.2f / %.2f)\n" +
                            "   KPI : %.1f%%\n",
                    completedOrdersSum, totalOrdersSum,
                    dayRateByCount, completedValueSum, totalValueSum,
                    dayKpi
            ));
            return report.toString();
        }

        if ("MANAGER".equalsIgnoreCase(role)) {
            // оставляем существующую логику менеджеров без изменений
            DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
            StringBuilder sb = new StringBuilder();
            sb.append("👔 Ежедневный отчет (менеджеры) за ").append(date.format(df)).append("\n\n");

            List<UserData> managers = Main.users.stream()
                    .filter(u -> u.getRole()!=null && u.getRole().equalsIgnoreCase("MANAGER"))
                    .collect(Collectors.toList());

            managers.sort((a,b) -> {
                var ma = a.getManagerStats().getDaily().get(date);
                var mb = b.getManagerStats().getDaily().get(date);
                int ra = (ma==null?0:ma.getResponses());
                int rb = (mb==null?0:mb.getResponses());
                return Integer.compare(rb, ra);
            });

            int totalAccepted = 0, totalRejected = 0, totalResponses = 0;
            long totalResponseMs = 0;

            for (UserData m : managers) {
                var s = m.getManagerStats().getDaily().get(date);
                int acc = s==null?0:s.getAccepted();
                int rej = s==null?0:s.getRejected();
                int resp = s==null?0:s.getResponses();
                double avgMin = s==null?0:s.getAvgResponseMinutes();

                if (acc==0 && rej==0) continue;

                sb.append(String.format(
                        "👤 %s\n" +
                                "   Принято: %d, Отклонено: %d, Обработано: %d\n" +
                                "   Среднее время ответа: %.1f мин\n\n",
                        m.getName(), acc, rej, resp, avgMin
                ));

                totalAccepted += acc;
                totalRejected += rej;
                totalResponses += resp;
                totalResponseMs += (s==null?0:s.getTotalResponseMs());
            }

            double avgAll = totalResponses==0 ? 0 : (totalResponseMs/60000.0)/totalResponses;

            sb.append("— — — — — — — — — —\n");
            sb.append(String.format(
                    "🧾 ИТОГО\n" +
                            "   Принято: %d, Отклонено: %d, Обработано: %d\n" +
                            "   Среднее время ответа: %.1f мин\n",
                    totalAccepted, totalRejected, totalResponses, avgAll
            ));

            return sb.toString();
        }

        return report.toString();
    }

    private static String buildWeeklyReportForRole(LocalDate start, LocalDate end, String role) {
        StringBuilder report = new StringBuilder();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        report.append("📊 Недельный отчет за период с ")
                .append(start.format(df))
                .append(" по ")
                .append(end.format(df))
                .append("\n\n");

        if ("DRIVER".equalsIgnoreCase(role)) {
            class WeeklyAgg {
                int totalOrders; int completedOrders;
                double totalValue; double completedValue;
                double rate() { return totalOrders==0 ? 0 : (completedOrders*100.0)/totalOrders; }
                double kpi()  { return wilsonScore(completedOrders, totalOrders); }
            }

            Map<UserData, WeeklyAgg> map = new HashMap<>();

            List<UserData> drivers = Main.users.stream()
                    .filter(u -> u.getRole()!=null && u.getRole().equalsIgnoreCase("DRIVER"))
                    .collect(Collectors.toList());

            for (UserData d : drivers) {
                WeeklyAgg ws = new WeeklyAgg();
                for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                    UserData.RouteStats.DailyStats s = d.getRouteStats().getDailyStats().get(day);
                    if (s != null) {
                        ws.totalOrders += s.getTotalOrders();
                        ws.completedOrders += s.getCompletedOrders();
                        ws.totalValue += s.getTotalOrderValue();
                        ws.completedValue += s.getCompletedOrderValue();
                    }
                }
                map.put(d, ws);
            }

            List<Map.Entry<UserData, WeeklyAgg>> sorted = map.entrySet().stream()
                    .sorted((a,b)->Double.compare(b.getValue().kpi(), a.getValue().kpi()))
                    .collect(Collectors.toList());

            int TO=0, CO=0; double TV=0, CV=0;

            for (var e : sorted) {
                WeeklyAgg s = e.getValue();
                if (s.totalOrders==0) continue;
                report.append(String.format(
                        "🚚 %s\n" +
                                "   Доставлено: %d из %d заказов\n" +
                                "   Выполнено на: %.1f%% (%.2f / %.2f)\n" +
                                "   KPI : %.1f%%\n",
                        e.getKey().getName(),
                        s.completedOrders, s.totalOrders,
                        s.rate(), s.completedValue, s.totalValue,
                        s.kpi() * 100.0
                ));
                TO+=s.totalOrders; CO+=s.completedOrders; TV+=s.totalValue; CV+=s.completedValue;
            }
            double rateByCount = TO==0?0:(CO*100.0)/TO;
            double totalKpi = wilsonScore(CO, TO) * 100.0;

            report.append("— — — — — — — — — —\n");
            report.append(String.format(
                    "🧾 ИТОГО (неделя)\n" +
                            "   Доставлено: %d из %d заказов\n" +
                            "   Выполнено на: %.1f%% (%.2f / %.2f)\n" +
                            "   KPI : %.1f%%\n",
                    CO, TO, rateByCount, CV, TV, totalKpi
            ));
            return report.toString();
        }


        if ("MANAGER".equalsIgnoreCase(role)) {
            // существующая логика менеджеров без изменений
            List<UserData> managers = Main.users.stream()
                    .filter(u -> u.getRole()!=null && u.getRole().equalsIgnoreCase("MANAGER"))
                    .collect(Collectors.toList());

            class Agg { int acc, rej, resp; long totalMs;
                double avg(){ return resp==0?0:(totalMs/60000.0)/resp; } }
            Map<UserData, Agg> map = new HashMap<>();

            for (UserData m : managers) {
                Agg a = new Agg();
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    var s = m.getManagerStats().getDaily().get(d);
                    if (s != null) {
                        a.acc += s.getAccepted();
                        a.rej += s.getRejected();
                        a.resp += s.getResponses();
                        a.totalMs += s.getTotalResponseMs();
                    }
                }
                map.put(m, a);
            }

            List<Map.Entry<UserData, Agg>> sorted = map.entrySet().stream()
                    .sorted((a,b)->Integer.compare(b.getValue().resp, a.getValue().resp))
                    .toList();

            int Tacc=0, Trej=0, Tresp=0; long Tms=0;

            for (var e : sorted) {
                Agg a = e.getValue();
                if (a.resp==0) continue;
                report.append(String.format(
                        "👤 %s\n   Принято: %d, Отклонено: %d, Обработано: %d\n   Среднее время ответа: %.1f мин\n\n",
                        e.getKey().getName(), a.acc, a.rej, a.resp, a.avg()
                ));
                Tacc+=a.acc; Trej+=a.rej; Tresp+=a.resp; Tms+=a.totalMs;
            }

            double avgAll = Tresp==0?0:(Tms/60000.0)/Tresp;
            report.append("— — — — — — — — — —\n");
            report.append(String.format("🧾 ИТОГО (неделя)\n   Принято: %d, Отклонено: %d, Обработано: %d\n   Среднее время ответа: %.1f мин\n",
                    Tacc, Trej, Tresp, avgAll));
            return report.toString();
        }

        return "Неверная роль.";
    }


    private static String buildMonthlyReportForRole(LocalDate start, LocalDate end, String role) {
        StringBuilder report = new StringBuilder();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        report.append("📊 Месячный отчет за период с ")
                .append(start.format(df))
                .append(" по ")
                .append(end.format(df))
                .append("\n\n");

        if ("DRIVER".equalsIgnoreCase(role)) {
            return buildWeeklyReportForRole(start, end, role)
                    .replace("(неделя)", "(месяц)")
                    .replace("Недельный", "Месячный");
        }

        if ("MANAGER".equalsIgnoreCase(role)) {
            return buildWeeklyReportForRole(start, end, role)
                    .replace("(неделя)", "(месяц)")
                    .replace("Недельный", "Месячный");
        }

        return "Неверная роль.";
    }


    @OnCallbackQuery
    public static void handleReportCallback(Update update){
        if (update.callbackQuery() != null && update.callbackQuery().data() != null) {
            String data = update.callbackQuery().data();

            // Открыть список разделов -> показываем кнопку "Отчёты"
            if ("other:open".equals(data)) {
                List<List<InlineKeyboardButton>> kb = new ArrayList<>();
                kb.add(Collections.singletonList(
                        new InlineKeyboardButton("📊 Отчёты").callbackData("reports:open")
                ));
                kb.add(Collections.singletonList(
                        new InlineKeyboardButton("\uD83D\uDCB0 Зарплаты").callbackData("payroll:open")
                ));
                Main.getInstance().sendInlineKeyboard(
                        update.callbackQuery().message().chat().id(),
                        kb,
                        "Выберите раздел:"
                );
                return;
            }

            // Нажали "Отчёты" -> выбор сущности отчёта
            if ("reports:open".equals(data)) {
                List<List<InlineKeyboardButton>> kb = new ArrayList<>();
                kb.add(Arrays.asList(
                        new InlineKeyboardButton("Водители").callbackData("report:role:DRIVER"),
                        new InlineKeyboardButton("Менеджеры").callbackData("report:role:MANAGER")
                ));
                Main.getInstance().sendInlineKeyboard(
                        update.callbackQuery().message().chat().id(),
                        kb,
                        "Отчет:"
                );
            }

            // Шаг 2: выбор роли -> показать периоды
            if (data.startsWith("report:role:")) {
                String role = data.substring("report:role:".length()); // DRIVER | MANAGER

                List<List<InlineKeyboardButton>> kb = new ArrayList<>();
                kb.add(Collections.singletonList(
                        new InlineKeyboardButton("Отчет за сегодня").callbackData("report:period:TODAY:" + role)
                ));
                kb.add(Collections.singletonList(
                        new InlineKeyboardButton("Отчет за вчера").callbackData("report:period:DAY:" + role)
                ));
                kb.add(Collections.singletonList(
                        new InlineKeyboardButton("Отчет за неделю").callbackData("report:period:WEEK:" + role)
                ));
                kb.add(Collections.singletonList(
                        new InlineKeyboardButton("Отчет за месяц").callbackData("report:period:MONTH:" + role)
                ));

                Long chatId = update.callbackQuery().message().chat().id();
                Main.getInstance().sendInlineKeyboard(chatId, kb, "Выберите период:");
                return;
            }

            // Шаг 3: выбор периода -> сгенерировать и выдать отчет
            if (data.startsWith("report:period:")) {
                String[] parts = data.split(":"); // ["report","period","TODAY|DAY|WEEK|MONTH","DRIVER|MANAGER"]
                if (parts.length >= 4) {
                    String period = parts[2];
                    String role = parts[3];
                    Long chatId = update.callbackQuery().message().chat().id();

                    String report;
                    LocalDate today = LocalDate.now();

                    switch (period) {
                        case "TODAY" -> {
                            LocalDate date = today; // сегодня
                            report = buildDailyReportForRole(date, role);
                        }
                        case "DAY" -> {
                            LocalDate date = today.minusDays(1); // вчера
                            report = buildDailyReportForRole(date, role);
                        }
                        case "WEEK" -> {
                            LocalDate end = today.minusDays(1);
                            LocalDate start = end.minusDays(6);
                            report = buildWeeklyReportForRole(start, end, role);
                        }
                        case "MONTH" -> {
                            LocalDate end = today.minusDays(1);
                            LocalDate start = end.withDayOfMonth(1);
                            report = buildMonthlyReportForRole(start, end, role);
                        }
                        default -> report = "Неверный период.";
                    }

                    Main.getInstance().sendMessage(chatId, report);
                    return;
                }
            }
        }
    }

    private static final double DEFAULT_Z = 1.96; // 95% CI

    private static double wilsonScore(int success, int total) {
        if (total <= 0) return 0.0;
        double z = DEFAULT_Z;
        double p = success / (double) total;
        double z2 = z * z;
        double denom = 1 + z2 / total;
        double center = p + z2 / (2.0 * total);
        double margin = z * Math.sqrt((p * (1 - p) + z2 / (4.0 * total)) / total);
        return (center - margin) / denom; // lower bound
    }


}
