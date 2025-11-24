package modules;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.ParseMode;
import core.Main;
import ru.xr4v3.bot.events.annotations.OnCallbackQuery;
import utils.Order;
import utils.UserData;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class ReturnsManager {

    /** Кэш возвратов по targetDriverId -> список найденных возвратов */
    private static final Map<Long, List<ReturnCtx>> CACHE = new HashMap<>();

    // ========= ПУБЛИЧНЫЕ ВХОДЫ (для reply-кнопок) =========

    /** Открыть список возвратов для текущего пользователя (водителя) — вызывать из обработчика обычной кнопки. */
    public static void openFromButton(Long chatId, Long userId) {
        if (chatId == null || userId == null) return;
        UserData driver = UserData.findUserById(userId);
        if (driver == null) {
            Main.getInstance().sendMessage(chatId, "⚠️ Пользователь не найден.");
            return;
        }
        showReturnList(chatId, driver.getId(), /*editMsgId*/ null, /*viewerModeOther*/ false);
    }

    /**
     * Открыть список возвратов другого водителя (для админа/логиста/менеджера) — из обычной кнопки/команды.
     * @param chatId          куда отправлять
     * @param targetDriverId  чей список возвратов смотреть
     */
    public static void openOthersFromButton(Long chatId, Long targetDriverId) {
        if (chatId == null || targetDriverId == null) return;
        UserData target = UserData.findUserById(targetDriverId);
        if (target == null) {
            Main.getInstance().sendMessage(chatId, "⚠️ Водитель не найден.");
            return;
        }
        showReturnList(chatId, target.getId(), /*editMsgId*/ null, /*viewerModeOther*/ true);
    }

    // ========= CALLBACK HANDLER =========

    @OnCallbackQuery
    public static void onCallback(Update update) {
        if (update == null || update.callbackQuery() == null) return;
        String data = update.callbackQuery().data();
        if (data == null) return;

        Long chatId = update.callbackQuery().message().chat().id();
        Integer messageId = update.callbackQuery().message().messageId();
        Long requesterId = update.callbackQuery().from().id();

        // свои возвраты (без targetDriverId)
        if (data.equals("returns:open") || data.equals("returns:list")) {
            UserData self = UserData.findUserById(requesterId);
            if (self == null) {
                Main.getInstance().sendMessage(chatId, "⚠️ Пользователь не найден.");
                return;
            }
            showReturnList(chatId, self.getId(), messageId, false);
            return;
        }

        // чужие возвраты: returns:list:<driverId>
        if (data.startsWith("returns:list:")) {
            Long targetId = parseLongSafe(data.substring("returns:list:".length()));
            if (targetId == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Не удалось распознать водителя.");
                return;
            }
            UserData target = UserData.findUserById(targetId);
            if (target == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Водитель не найден.");
                return;
            }
            showReturnList(chatId, target.getId(), messageId, true);
            return;
        }

        // свои: returns:view:<idx>
        if (data.startsWith("returns:view:") && countColons(data) == 2) {
            Integer idx = parseIndex(data, "returns:view:");
            if (idx == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Некорректные данные.");
                return;
            }
            showReturnCardByIndex(chatId, messageId, /*target*/ requesterId, idx, false);
            return;
        }

        // чужие: returns:view:<driverId>:<idx>
        if (data.startsWith("returns:view:") && countColons(data) == 3) {
            String[] p = data.split(":");
            Long targetId = parseLongSafe(p[2]);
            Integer idx   = parseIntSafe(p[3]);
            if (targetId == null || idx == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Некорректные данные.");
                return;
            }
            showReturnCardByIndex(chatId, messageId, targetId, idx, true);
            return;
        }

        // свои: returns:attach:<idx>
        if (data.startsWith("returns:attach:") && countColons(data) == 2) {
            Integer idx = parseIndex(data, "returns:attach:");
            if (idx == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Некорректные данные.");
                return;
            }
            startAttachPhoto(chatId, messageId, requesterId, /*targetDriverId*/ requesterId, idx, false);
            return;
        }

        // чужие: returns:attach:<driverId>:<idx>
        if (data.startsWith("returns:attach:") && countColons(data) == 3) {
            String[] p = data.split(":");
            Long targetId = parseLongSafe(p[2]);
            Integer idx   = parseIntSafe(p[3]);
            if (targetId == null || idx == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Некорректные данные.");
                return;
            }
            startAttachPhoto(chatId, messageId, requesterId, targetId, idx, true);
            return;
        }

        // свои: returns:photos:<idx>
        if (data.startsWith("returns:photos:") && countColons(data) == 2) {
            Integer idx = parseIndex(data, "returns:photos:");
            if (idx == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Некорректные данные.");
                return;
            }
            showReturnPhotos(chatId, messageId, /*target*/ requesterId, idx, false);
            return;
        }

        // чужие: returns:photos:<driverId>:<idx>
        if (data.startsWith("returns:photos:") && countColons(data) == 3) {
            String[] p = data.split(":");
            Long targetId = parseLongSafe(p[2]);
            Integer idx   = parseIntSafe(p[3]);
            if (targetId == null || idx == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Некорректные данные.");
                return;
            }
            showReturnPhotos(chatId, messageId, targetId, idx, true);
            return;
        }

        // свои: returns:markdone:<idx>
        if (data.startsWith("returns:markdone:") && countColons(data) == 2) {
            Integer idx = parseIndex(data, "returns:markdone:");
            if (idx == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Некорректные данные.");
                return;
            }
            markDone(chatId, messageId, requesterId, idx, false);
            return;
        }

        // чужие: returns:markdone:<driverId>:<idx>
        if (data.startsWith("returns:markdone:") && countColons(data) == 3) {
            String[] p = data.split(":");
            Long targetId = parseLongSafe(p[2]);
            Integer idx   = parseIntSafe(p[3]);
            if (targetId == null || idx == null) {
                Main.getInstance().editMessage(chatId, messageId, "❌ Некорректные данные.");
                return;
            }
            markDone(chatId, messageId, targetId, idx, true);
        }
    }

    // ========= РЕНДЕР СПИСКА И КАРТОЧКИ =========

    private static void showReturnList(Long chatId, Long targetDriverId, Integer editMessageIdOrNull, boolean viewerModeOther) {
        UserData target = UserData.findUserById(targetDriverId);
        if (target == null) {
            if (editMessageIdOrNull == null) {
                Main.getInstance().sendMessage(chatId, "❌ Водитель не найден.");
            } else {
                Main.getInstance().editMessage(chatId, editMessageIdOrNull, "❌ Водитель не найден.");
            }
            return;
        }

        List<ReturnCtx> list = findReturnsForDriver(target);
        CACHE.put(targetDriverId, list);

        String titlePrefix = viewerModeOther ? ("🔁 Возвраты водителя: " + escape(target.getName())) : "🔁 Ваши возвраты";
        if (list.isEmpty()) {
            String text = "" + titlePrefix + "\nНа текущий момент возвраты не найдены.";
            if (editMessageIdOrNull == null) {
                Main.getInstance().sendMessage(chatId, text, ParseMode.HTML);
            } else {
                Main.getInstance().editMessage(chatId, editMessageIdOrNull, text, ParseMode.HTML,
                        buildBackToDriverMenuKb());
            }
            return;
        }

        // вертикальный список кнопок с номерами возвратов
        List<List<InlineKeyboardButton>> kb = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ReturnCtx ctx = list.get(i);
            boolean hasPhotos = OrderLoader.hasPhotoInReturn(nv(ctx.returnItem.returnNumber, ""));
            boolean done = isDone(ctx.returnItem);
            // Текст: № 12345 + 📷 если есть фото + ✅ если сдан
            StringBuilder btn = new StringBuilder("№ ").append(nv(ctx.order.orderNumber, "—"));
            if (hasPhotos) btn.append(" \uD83D\uDCF8"); // 📷
            if (done) btn.append(" ✅");

            String cb = viewerModeOther ? ("returns:view:" + targetDriverId + ":" + i) : ("returns:view:" + i);
            kb.add(Collections.singletonList(new InlineKeyboardButton(btn.toString()).callbackData(cb)));
        }

        // Назад
        if (viewerModeOther) kb.addAll(buildBackToDriverMenuKb());

        String title = "" + titlePrefix + "\nВыберите номер возврата:";
        if (editMessageIdOrNull == null) {
            Main.getInstance().sendInlineKeyboard(chatId, kb, title);
        } else {
            Main.getInstance().editMessage(chatId, editMessageIdOrNull, title, ParseMode.HTML, kb);
        }
    }

    private static void showReturnCardByIndex(Long chatId, Integer messageId, Long targetDriverId, int idx, boolean viewerModeOther) {
        ReturnCtx ctx = getCtxByIndex(targetDriverId, idx);
        if (ctx == null) {
            Main.getInstance().editMessage(chatId, messageId,
                    "❌ Не удалось найти данные возврата. Обновите список.",
                    viewerModeOther ? buildListOtherKb(targetDriverId) : buildListSelfKb());
            return;
        }

        String text = buildSingleReturnText(ctx);

        List<List<InlineKeyboardButton>> kb = new ArrayList<>();

        // Показать фото — если есть
        boolean hasPhotos = OrderLoader.hasPhotoInReturn(nv(ctx.returnItem.returnNumber, ""));
        if (hasPhotos) {
            String photosCb = viewerModeOther ? ("returns:photos:" + targetDriverId + ":" + idx) : ("returns:photos:" + idx);
            kb.add(Collections.singletonList(new InlineKeyboardButton("🖼 Показать фото").callbackData(photosCb)));
        }

        // Прикрепить фото — всегда доступно
        String attachCb = viewerModeOther ? ("returns:attach:" + targetDriverId + ":" + idx) : ("returns:attach:" + idx);
        kb.add(Collections.singletonList(new InlineKeyboardButton("📷 Прикрепить фото").callbackData(attachCb)));

        // ✅ Сдал — показываем ТОЛЬКО если есть фото и ещё не «Сдал»
        boolean done = isDone(ctx.returnItem);
        if (hasPhotos && !done) {
            String doneCb = viewerModeOther ? ("returns:markdone:" + targetDriverId + ":" + idx) : ("returns:markdone:" + idx);
            kb.add(Collections.singletonList(new InlineKeyboardButton("✅ Сдал").callbackData(doneCb)));
        }

        // Назад
        if (viewerModeOther) kb.addAll(buildListOtherKb(targetDriverId));
        else kb.addAll(buildListSelfKb());

        Main.getInstance().editMessage(chatId, messageId, text, ParseMode.HTML, kb);
    }

    private static void startAttachPhoto(Long chatId,
                                         Integer messageId,
                                         Long requesterId,
                                         Long targetDriverId,
                                         int idx,
                                         boolean viewerModeOther) {
        ReturnCtx ctx = getCtxByIndex(targetDriverId, idx);
        if (ctx == null) {
            Main.getInstance().editMessage(chatId, messageId,
                    "❌ Не удалось найти данные возврата. Обновите список.",
                    viewerModeOther ? buildListOtherKb(targetDriverId) : buildListSelfKb());
            return;
        }

        String retNo = nv(ctx.returnItem.returnNumber, "");
        if (retNo.isBlank()) {
            Main.getInstance().editMessage(chatId, messageId, "❌ У возврата не указан номер.", ParseMode.HTML,
                    viewerModeOther ? buildListOtherKb(targetDriverId) : buildListSelfKb());
            return;
        }

        Main.getInstance().pendingReturnPhotoUpload.put(requesterId, retNo);

        String text = buildSingleReturnText(ctx)
                + "\n\n📷 Прикрепление фото\nОтправьте изображение (фото или файл) для возврата №"
                + retNo + ".";
        List<List<InlineKeyboardButton>> kb = new ArrayList<>();
        if (viewerModeOther) kb.addAll(buildListOtherKb(targetDriverId));
        else kb.addAll(buildListSelfKb());
        Main.getInstance().editMessage(chatId, messageId, text, ParseMode.HTML, kb);
    }

    private static void showReturnPhotos(Long chatId, Integer messageId, Long targetDriverId, int idx, boolean viewerModeOther) {
        ReturnCtx ctx = getCtxByIndex(targetDriverId, idx);
        if (ctx == null) {
            Main.getInstance().editMessage(chatId, messageId,
                    "❌ Не удалось найти данные возврата. Обновите список.",
                    viewerModeOther ? buildListOtherKb(targetDriverId) : buildListSelfKb());
            return;
        }

        String retNo = nv(ctx.returnItem.returnNumber, "");
        if (retNo.isBlank()) {
            Main.getInstance().editMessage(chatId, messageId, "❌ У возврата не указан номер.",
                    viewerModeOther ? buildListOtherKb(targetDriverId) : buildListSelfKb());
            return;
        }

        List<File> photos = OrderLoader.getReturnPhotos(retNo);
        if (photos.isEmpty()) {
            String text = buildSingleReturnText(ctx) + "\n\n🖼 Фото не найдены.";
            List<List<InlineKeyboardButton>> kb = new ArrayList<>();
            String attachCb = viewerModeOther ? ("returns:attach:" + targetDriverId + ":" + idx) : ("returns:attach:" + idx);
            kb.add(Collections.singletonList(new InlineKeyboardButton("📷 Прикрепить фото").callbackData(attachCb)));
            if (viewerModeOther) kb.addAll(buildListOtherKb(targetDriverId));
            else kb.addAll(buildListSelfKb());

            Main.getInstance().editMessage(chatId, messageId, text, ParseMode.HTML, kb);
            return;
        }

        // Отдельным сообщением отправляем медиа-группу
        Main.getInstance().sendMediaGroup(chatId, photos);
    }

    private static void markDone(Long chatId, Integer messageId, Long targetDriverId, int idx, boolean viewerModeOther) {
        ReturnCtx ctx = getCtxByIndex(targetDriverId, idx);
        if (ctx == null) {
            Main.getInstance().editMessage(chatId, messageId,
                    "❌ Не удалось найти данные возврата. Обновите список.",
                    viewerModeOther ? buildListOtherKb(targetDriverId) : buildListSelfKb());
            return;
        }
        String retNo = nv(ctx.returnItem.returnNumber, "");
        boolean hasPhotos = OrderLoader.hasPhotoInReturn(retNo);
        boolean done = isDone(ctx.returnItem);

        if (!hasPhotos) {
            // Нет фото — запрещаем отметку
            String text = buildSingleReturnText(ctx)
                    + "\n\n⚠️ Нельзя отметить «Сдал» без прикреплённых фото.";
            List<List<InlineKeyboardButton>> kb = new ArrayList<>();
            String attachCb = viewerModeOther ? ("returns:attach:" + targetDriverId + ":" + idx) : ("returns:attach:" + idx);
            kb.add(Collections.singletonList(new InlineKeyboardButton("📷 Прикрепить фото").callbackData(attachCb)));
            if (viewerModeOther) kb.addAll(buildListOtherKb(targetDriverId));
            else kb.addAll(buildListSelfKb());
            Main.getInstance().editMessage(chatId, messageId, text, ParseMode.HTML, kb);
            return;
        }

        if (done) {
            // Уже «Сдал» — просто перерисуем карточку без кнопки
            showReturnCardByIndex(chatId, messageId, targetDriverId, idx, viewerModeOther);
            return;
        }

        // Ставим статус «Сдал»
        setDone(ctx.returnItem);

        // Уведомляем админов и логистов
        notifyAdminsAndLogistics(ctx);

        // Перерисовываем карточку (кнопка «Сдал» исчезнет)
        showReturnCardByIndex(chatId, messageId, targetDriverId, idx, viewerModeOther);
    }

    private static String buildSingleReturnText(ReturnCtx ctx) {
        String org = nv(firstNonBlank(ctx.supplierOrder.organization, ctx.order.organization), "—");
        String wh  = nv(ctx.supplierOrder.supplierWarehouse, "—");
        String rNo = nv(ctx.returnItem.returnNumber, "—");

        String status = nv(ctx.returnItem.status, "—");
        boolean done = isDone(ctx.returnItem);

        StringBuilder sb = new StringBuilder();
        sb.append("🔁 Возврат № ").append(escape(rNo));
        if (done) sb.append(" ✅");
        sb.append("\n");
        sb.append("Организация: ").append(escape(org)).append("\n");
        sb.append("Склад поставщика: ").append(escape(wh)).append("\n");

        // Состав
        appendCompositionLines(sb, "📦 ", ctx.returnItem.productComposition);

        // Комментарий
        String comment = nv(ctx.returnItem.comment, "");
        if (!comment.isBlank()) {
            sb.append("\n💬 ").append(escape(comment)).append("\n");
        }

        // ⏳ Дедлайн/просрочка: показываем только если НЕ "Сдал" и статус распарсился как дата
        if (!done) {
            LocalDate startDate = parseStatusDate(status); // статус как дата dd.MM.yyyy
            if (startDate != null) {
                LocalDate deadline = startDate.plusDays(10);
                LocalDate today = LocalDate.now();
                long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, deadline);

                if (daysLeft > 0) {
                    sb.append("⏳ Осталось дней до сдачи: ").append(daysLeft)
                            .append(" (до ").append(deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append(")")
                            .append("\n");
                } else if (daysLeft == 0) {
                    sb.append("⏳ Сегодня последний день сдачи (")
                            .append(deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append(")")
                            .append("\n");
                } else { // просрочен
                    sb.append("⚠️ Просрочен на ").append(Math.abs(daysLeft)).append(" дн.")
                            .append(" (дедлайн был ").append(deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append(")")
                            .append("\n");
                }
            }
        }
        return sb.toString();
    }

    // Парсим статус как дату dd.MM.yyyy (если это не "Сдал")
    private static LocalDate parseStatusDate(String status) {
        if (status == null) return null;
        String s = status.trim();
        if (s.equalsIgnoreCase("Сдал")) return null;
        try {
            return LocalDate.parse(s, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception e) {
            return null;
        }
    }


    // ========= ПОИСК ДАННЫХ =========

    /** Найти все возвраты, где ВодительВозврата содержит имя данного водителя. */
    private static List<ReturnCtx> findReturnsForDriver(UserData driver) {
        String needle = driver.getName() == null ? "" : driver.getName();
        if (needle.isBlank()) return Collections.emptyList();

        List<ReturnCtx> out = new ArrayList<>();
        if (OrderLoader.orders == null) return out;

        for (Order order : OrderLoader.orders) {
            if (order == null || order.supplierOrders == null) continue;

            for (Order.SupplierOrder so : order.supplierOrders) {
                if (so == null || so.returns == null || so.returns.isEmpty()) continue;

                for (Order.ReturnItem r : so.returns) {
                    String returnDriver = r == null ? "" : Optional.ofNullable(r.returnDriver).orElse("");
                    if (!returnDriver.isBlank() && returnDriver.contains(needle)) {
                        out.add(new ReturnCtx(order, so, r));
                    }
                }
            }
        }
        return out;
    }

    public static int countUndoneReturnsForDriver(UserData driver) {
        if (driver == null || driver.getName() == null || driver.getName().isBlank()) return 0;
        if (OrderLoader.orders == null) return 0;

        String needle = driver.getName();
        int cnt = 0;

        for (Order order : OrderLoader.orders) {
            if (order == null || order.supplierOrders == null) continue;

            for (Order.SupplierOrder so : order.supplierOrders) {
                if (so == null || so.returns == null) continue;

                for (Order.ReturnItem r : so.returns) {
                    if (r == null) continue;
                    String returnDriver = r.returnDriver == null ? "" : r.returnDriver;
                    if (!returnDriver.isBlank() && returnDriver.contains(needle) && !isDone(r)) {
                        cnt++;
                    }
                }
            }
        }
        return cnt;
    }

    private static ReturnCtx getCtxByIndex(Long targetDriverId, int idx) {
        if (targetDriverId == null) return null;
        List<ReturnCtx> list = CACHE.get(targetDriverId);
        if (list == null || idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    // ========= УВЕДОМЛЕНИЯ / СТАТУС =========

    private static void notifyAdminsAndLogistics(ReturnCtx ctx) {
        String driverName = nv(ctx.returnItem.returnDriver, "—");
        String retNo = nv(ctx.returnItem.returnNumber, "—");
        String org = nv(firstNonBlank(ctx.supplierOrder.organization, ctx.order.organization), "—");
        String wh = nv(ctx.supplierOrder.supplierWarehouse, "—");

        String text = "✅ <b>Возврат сдан</b>\n"
                + "Водитель: " + escape(driverName) + "\n"
                + "Возврат №: " + escape(retNo) + "\n"
                + "Организация: " + escape(org) + "\n"
                + "Склад поставщика: " + escape(wh);

        for (UserData user : Main.users) {
            if (user.getRole() != null) {
                String role = user.getRole().toUpperCase();
                if ("ADMIN".equals(role) || "LOGISTIC".equals(role)) {
                    Main.getInstance().sendMessage(user.getId(), text, ParseMode.HTML);
                }
            }
        }
    }

    private static boolean isDone(Order.ReturnItem r) {
        String st = (r == null ? null : r.status);
        return st != null && st.trim().equalsIgnoreCase("Сдал");
    }

    private static void setDone(Order.ReturnItem r) {
        if (r == null) return;
        r.status = "Сдал";
        // запись в файлы
        OrderStatusUpdater.updateReturnStatus(r.returnNumber, r.status);
    }

    // ========= КНОПКИ =========

    /** Назад в главное меню водителя */
    private static List<List<InlineKeyboardButton>> buildBackToDriverMenuKb() {
        return Collections.singletonList(
                Collections.singletonList(new InlineKeyboardButton("⬅️ Назад").callbackData("driver:list"))
        );
    }

    /** Назад в список «свои возвраты». */
    private static List<List<InlineKeyboardButton>> buildListSelfKb() {
        return Collections.singletonList(
                Collections.singletonList(new InlineKeyboardButton("⬅️ Назад").callbackData("returns:list"))
        );
    }

    /** Назад в список «чужие возвраты» (по targetDriverId). */
    private static List<List<InlineKeyboardButton>> buildListOtherKb(Long targetDriverId) {
        return Collections.singletonList(
                Collections.singletonList(new InlineKeyboardButton("⬅️ Назад").callbackData("returns:list:" + targetDriverId))
        );
    }

    // ========= УТИЛИТЫ =========

    private static void appendCompositionLines(StringBuilder sb, String bullet, String composition) {
        String comp = nv(composition, "—");
        if ("—".equals(comp)) {
            sb.append("Товар: —");
            return;
        }
        String[] lines = comp.split("\\r?\\n");
        boolean printed = false;
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (!line.isEmpty()) {
                sb.append(bullet).append(escape(line)).append("\n");
                printed = true;
            }
        }
        if (!printed) {
            sb.append("Товар: —");
        }
    }

    private static String nv(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static int countColons(String s) {
        int c = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == ':') c++;
        return c;
    }

    private static Integer parseIndex(String data, String prefix) {
        try {
            return Integer.parseInt(data.substring(prefix.length()));
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLongSafe(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    // ========= DTO =========

    private static class ReturnCtx {
        final Order order;
        final Order.SupplierOrder supplierOrder;
        final Order.ReturnItem returnItem;

        ReturnCtx(Order order, Order.SupplierOrder supplierOrder, Order.ReturnItem returnItem) {
            this.order = order;
            this.supplierOrder = supplierOrder;
            this.returnItem = returnItem;
        }
    }


}
