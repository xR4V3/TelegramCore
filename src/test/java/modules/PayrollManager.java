package modules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.ParseMode;
import core.Main;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;
import ru.xr4v3.bot.events.annotations.OnCallbackQuery;
import utils.UserData;

public class PayrollManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter YM_FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YM_SHOW_FMT = DateTimeFormatter.ofPattern("MM.yyyy");
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd.MM");

    /**
     * TEMP stores snapshots for the whole month, broken into ISO weeks (Mon-Sun) and days inside them.
     * Key: driverId -> ("yyyy-MM") -> MonthBlock
     */
    private static Map<Long, Map<String, MonthBlock>> TEMP = null;

    public static void showMySettlementText(Long chatId, Long userId, YearMonth unused) {
        openMyMenuFromButton(chatId, userId);
    }

    public static void openMyMenuFromButton(Long chatId, Long userId) {
        if (chatId == null || userId == null) return;
        UserData u = UserData.findUserById(userId);
        if (u == null) {
            Main.getInstance().sendMessage(chatId, "⚠️ Пользователь не найден.");
            return;
        }
        if (u.getRole() == null || !u.getRole().equalsIgnoreCase("DRIVER")) {
            Main.getInstance().sendMessage(chatId, "⚠️ Доступно только водителям.");
            return;
        }
        List<List<InlineKeyboardButton>> kb = new ArrayList<>();
        kb.add(Arrays.asList(
                new InlineKeyboardButton("Текущее").callbackData("payroll:my:period:TODAY"),
                new InlineKeyboardButton("Неделя").callbackData("payroll:my:period:WEEK")
        ));
        Main.getInstance().sendInlineKeyboard(chatId, kb, "💼 Зарплаты — выберите период:");
    }

    public static void openMenuFromButton(Long chatId, Long userId) {
        openAdminMenu(chatId, userId);
    }

    @OnCallbackQuery
    public static void handlePayrollCallbacks(Update update) {
        if (update == null || update.callbackQuery() == null) return;
        String data = update.callbackQuery().data();
        Long chatId = update.callbackQuery().message().chat().id();
        Integer msgId = update.callbackQuery().message().messageId();
        Long userId = update.callbackQuery().from().id();
        if (data == null) return;

        if (data.equals("payroll:open")) {
            openAdminMenu(chatId, userId, msgId);
            return;
        }

        if (data.equals("payroll:my")) {
            openMyMenuFromButton(chatId, userId);
            return;
        }

        if (data.startsWith("payroll:my:period:")) {
            String period = data.substring("payroll:my:period:".length());
            UserData u = UserData.findUserById(userId);
            if (u == null || u.getRole() == null || !u.getRole().equalsIgnoreCase("DRIVER")) {
                Main.getInstance().editMessage(chatId, msgId, "⚠️ Доступно только водителям.");
                return;
            }
            YearMonth ym = resolveYearMonthByPeriod(period);
            Optional<DriverMonthSettlement> opt = loadSettlementForDriver(u, ym);
            ensureTempLoaded();
            if (opt.isPresent()) updateTemp(u.getId(), ym, opt.get());
            if (opt.isEmpty()) {
                List<List<InlineKeyboardButton>> kbEmpty = new ArrayList<>();
                kbEmpty.add(Arrays.asList(
                        new InlineKeyboardButton("Текущее").callbackData("payroll:my:period:TODAY"),
                        new InlineKeyboardButton("Неделя").callbackData("payroll:my:period:WEEK")
                ));
                Main.getInstance().editMessage(chatId, msgId, "⚠️ Нет данных за " + ym.format(YM_SHOW_FMT) + ".", kbEmpty);
                return;
            }

            if ("WEEK".equalsIgnoreCase(period)) {
                String text = buildWeekText(u.getId(), ym, u.getName());
                List<List<InlineKeyboardButton>> kb = new ArrayList<>();
                kb.add(Arrays.asList(
                        new InlineKeyboardButton("Текущее").callbackData("payroll:my:period:TODAY"),
                        new InlineKeyboardButton("Неделя").callbackData("payroll:my:period:WEEK")
                ));
                Main.getInstance().editMessage(chatId, msgId, text, ParseMode.HTML, kb);
                return;
            }

            PeriodSlice slice = computeSlice(u.getId(), ym, period);
            String text = buildPeriodText(opt.get(), ym, slice, periodLabel(period), u.getName());
            List<List<InlineKeyboardButton>> kb = new ArrayList<>();
            kb.add(Arrays.asList(
                    new InlineKeyboardButton("Текущее").callbackData("payroll:my:period:TODAY"),
                    new InlineKeyboardButton("Неделя").callbackData("payroll:my:period:WEEK")
            ));
            Main.getInstance().editMessage(chatId, msgId, text, ParseMode.HTML, kb);
            return;
        }

        if (data.startsWith("payroll:drv:")) {
            String[] p = data.split(":");
            Long drvId = parseLongSafe(p[2]);
            if (drvId == null) return;
            UserData u = UserData.findUserById(userId);
            if (u == null || u.getRole() == null || (!u.getRole().equalsIgnoreCase("ADMIN") && !u.getRole().equalsIgnoreCase("LOGISTIC"))) {
                Main.getInstance().editMessage(chatId, msgId, "⚠️ Недостаточно прав.");
                return;
            }
            showDriverPeriodScreen(chatId, msgId, drvId, "TODAY");
            return;
        }

        if (data.startsWith("payroll:drvperiod:")) {
            String[] p = data.split(":");
            if (p.length < 4) return;
            Long drvId = parseLongSafe(p[2]);
            String period = p[3];
            showDriverPeriodScreen(chatId, msgId, drvId, period);
            return;
        }

        if (data.startsWith("payroll:send:")) {
            String[] p = data.split(":");
            Long drvId = parseLongSafe(p[2]);
            if (drvId == null) return;
            UserData sender = UserData.findUserById(userId);
            UserData driver = UserData.findUserById(drvId);
            if (driver == null) return;
            String period = defaultBroadcastPeriod();
            YearMonth ym = resolveYearMonthByPeriod(period);
            Optional<DriverMonthSettlement> opt = loadSettlementForDriver(driver, ym);
            ensureTempLoaded();
            if (opt.isPresent()) updateTemp(driver.getId(), ym, opt.get());
            if (opt.isPresent()) {
                if ("WEEK".equalsIgnoreCase(period)) {
                    String text = buildWeekText(driver.getId(), ym, driver.getName());
                    Main.getInstance().sendMessage(driver.getId(), text, ParseMode.HTML);
                } else {
                    PeriodSlice slice = computeSlice(driver.getId(), ym, period);
                    String text = buildPeriodText(opt.get(), ym, slice, periodLabel(period), driver.getName());
                    Main.getInstance().sendMessage(driver.getId(), text, ParseMode.HTML);
                }
                if (sender != null) {
                    Main.getInstance().sendMessage(sender.getId(), "✅ Отчёт (" + periodLabel(period) + ") отправлен: " + safe(driver.getName()) + ".");
                }
            } else if (sender != null) {
                Main.getInstance().sendMessage(sender.getId(), "⚠️ Нет данных для отправки.");
            }
            return;
        }

        if (data.equals("payroll:sendall")) {
            String period = defaultBroadcastPeriod();
            UserData sender = UserData.findUserById(userId);
            List<UserData> drivers = Main.users.stream().filter(x -> x.getRole() != null && x.getRole().equalsIgnoreCase("DRIVER")).collect(Collectors.toList());
            YearMonth ym = resolveYearMonthByPeriod(period);
            ensureTempLoaded();
            int sent = 0, skipped = 0;
            for (UserData d : drivers) {
                Optional<DriverMonthSettlement> opt = loadSettlementForDriver(d, ym);
                if (opt.isPresent()) {
                    updateTemp(d.getId(), ym, opt.get());
                    String text;
                    if ("WEEK".equalsIgnoreCase(period)) {
                        text = buildWeekText(d.getId(), ym, d.getName());
                    } else {
                        PeriodSlice slice = computeSlice(d.getId(), ym, period);
                        text = buildPeriodText(opt.get(), ym, slice, periodLabel(period), d.getName());
                    }
                    Main.getInstance().sendMessage(d.getId(), text, ParseMode.HTML);
                    sent++;
                } else {
                    skipped++;
                }
            }
            if (sender != null) {
                Main.getInstance().sendMessage(sender.getId(), "📨 Отправлено (" + periodLabel(period) + "): " + sent + ", пропущено: " + skipped + ".");
            }
        }
    }

    private static void showDriverPeriodScreen(Long chatId, Integer msgId, Long drvId, String period) {
        if (drvId == null) return;
        UserData driver = UserData.findUserById(drvId);
        if (driver == null) {
            Main.getInstance().editMessage(chatId, msgId, "⚠️ Водитель не найден.");
            return;
        }
        YearMonth ym = resolveYearMonthByPeriod(period);
        Optional<DriverMonthSettlement> opt = loadSettlementForDriver(driver, ym);
        ensureTempLoaded();
        if (opt.isPresent()) updateTemp(driver.getId(), ym, opt.get());
        if (opt.isEmpty()) {
            List<List<InlineKeyboardButton>> list = new ArrayList<>();
            list.add(Collections.singletonList(new InlineKeyboardButton("⬅️ Назад").callbackData("payroll:open")));
            Main.getInstance().editMessage(chatId, msgId, "⚠️ Нет данных по " + safe(driver.getName()) + " за " + ym.format(YM_SHOW_FMT) + ".", list);
            return;
        }

        String text;
        if ("WEEK".equalsIgnoreCase(period)) {
            text = buildWeekText(driver.getId(), ym, driver.getName());
        } else {
            PeriodSlice slice = computeSlice(driver.getId(), ym, period);
            text = buildPeriodText(opt.get(), ym, slice, periodLabel(period), driver.getName());
        }

        List<List<InlineKeyboardButton>> kb = new ArrayList<>();
        kb.add(Arrays.asList(
                new InlineKeyboardButton("Текущее").callbackData("payroll:drvperiod:" + drvId + ":TODAY"),
                new InlineKeyboardButton("Неделя").callbackData("payroll:drvperiod:" + drvId + ":WEEK")
        ));
        kb.add(Collections.singletonList(new InlineKeyboardButton("📨 Отправить").callbackData("payroll:send:" + drvId)));
        kb.add(Collections.singletonList(new InlineKeyboardButton("⬅️ Назад").callbackData("payroll:open")));
        Main.getInstance().editMessage(chatId, msgId, text, ParseMode.HTML, kb);
    }

    private static void openAdminMenu(Long chatId, Long userId) {
        UserData u = UserData.findUserById(userId);
        if (u == null || u.getRole() == null || (!u.getRole().equalsIgnoreCase("ADMIN") && !u.getRole().equalsIgnoreCase("LOGISTIC"))) {
            Main.getInstance().sendMessage(chatId, "⚠️ Недостаточно прав.");
            return;
        }
        // прогрев TEMP, чтобы в недельных/дневных отчётах были все водители
        warmupTempForMonth(YearMonth.now());

        List<UserData> drivers = Main.users.stream()
                .filter(x -> x.getRole() != null && x.getRole().equalsIgnoreCase("DRIVER"))
                .sorted(Comparator.comparing(UserData::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
        List<List<InlineKeyboardButton>> kb = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (UserData d : drivers) {
            String name = (d.getName() == null) ? ("ID " + d.getId()) : d.getName();
            row.add(new InlineKeyboardButton("🚚 " + name).callbackData("payroll:drv:" + d.getId()));
            if (row.size() == 2) {
                kb.add(new ArrayList<>(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) kb.add(new ArrayList<>(row));
        kb.add(Collections.singletonList(new InlineKeyboardButton("📨 Отправить всем").callbackData("payroll:sendall")));
        kb.add(Collections.singletonList(new InlineKeyboardButton("⬅️ Назад").callbackData("other:open")));
        Main.getInstance().sendInlineKeyboard(chatId, kb, "💼 Зарплаты — выберите водителя:");
    }

    private static void openAdminMenu(Long chatId, Long userId, Integer editMsgId) {
        UserData u = UserData.findUserById(userId);
        if (u == null || u.getRole() == null || (!u.getRole().equalsIgnoreCase("ADMIN") && !u.getRole().equalsIgnoreCase("LOGISTIC"))) {
            Main.getInstance().sendMessage(chatId, "⚠️ Недостаточно прав.");
            return;
        }
        // прогрев TEMP перед показом
        warmupTempForMonth(YearMonth.now());

        List<UserData> drivers = Main.users.stream()
                .filter(x -> x.getRole() != null && x.getRole().equalsIgnoreCase("DRIVER"))
                .sorted(Comparator.comparing(UserData::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
        List<List<InlineKeyboardButton>> kb = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (UserData d : drivers) {
            String name = (d.getName() == null) ? ("ID " + d.getId()) : d.getName();
            row.add(new InlineKeyboardButton("🚚 " + name).callbackData("payroll:drv:" + d.getId()));
            if (row.size() == 2) {
                kb.add(new ArrayList<>(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) kb.add(new ArrayList<>(row));
        kb.add(Collections.singletonList(new InlineKeyboardButton("📨 Отправить всем").callbackData("payroll:sendall")));
        kb.add(Collections.singletonList(new InlineKeyboardButton("⬅️ Назад").callbackData("other:open")));
        Main.getInstance().editMessage(chatId, editMsgId, "💼 Зарплаты — выберите водителя:", kb);
    }

    private static String defaultBroadcastPeriod() {
        DayOfWeek dow = LocalDate.now().getDayOfWeek();
        return (dow == DayOfWeek.MONDAY) ? "WEEK" : "TODAY";
    }

    private static YearMonth resolveYearMonthByPeriod(String period) {
        LocalDate today = LocalDate.now();
        if ("DAY".equalsIgnoreCase(period)) return YearMonth.from(today.minusDays(1L));
        return YearMonth.from(today);
    }

    private static String periodLabel(String p) {
        if (p == null) return "Текущее";
        switch (p.toUpperCase()) {
            case "WEEK": return "За неделю";
            case "DAY":  return "Вчера";
            default:     return "Текущее"; // TODAY и прочее
        }
    }

    // Вызвать при загрузке заказов: подтянуть свежие суммы всем водителям в TEMP "на сейчас"
    public static void refreshAllNow() {
        ensureTempLoaded();
        YearMonth ym = YearMonth.now();

        List<UserData> drivers = Main.users == null ? List.of() :
                Main.users.stream()
                        .filter(u -> u != null && "DRIVER".equalsIgnoreCase(safe(u.getRole())))
                        .collect(Collectors.toList());

        for (UserData d : drivers) {
            try {
                loadSettlementForDriver(d, ym).ifPresent(s -> upsertTodaySimple(d.getId(), ym, s));
            } catch (Exception ignore) {}
        }
        persistTemp();
    }

    /**
     * Простейшая запись снапшота за сегодня:
     * - если нет блока на месяц/день — создаём,
     * - dayStart = today = текущие суммы (без «вчера», без сложных баз),
     * - сетка недели: кладём сегодня; mondayStart = текущие суммы, если не было.
     */
    private static void upsertTodaySimple(Long driverId, YearMonth ym, DriverMonthSettlement s) {
        MonthBlock mb = TEMP.computeIfAbsent(driverId, k -> new HashMap<>())
                .computeIfAbsent(ym.toString(), k -> new MonthBlock(ym));

        LocalDate today = LocalDate.now();
        String todayStr = today.format(D_FMT);
        Totals cur = Totals.of(s);

        // если день поменялся или ещё не инициализирован — сразу создаём "старт" и "сегодня" одинаковыми
        if (mb.today == null || !todayStr.equals(safe(mb.today.date))) {
            mb.dayStart = new DatedTotals(todayStr, cur);
            mb.today = new DatedTotals(todayStr, cur);
            mb.yesterday = null;
            mb.yesterdayPrev = null;
        } else {
            // тот же день — просто обновим today (старт не двигаем)
            mb.today.totals = cur;
        }

        // Неделя: Mon..Sun
        int wToday = weekOfMonth(today);
        WeekBlock wbToday = mb.weeks.computeIfAbsent(wToday, __ -> new WeekBlock(mondayOf(today)));
        if (wbToday.mondayStart == null) {
            wbToday.mondayStart = cur;
        }
        wbToday.days.put(today.getDayOfWeek(), cur);
    }


    private static Optional<DriverMonthSettlement> loadSettlementForDriver(UserData driver, YearMonth ym) {
        Path botDir = Path.of("").toAbsolutePath();
        Path parent = (botDir.getParent() != null) ? botDir.getParent() : botDir;
        Path dataDir = parent.resolve("Водители");
        if (!Files.isDirectory(dataDir)) return Optional.empty();
        Path file = dataDir.resolve(ym.format(YM_FILE_FMT) + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            JsonNode root = MAPPER.readTree(file.toFile());
            JsonNode arr = root.get("Водители");
            if (arr == null || !arr.isArray()) return Optional.empty();
            for (JsonNode n : arr) {
                String drv = jstr(n, "Водитель");
                if (matchesDriver(drv, driver)) {
                    DriverMonthSettlement s = new DriverMonthSettlement();
                    s.driverName = drv;
                    s.accruals = jnum(n, "Начисления");
                    s.incasso = jnum(n, "Инкассация");
                    s.purchase = jnum(n, "Закупка");
                    s.payout = jnum(n, "Выплата");
                    s.transfer = jnum(n, "Перемещение");
                    s.closing = jnum(n, "Конечный остаток");
                    s.opening = jnum(n, "Начальный остаток");
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static boolean matchesDriver(String sourceName, UserData driver) {
        String src = normalizeName(safe(sourceName));
        String usr = normalizeName(safe(driver.getName()));

        if (src.isBlank() || usr.isBlank()) return false;
        if (src.contains(usr)) return true; // точное совпадение после нормализации

        // сравнение по токенам без учёта порядка (максимально терпимое)
        Set<String> srcTokens = nameTokens(src);
        Set<String> usrTokens = nameTokens(usr);

        if (!usrTokens.isEmpty() && srcTokens.containsAll(usrTokens)) return true;   // все токены юзера есть в источнике
        if (!srcTokens.isEmpty() && usrTokens.containsAll(srcTokens)) return true;   // и наоборот (редкий случай)

        // фамилия + имя/инициал (например: "рыбин максим" vs "рыбин м.")
        // берём первые два самых "длинных" токена из каждого
        List<String> srcList = new ArrayList<>(srcTokens);
        srcList.sort((a,b) -> Integer.compare(b.length(), a.length()));
        List<String> usrList = new ArrayList<>(usrTokens);
        usrList.sort((a,b) -> Integer.compare(b.length(), a.length()));

        if (!srcList.isEmpty() && !usrList.isEmpty()) {
            String s1 = srcList.get(0), u1 = usrList.get(0); // обычно фамилии
            boolean surnameMatch = s1.equals(u1);
            boolean nameOrInitialMatch = false;
            if (srcList.size() > 1 && usrList.size() > 1) {
                String s2 = srcList.get(1), u2 = usrList.get(1);
                nameOrInitialMatch = s2.equals(u2)
                        || s2.startsWith(u2.substring(0, 1))    // инициалы
                        || u2.startsWith(s2.substring(0, 1));
            }
            if (surnameMatch && nameOrInitialMatch) return true;
        }

        return false;
    }

    private static String normalizeName(String s) {
        // нижний регистр, схлопываем пробелы, убираем точки и лишние символы в именах
        String t = s.toLowerCase(Locale.ROOT)
                .replace('ё','е')
                .replace(".", " ")
                .replace(",", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return t;
    }

    private static Set<String> nameTokens(String s) {
        // токенизируем по пробелам, выкидываем короткие/служебные
        Set<String> out = new HashSet<>();
        for (String p : s.split(" ")) {
            String tok = p.trim();
            if (tok.length() >= 2) out.add(tok);
        }
        return out;
    }


    private static String buildPeriodText(
            DriverMonthSettlement monthTotals,
            YearMonth ym,
            PeriodSlice slice,
            String label,
            String driverName
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("💼 <b>Ведомость по взаиморасчётам</b>\n");
        sb.append("👤 <b>").append(escape(safe(driverName))).append("</b>\n");
        if (label.equalsIgnoreCase("Текущее")) {
            sb.append("🗓 ").append(label).append(" (")
                    .append(LocalDate.now().minusDays(1).format(DAY_FMT)).append(" - ")
                    .append(LocalDate.now().format(DAY_FMT)).append(")\n\n");
        } else {
            sb.append("🗓 ").append(label).append(" (").append(ym.format(YM_SHOW_FMT)).append(")\n\n");
        }

        if (slice.delta.incasso.compareTo(BigDecimal.ZERO) != 0) {
            sb.append("💸 Инкассация: <b>").append(moneyRub(slice.delta.incasso)).append("</b>\n\n");
        }
        if (slice.delta.payout.compareTo(BigDecimal.ZERO) != 0) {
            sb.append("💳 Выплата з/п: <b>").append(moneyRub(slice.delta.payout)).append("</b>\n\n");
        }
        if (slice.delta.accruals.compareTo(BigDecimal.ZERO) != 0) {
            sb.append("🧾 Начисление з/п: <b>").append(moneyRub(slice.delta.accruals)).append("</b>\n\n");
        }
        if (slice.delta.purchase.compareTo(BigDecimal.ZERO) != 0) {
            sb.append("📦 Закупка у поставщика: <b>").append(moneyRub(slice.delta.purchase)).append("</b>\n\n");
        }
        if (slice.delta.transfer.compareTo(BigDecimal.ZERO) != 0) {
            sb.append("🔁 Перемещение д/с: <b>").append(moneyRub(slice.delta.transfer)).append("</b>\n\n");
        }

        if (slice.endBalance.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal abs = slice.endBalance.abs();
            if (slice.endBalance.signum() < 0) {
                sb.append("✅На текущий момент компания <b>Вам должна ")
                        .append(moneyRub(abs)).append("</b>\n\n");
            } else {
                sb.append("❌На текущий момент Вы <b>должны компании ")
                        .append(moneyRub(abs)).append("</b>\n\n");
            }
        }

        sb.append("⚠️ Данные могут быть неточными: модуль расчётов в тестировании.");
        return sb.toString();
    }

    /** WEEK VIEW (per day + total) **/
    private static String buildWeekText(Long driverId, YearMonth ym, String driverName) {
        ensureTempLoaded();
        MonthBlock mb = TEMP.getOrDefault(driverId, Collections.emptyMap()).get(ym.toString());
        if (mb == null) {
            return "⚠️ Нет данных за " + ym.format(YM_SHOW_FMT) + ".";
        }
        LocalDate today = LocalDate.now();
        int weekIndex = weekOfMonth(today);
        WeekBlock wb = mb.weeks.get(weekIndex);
        if (wb == null) return "⚠️ Нет данных за неделю (" + weekIndex + ") в " + ym.format(YM_SHOW_FMT) + ".";

        // Compose per-day deltas, using robust baseline for the week
        StringBuilder sb = new StringBuilder();
        sb.append("💼 <b>Ведомость по дням недели</b>\n");
        sb.append("👤 <b>").append(escape(safe(driverName))).append("</b>\n");
        LocalDate mon = wb.weekMonday;
        LocalDate sun = mon.plusDays(6);
        sb.append("🗓 Неделя: ").append(mon.format(DAY_FMT)).append(" — ").append(sun.format(DAY_FMT)).append("\n\n");

        Totals prev = (wb.mondayStart != null) ? wb.mondayStart : baselineFromPreviousDay(mb, wb.weekMonday);
        boolean firstPrinted = false;

        BigDecimal totalIncasso = BigDecimal.ZERO;
        BigDecimal totalPayout = BigDecimal.ZERO;
        BigDecimal totalAccruals = BigDecimal.ZERO;
        BigDecimal totalPurchase = BigDecimal.ZERO;
        BigDecimal totalTransfer = BigDecimal.ZERO;

        for (int i = 0; i < 7; i++) {
            LocalDate d = mon.plusDays(i);
            Totals dayTotals = wb.days.get(d.getDayOfWeek());
            if (dayTotals == null) continue; // no snapshot for that day yet

            boolean skipDelta = !firstPrinted && equalsTotals(prev, dayTotals);
            Delta delta = skipDelta ? new Delta() : Delta.between(prev, dayTotals);
            prev = dayTotals; // move baseline for the next day
            firstPrinted = true;

            totalIncasso = totalIncasso.add(delta.incasso);
            totalPayout = totalPayout.add(delta.payout);
            totalAccruals = totalAccruals.add(delta.accruals);
            totalPurchase = totalPurchase.add(delta.purchase);
            totalTransfer = totalTransfer.add(delta.transfer);

            sb.append("<b>").append(dayNameRu(d.getDayOfWeek())).append(" ")
                    .append(d.format(DAY_FMT)).append("</b>\n");
            appendIfNonZero(sb, "💸 Инкассация", delta.incasso);
            appendIfNonZero(sb, "💳 Выплата з/п", delta.payout);
            appendIfNonZero(sb, "🧾 Начисление з/п", delta.accruals);
            appendIfNonZero(sb, "📦 Закупка у поставщика", delta.purchase);
            appendIfNonZero(sb, "🔁 Перемещение д/с", delta.transfer);

            sb.append("Итог на конец дня: <b>")
                    .append(moneyRub(dayTotals.closing))
                    .append("</b>\n");

            if (dayTotals.closing.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal abs = dayTotals.closing.abs();
                if (dayTotals.closing.signum() < 0) {
                    sb.append("✅На конец дня компания <b>Вам должна ")
                            .append(moneyRub(abs)).append("</b>\n\n");
                } else {
                    sb.append("❌На конец дня Вы <b>должны компании ")
                            .append(moneyRub(abs)).append("</b>\n\n");
                }
            } else {
                sb.append("\n"); // пустая строка между днями
            }
        }

        sb.append("<b>ИТОГО за неделю</b>\n");
        appendIfNonZero(sb, "💸 Инкассация", totalIncasso);
        appendIfNonZero(sb, "💳 Выплата з/п", totalPayout);
        appendIfNonZero(sb, "🧾 Начисление з/п", totalAccruals);
        appendIfNonZero(sb, "📦 Закупка у поставщика", totalPurchase);
        appendIfNonZero(sb, "🔁 Перемещение д/с", totalTransfer);
        if (prev != null) {
            sb.append("Конечный остаток: <b>").append(moneyRub(prev.closing)).append("</b>\n\n");
            if (prev.closing.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal abs = prev.closing.abs();
                if (prev.closing.signum() < 0) {
                    sb.append("✅На текущий момент компания <b>Вам должна ")
                            .append(moneyRub(abs)).append("</b>\n\n");
                } else {
                    sb.append("❌На текущий момент Вы <b>должны компании ")
                            .append(moneyRub(abs)).append("</b>\n\n");
                }
            }
        }
        sb.append("⚠️ Данные могут быть неточными: модуль расчётов в тестировании.");
        return sb.toString();
    }

    private static void appendIfNonZero(StringBuilder sb, String label, BigDecimal v) {
        if (v == null) return;
        if (v.compareTo(BigDecimal.ZERO) != 0) {
            sb.append(label).append(": <b>").append(moneyRub(v)).append("</b>\n");
        }
    }

    private static String dayNameRu(DayOfWeek d) {
        switch (d) {
            case MONDAY: return "Понедельник";
            case TUESDAY: return "Вторник";
            case WEDNESDAY: return "Среда";
            case THURSDAY: return "Четверг";
            case FRIDAY: return "Пятница";
            case SATURDAY: return "Суббота";
            case SUNDAY: return "Воскресенье";
            default: return d.name();
        }
    }

    public static void ensureDayStartForAllAt1AM() {
        java.time.LocalTime nowTime = java.time.LocalTime.now();
        if (nowTime.isBefore(java.time.LocalTime.of(1, 0))) return;

        ensureTempLoaded();
        LocalDate today = LocalDate.now();
        String todayStr = today.format(D_FMT);
        YearMonth ym = YearMonth.from(today);

        List<UserData> drivers = Main.users.stream()
                .filter(x -> x.getRole() != null && x.getRole().equalsIgnoreCase("DRIVER"))
                .collect(Collectors.toList());

        for (UserData d : drivers) {
            Optional<DriverMonthSettlement> opt = loadSettlementForDriver(d, ym);
            if (opt.isEmpty()) continue;

            MonthBlock mb = TEMP.computeIfAbsent(d.getId(), k -> new HashMap<>())
                    .computeIfAbsent(ym.toString(), k -> new MonthBlock(ym));

            Totals cur = Totals.of(opt.get());

            // Set/update today's structures
            if (mb.today == null || !todayStr.equals(safe(mb.today.date))) {
                mb.yesterdayPrev = mb.yesterday;
                mb.yesterday = mb.today;
                mb.dayStart = new DatedTotals(todayStr, mb.yesterday != null ? mb.yesterday.totals : cur);
                mb.today = new DatedTotals(todayStr, cur);
            } else {
                mb.today.totals = cur;
            }

            // Persist a snapshot for YESTERDAY as the end-of-day snapshot (Mon-Sun grid)
            if (mb.yesterday != null) {
                LocalDate y = LocalDate.parse(mb.yesterday.date);
                int w = weekOfMonth(y);
                WeekBlock wb = mb.weeks.computeIfAbsent(w, __ -> new WeekBlock(mondayOf(y)));
                if (wb.mondayStart == null && y.getDayOfWeek() == DayOfWeek.MONDAY) {
                    wb.mondayStart = mb.yesterdayPrev != null ? mb.yesterdayPrev.totals : mb.yesterday.totals;
                }
                wb.days.put(y.getDayOfWeek(), mb.yesterday.totals);
            }

            // Ensure current week exists and holds (temporary) today snapshot too
            int wToday = weekOfMonth(today);
            WeekBlock wbToday = mb.weeks.computeIfAbsent(wToday, __ -> new WeekBlock(mondayOf(today)));
            if (wbToday.mondayStart == null) {
                // если сегодня понедельник — база по "старту дня"
                if (today.getDayOfWeek() == DayOfWeek.MONDAY) {
                    wbToday.mondayStart = mb.dayStart != null ? mb.dayStart.totals : cur;
                }
            }
            wbToday.days.put(today.getDayOfWeek(), cur);

            // Фолбек базы недели: если не понедельник и базы нет — попробуем взять "вчера - 1"
            if (wbToday.mondayStart == null && mb.yesterday != null) {
                LocalDate monThisWeek = mondayOf(today);
                if (!safe(mb.yesterday.date).isBlank()) {
                    LocalDate y = LocalDate.parse(mb.yesterday.date);
                    if (!y.isBefore(monThisWeek)) {
                        wbToday.mondayStart = (mb.yesterdayPrev != null) ? mb.yesterdayPrev.totals : mb.yesterday.totals;
                    }
                }
            }
        }

        persistTemp();
    }

    // ==== DATA STRUCTURES =====================================================

    public static class DriverMonthSettlement {
        public String driverName;
        public BigDecimal opening;
        public BigDecimal incasso;
        public BigDecimal payout;
        public BigDecimal accruals;
        public BigDecimal purchase;
        public BigDecimal transfer;
        public BigDecimal closing;
    }

    private static class MonthBlock {
        public final String ym; // yyyy-MM
        public DatedTotals dayStart;
        public DatedTotals today;
        public DatedTotals yesterday;
        public DatedTotals yesterdayPrev;
        public Map<Integer, WeekBlock> weeks = new HashMap<>(); // 1..5 (иногда 6)
        public MonthBlock(YearMonth ym) { this.ym = ym.toString(); }
    }

    private static class WeekBlock {
        public LocalDate weekMonday; // понедельник текущей недели (в рамках месяца)
        public Totals mondayStart;   // состояние на начало понедельника (база для дельт)
        public Map<DayOfWeek, Totals> days = new EnumMap<>(DayOfWeek.class); // Mon..Sun -> snapshot на конец дня
        public WeekBlock(LocalDate weekMonday) { this.weekMonday = weekMonday; }
    }

    private static class DatedTotals {
        public String date; // ISO yyyy-MM-dd
        public Totals totals;
        public DatedTotals() {}
        public DatedTotals(String date, Totals totals) { this.date = date; this.totals = totals; }
    }

    private static class Totals {
        public BigDecimal opening = BigDecimal.ZERO;
        public BigDecimal incasso = BigDecimal.ZERO;
        public BigDecimal payout = BigDecimal.ZERO;
        public BigDecimal accruals = BigDecimal.ZERO;
        public BigDecimal purchase = BigDecimal.ZERO;
        public BigDecimal transfer = BigDecimal.ZERO;
        public BigDecimal closing = BigDecimal.ZERO;
        public static Totals of(DriverMonthSettlement s) {
            Totals t = new Totals();
            t.opening = nz(s.opening);
            t.incasso = nz(s.incasso);
            t.payout = nz(s.payout);
            t.accruals = nz(s.accruals);
            t.purchase = nz(s.purchase);
            t.transfer = nz(s.transfer);
            t.closing = nz(s.closing);
            return t;
        }
    }

    private static class Delta {
        public BigDecimal incasso = BigDecimal.ZERO;
        public BigDecimal payout = BigDecimal.ZERO;
        public BigDecimal accruals = BigDecimal.ZERO;
        public BigDecimal purchase = BigDecimal.ZERO;
        public BigDecimal transfer = BigDecimal.ZERO;
        public BigDecimal closing = BigDecimal.ZERO;
        public static Delta between(Totals a, Totals b) {
            Delta d = new Delta();
            d.incasso = b.incasso.subtract(a.incasso);
            d.payout = b.payout.subtract(a.payout);
            d.accruals = b.accruals.subtract(a.accruals);
            d.purchase = b.purchase.subtract(a.purchase);
            d.transfer = b.transfer.subtract(a.transfer);
            d.closing = b.closing.subtract(a.closing);
            return d;
        }
    }

    private static class PeriodSlice {
        public final Delta delta;
        public final BigDecimal startBalance;
        public final BigDecimal endBalance;
        public PeriodSlice(Delta delta, BigDecimal startBalance, BigDecimal endBalance) {
            this.delta = delta; this.startBalance = startBalance; this.endBalance = endBalance;
        }
    }

    // ==== SNAPSHOT UPDATE / COMPUTE ===========================================

    private static void ensureTempLoaded() {
        if (TEMP != null) return;
        try {
            Path p = tempPath();
            if (Files.exists(p)) {
                TEMP = MAPPER.readValue(p.toFile(), new TypeReference<Map<Long, Map<String, MonthBlock>>>() {});
            } else {
                TEMP = new HashMap<>();
            }
        } catch (Exception e) {
            TEMP = new HashMap<>();
        }
    }

    private static void persistTemp() {
        try {
            Path p = tempPath();
            if (p.getParent() != null && !Files.exists(p.getParent())) {
                Files.createDirectories(p.getParent(), new FileAttribute<?>[0]);
            }
            Path tmp = p.resolveSibling(p.getFileName().toString() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), TEMP);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {}
    }

    private static Path tempPath() {
        Path botDir = Path.of("").toAbsolutePath();
        return botDir.resolve("payroll_temp.json");
    }

    private static void updateTemp(Long driverId, YearMonth ym, DriverMonthSettlement s) {
        ensureTempLoaded();
        MonthBlock mb = TEMP.computeIfAbsent(driverId, k -> new HashMap<>())
                .computeIfAbsent(ym.toString(), k -> new MonthBlock(ym));
        LocalDate today = LocalDate.now();
        String todayStr = today.format(D_FMT);
        Totals cur = Totals.of(s);

        if (mb.today == null || !safe(mb.today.date).equals(todayStr)) {
            mb.yesterdayPrev = mb.yesterday;
            mb.yesterday = mb.today;
            Totals start = (mb.yesterday != null) ? mb.yesterday.totals : cur;
            mb.dayStart = new DatedTotals(todayStr, start);
            mb.today = new DatedTotals(todayStr, cur);
        } else {
            mb.today.totals = cur;
        }

        // Fill week grid for TODAY
        int wToday = weekOfMonth(today);
        WeekBlock wbToday = mb.weeks.computeIfAbsent(wToday, __ -> new WeekBlock(mondayOf(today)));
        if (wbToday.mondayStart == null && today.getDayOfWeek() == DayOfWeek.MONDAY) {
            wbToday.mondayStart = mb.dayStart != null ? mb.dayStart.totals : cur;
        }
        wbToday.days.put(today.getDayOfWeek(), cur);

        // Фолбек: если не понедельник и базы нет — пробуем взять "вчера - 1"
        if (wbToday.mondayStart == null && mb.yesterday != null) {
            LocalDate monThisWeek = mondayOf(today);
            if (!safe(mb.yesterday.date).isBlank()) {
                LocalDate y = LocalDate.parse(mb.yesterday.date);
                if (!y.isBefore(monThisWeek)) {
                    wbToday.mondayStart = (mb.yesterdayPrev != null) ? mb.yesterdayPrev.totals : mb.yesterday.totals;
                }
            }
        }

        persistTemp();
    }

    private static PeriodSlice computeSlice(Long driverId, YearMonth ym, String periodKey) {
        ensureTempLoaded();
        MonthBlock b = TEMP.getOrDefault(driverId, Collections.emptyMap()).get(ym.toString());
        if (b == null) return emptySlice();

        String key = (periodKey == null) ? "TODAY" : periodKey.toUpperCase();
        if ("DAY".equals(key)) {
            if (b.yesterday == null || b.yesterdayPrev == null) return emptySlice();
            Delta delta = Delta.between(b.yesterdayPrev.totals, b.yesterday.totals);
            BigDecimal s = b.yesterdayPrev.totals.closing;
            BigDecimal e = b.yesterday.totals.closing;
            return new PeriodSlice(delta, s, e);
        }
        if ("WEEK".equals(key)) {
            LocalDate today = LocalDate.now();
            int w = weekOfMonth(today);
            WeekBlock wb = b.weeks.get(w);
            if (wb == null || b.today == null) return emptySlice();
            Totals start = (wb.mondayStart != null)
                    ? wb.mondayStart
                    : (b.dayStart != null ? b.dayStart.totals : b.today.totals);
            Delta delta = Delta.between(start, b.today.totals);
            BigDecimal s = start.closing;
            BigDecimal e = b.today.totals.closing;
            return new PeriodSlice(delta, s, e);
        }

        if (b.today == null) return emptySlice();
        Totals todayTotals = b.today.totals;
        Totals startTotals = (b.dayStart != null) ? b.dayStart.totals : todayTotals;
        Delta d = Delta.between(startTotals, b.today.totals);
        BigDecimal start = todayTotals.opening;
        BigDecimal end   = todayTotals.closing;
        return new PeriodSlice(d, start, end);
    }

    private static PeriodSlice emptySlice() {
        return new PeriodSlice(new Delta(), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    // ==== HELPERS =============================================================

    private static int weekOfMonth(LocalDate date) {
        return date.get(WeekFields.ISO.weekOfMonth());
    }

    private static LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private static Totals firstNonNullTotals(WeekBlock wb) {
        for (int i = 0; i < 7; i++) {
            Totals t = wb.days.get(DayOfWeek.MONDAY.plus(i));
            if (t != null) return t;
        }
        return null;
    }

    private static String jstr(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return (v == null) ? "" : v.asText("");
    }

    private static BigDecimal jnum(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || v.isNull()) return BigDecimal.ZERO;
        try {
            if (v.isNumber()) return new BigDecimal(v.asText()); // точный парсинг, без double
            String s = v.asText("").replace(" ", "").replace(",", ".");
            if (s.isBlank()) return BigDecimal.ZERO;
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String moneyRub(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance(new Locale("ru", "RU"));
        df.applyPattern("#,##0.00");
        String s = df.format(v).replace('\u00A0', ' ');
        return s + " руб.";
    }

    private static String safe(String s) { return (s == null) ? "" : s; }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Long parseLongSafe(String s) { try { return Long.valueOf(Long.parseLong(s)); } catch (Exception e) { return null; } }

    private static BigDecimal nz(BigDecimal v) { return (v == null) ? BigDecimal.ZERO : v; }

    private static boolean equalsTotals(Totals a, Totals b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.opening.compareTo(b.opening) == 0
                && a.incasso.compareTo(b.incasso) == 0
                && a.payout.compareTo(b.payout) == 0
                && a.accruals.compareTo(b.accruals) == 0
                && a.purchase.compareTo(b.purchase) == 0
                && a.transfer.compareTo(b.transfer) == 0
                && a.closing.compareTo(b.closing) == 0;
    }

    /** Берём базу недели:
     *  1) если есть mondayStart — используем его;
     *  2) иначе пытаемся взять закрытие воскресенья прошлой недели;
     *  3) иначе берём первый доступный день недели (и тогда первую дельту не печатаем).
     */
    private static Totals baselineFromPreviousDay(MonthBlock mb, LocalDate weekMonday) {
        LocalDate prevDay = weekMonday.minusDays(1); // воскресенье
        WeekBlock prevWb = mb.weeks.get(weekOfMonth(prevDay));
        if (prevWb != null) {
            Totals prevClose = prevWb.days.get(prevDay.getDayOfWeek());
            if (prevClose != null) return prevClose;
        }
        WeekBlock thisWb = mb.weeks.get(weekOfMonth(weekMonday));
        Totals first = (thisWb != null) ? firstNonNullTotals(thisWb) : null;
        return (first != null) ? first : new Totals();
    }

    // Прогрев TEMP для всех водителей на текущий месяц
    private static void warmupTempForMonth(YearMonth ym) {
        ensureTempLoaded();
        List<UserData> drivers = Main.users.stream()
                .filter(x -> "DRIVER".equalsIgnoreCase(safe(x.getRole())))
                .collect(Collectors.toList());
        for (UserData d : drivers) {
            loadSettlementForDriver(d, ym).ifPresent(s -> updateTemp(d.getId(), ym, s));
        }
    }
}
