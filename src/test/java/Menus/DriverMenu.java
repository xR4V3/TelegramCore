package Menus;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.SendMediaGroup;
import core.Main;
import modules.OrderLoader;
import modules.OrderStatusUpdater;
import modules.ReportManager;
import modules.Routes;
import utils.*;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class DriverMenu {

    public void open(Update update) {
        List<List<KeyboardButton>> buttons = Arrays.asList(
                Arrays.asList(
                        new KeyboardButton(EDriverMenuBtn.ROUTES.getButtonText()),
                        new KeyboardButton(EDriverMenuBtn.RETURNS.getButtonText())
                ),
                Arrays.asList(
                        new KeyboardButton(EDriverMenuBtn.SALARY.getButtonText())
                )
        );
        Main.getInstance().sendKeyboard(update.message().chat().id(), Messages.driverMenu, buttons, true, false);
    }

    public void open(Update update, String msg) {
        List<List<KeyboardButton>> buttons = Arrays.asList(
                Arrays.asList(
                        new KeyboardButton(EDriverMenuBtn.ROUTES.getButtonText()),
                        new KeyboardButton(EDriverMenuBtn.RETURNS.getButtonText())
                ),
                Arrays.asList(
                        new KeyboardButton(EDriverMenuBtn.SALARY.getButtonText())
                )
        );
        Main.getInstance().sendKeyboard(update.message().chat().id(), msg, buttons, true, false);
    }



    public void orders(Update update) {
        Long chatId = update.callbackQuery().message().chat().id();
        Integer messageId = update.callbackQuery().message().messageId();
        String data = update.callbackQuery().data();
        LocalDate dateToCheck = null;
        if (data.startsWith("order:")) {
            String[] parts = data.split(":");

            if (parts.length > 2) {
                try {
                    dateToCheck = LocalDate.parse(parts[2]); // формат yyyy-MM-dd
                } catch (Exception e) {
                    // некорректная дата, можно залогировать
                }
            }
        }
        if (data.startsWith("upload_photo:")) {
            String orderNum = data.substring("upload_photo:".length());
            Long userId = update.callbackQuery().from().id();

            // Сохраняем информацию о том, что пользователь хочет загрузить фото к конкретному заказу
            Main.pendingPhotoUpload.put(userId, orderNum);

            Main.getInstance().sendMessage(chatId, "📸 Пожалуйста, отправьте фото для заказа №" + orderNum);
            return;
        }

        if (data.startsWith("show_photos:")) {
            String orderNum = data.substring("show_photos:".length());
            List<File> photos = OrderLoader.getOrderPhotos(orderNum);
            if (photos.isEmpty()) {
                Main.getInstance().sendMessage(chatId, "❌ Для заказа №" + orderNum + " нет загруженных фото.");
            } else {
                Main.getInstance().sendMediaGroup(chatId, photos);
            }
            return;
        }

        // 🔙 Возврат к списку заказов
        if (data.contains("order:back")) {
            Long userId = update.callbackQuery().from().id();
            UserData user = UserData.findUserById(userId);
            if(user.getRole().equals("LOGISTIC") || user.getRole().equals("ADMIN") || user.getRole().equals("MANAGER") ){
                OrderLoader.drivers(update);
                return;
            }
            if (OrderLoader.orders.isEmpty()) {
                Main.getInstance().editMessage(chatId, messageId, "Нет доступных заказов.");
                return;
            }

            List<List<InlineKeyboardButton>> buttonsInline = OrderLoader.buildOrderButtons(OrderLoader.orders, user.getName(), dateToCheck);
            Main.getInstance().editMessage(chatId, messageId, "Выберите заказ:", buttonsInline);
            return;
        }

        if (data.equals("order:find")) {
            String msg = "🔎 Введите номер заказа, который хотите найти:";
            Main.waitingForOrderNumber.put(chatId, "WAITING_FOR_ORDER_NUMBER");
            List<List<InlineKeyboardButton>> backButton = List.of(
                    List.of(new InlineKeyboardButton("◀️ Назад").callbackData("driver:list"))
            );

            Main.getInstance().editMessage(chatId, messageId, msg, backButton);
            return;
        }

        // 📦 Просмотр заказа
        if (data.startsWith("order:")) {
            String[] parts = data.split(":");
            String orderNum = parts.length > 1 ? parts[1] : "";

            Order selected = OrderLoader.orders.stream()
                    .filter(o -> o.orderNumber != null && o.orderNumber.trim().equals(orderNum))
                    .findFirst()
                    .orElse(null);

            if (selected != null) {
                Long userId = update.callbackQuery().from().id();
                UserData currentUser = UserData.findUserById(userId);
                if(currentUser.getRole().equals("MANAGER")){
                    if(!selected.clientManager.contains(currentUser.getName())){
                        Main.getInstance().sendMessage(chatId, "❌ Извините, у Вас нет доступа, " + selected.clientManager + " ответственный за этот заказ.");
                        return;
                    }
                }

                String info = String.format("""
        🏬 Организация: %s
        📦 Заказ № %s от %s
        🏢 Клиент: %s
        👨‍💼 Менеджер клиента: %s

        📅 Дата доставки: %s
        ⚖️ Вес: %s кг   📦 Объем: %s м3   📏 Макс. габарит: %s м
        💸 Сумма заказа: %s руб

        🚚 Адрес доставки: %s
        📦 Способ доставки: %s
        🏗 Разгрузка и подъем: %s

        👤 Контактное лицо: %s
        📞 Принимает: %s
        📱 Дополнительный номер: %s
        ☎️ Запасной телефон: %s

        💳 Способ оплаты: %s   ✅ Статус оплаты: %s
        💬 Комментарий: %s

        📦 Товарный состав:
        %s
        
        🚚 Водитель: %s
        """,
                        selected.organization != null ? selected.organization : "-",
                        selected.orderNumber != null ? selected.orderNumber : "-",
                        selected.deliveryDate != null ? selected.deliveryDate : "-",
                        selected.client != null ? selected.client : "-",
                        selected.clientManager != null ? selected.clientManager : "-",
                        selected.deliveryDate != null ? selected.deliveryDate : "-",
                        selected.weight != null ? selected.weight : "-",
                        selected.volume != null ? selected.volume : "-",
                        selected.length != null ? selected.length : "-",
                        selected.orderTotal != null ? selected.orderTotal : "-",
                        selected.deliveryAddress != null ? selected.deliveryAddress : "-",
                        selected.deliveryMethod != null ? selected.deliveryMethod : "-",
                        selected.unloading != null ? selected.unloading : "-",
                        selected.contactPerson != null ? selected.contactPerson : "-",
                        selected.recipientPhone != null ? selected.recipientPhone : "-",
                        selected.additionalNumber != null ? selected.additionalNumber : "-", // если есть второй запасной
                        selected.backupPhone != null ? selected.backupPhone : "-",
                        selected.paymentMethod != null ? selected.paymentMethod : "-",
                        selected.paymentStatus != null ? selected.paymentStatus : "-",
                        selected.comment != null ? selected.comment : "-",
                        selected.productDescription != null ? selected.productDescription : "-",
                        selected.driver != null ? selected.driver : "-"
                );

                OrderStatus status = OrderStatus.fromDisplayName(selected.orderStatus);
                List<List<InlineKeyboardButton>> buttonsInline = new ArrayList<>();
                if(status == null || (status == OrderStatus.RESCHEDULED_BY_STORE ||
                        status == OrderStatus.RESCHEDULED_BY_CLIENT ||
                        status == OrderStatus.HANDED_TO_MANAGER ||
                        status == OrderStatus.PARTIALLY_DELIVERED ||
                        status == OrderStatus.NOT_SHIPPED_NO_SPACE ||
                        status == OrderStatus.NOT_SHIPPED_NO_STOCK||
                        status == OrderStatus.NOT_SHIPPED_NO_INVOICE||
                        status == OrderStatus.NOT_SHIPPED_NOT_PICKED_FROM_DRIVER)) {
                    buttonsInline.add(List.of(
                            new InlineKeyboardButton("📦 Доставлено").callbackData("OrderStatus:DELIVERED:" + orderNum)
                    ));


                    buttonsInline.add(List.of(
                            new InlineKeyboardButton("🛑 Проблема с заказом [Список]").callbackData("cancel_menu:" + orderNum)
                    ));
                }


                buttonsInline.add(List.of(
                        new InlineKeyboardButton("📷 Загрузить фото").callbackData("upload_photo:" + orderNum)
                ));

                // Проверяем наличие фото
                List<File> photos = OrderLoader.getOrderPhotos(orderNum);
                if (!photos.isEmpty()) {
                    buttonsInline.add(List.of(
                            new InlineKeyboardButton("\uD83D\uDCF8 Показать фото").callbackData("show_photos:" + orderNum)
                    ));
                }

                // ◀️ Добавим кнопку "Назад"
                buttonsInline.add(List.of(
                        new InlineKeyboardButton("◀️ Назад").callbackData("order:back:" + dateToCheck)
                ));

                Main.getInstance().editMessage(chatId, messageId, info, buttonsInline);

            } else {
                Main.getInstance().editMessage(chatId, messageId, "Заказ не найден.");
            }
            return;
        }

        // Обработка меню отмены
        if (data.startsWith("cancel_menu:")) {
            String orderNum = data.substring("cancel_menu:".length());

            Order selected = OrderLoader.orders.stream()
                    .filter(o -> o.orderNumber != null && o.orderNumber.trim().equals(orderNum))
                    .findFirst()
                    .orElse(null);

            if (selected != null) {
                Long userId = update.callbackQuery().from().id();
                UserData currentUser = UserData.findUserById(userId);
                if(currentUser.getRole().equals("MANAGER")){
                    if(!selected.clientManager.contains(currentUser.getName())){
                        Main.getInstance().sendMessage(chatId, "❌ Извините, у Вас нет доступа, " + selected.clientManager + " ответственный за этот заказ.");
                        return;
                    }
                }
                String info = String.format("""
        🏬 Организация: %s
        📦 Заказ № %s от %s
        🏢 Клиент: %s
        👨‍💼 Менеджер клиента: %s

        📅 Дата доставки: %s
        ⚖️ Вес: %s кг   📦 Объем: %s м3   📏 Макс. габарит: %s м
        💸 Сумма заказа: %s руб

        🚚 Адрес доставки: %s
        📦 Способ доставки: %s
        🏗 Разгрузка и подъем: %s

        👤 Контактное лицо: %s
        📞 Принимает: %s
        📱 Дополнительный номер: %s
        ☎️ Запасной телефон: %s

        💳 Способ оплаты: %s   ✅ Статус оплаты: %s
        💬 Комментарий: %s

        📦 Товарный состав:
        %s
        
        🚚 Водитель: %s
        """,
                        selected.organization != null ? selected.organization : "-",
                        selected.orderNumber != null ? selected.orderNumber : "-",
                        selected.deliveryDate != null ? selected.deliveryDate : "-",
                        selected.client != null ? selected.client : "-",
                        selected.clientManager != null ? selected.clientManager : "-",
                        selected.deliveryDate != null ? selected.deliveryDate : "-",
                        selected.weight != null ? selected.weight : "-",
                        selected.volume != null ? selected.volume : "-",
                        selected.length != null ? selected.length : "-",
                        selected.orderTotal != null ? selected.orderTotal : "-",
                        selected.deliveryAddress != null ? selected.deliveryAddress : "-",
                        selected.deliveryMethod != null ? selected.deliveryMethod : "-",
                        selected.unloading != null ? selected.unloading : "-",
                        selected.contactPerson != null ? selected.contactPerson : "-",
                        selected.recipientPhone != null ? selected.recipientPhone : "-",
                        selected.additionalNumber != null ? selected.additionalNumber : "-", // если есть второй запасной
                        selected.backupPhone != null ? selected.backupPhone : "-",
                        selected.paymentMethod != null ? selected.paymentMethod : "-",
                        selected.paymentStatus != null ? selected.paymentStatus : "-",
                        selected.comment != null ? selected.comment : "-",
                        selected.productDescription != null ? selected.productDescription : "-",
                        selected.driver != null ? selected.driver : "-"
                );
                OrderStatus status = OrderStatus.fromDisplayName(selected.orderStatus);
                // Создаем кнопки для вариантов отмены
                List<List<InlineKeyboardButton>> buttonsInline = new ArrayList<>();
                if(status == null || (status == OrderStatus.RESCHEDULED_BY_STORE ||
                        status == OrderStatus.RESCHEDULED_BY_CLIENT ||
                        status == OrderStatus.HANDED_TO_MANAGER ||
                        status == OrderStatus.PARTIALLY_DELIVERED ||
                        status == OrderStatus.NOT_SHIPPED_NO_SPACE ||
                        status == OrderStatus.NOT_SHIPPED_NO_STOCK||
                        status == OrderStatus.NOT_SHIPPED_NO_INVOICE||
                        status == OrderStatus.NOT_SHIPPED_NOT_PICKED_FROM_DRIVER)) {

                    buttonsInline.add(List.of(
                            new InlineKeyboardButton("✂\uFE0F Частично доставлен").callbackData("OrderStatus:PARTIALLY_DELIVERED:" + orderNum),
                            new InlineKeyboardButton("🛑 Отмена при вручении").callbackData("OrderStatus:CANCELED_AT_HANDOVER:" + orderNum)

                    ));

                    buttonsInline.add(List.of(
                            new InlineKeyboardButton("📞 Отмена по телефону").callbackData("OrderStatus:CANCELED_BY_PHONE:" + orderNum),
                            new InlineKeyboardButton("📵 Не отвечает").callbackData("OrderStatus:NO_RESPONSE:" + orderNum)
                    ));


                    buttonsInline.add(List.of(
                            new InlineKeyboardButton("↩️ Перенос [Список]").callbackData("rescheduled_menu:" + orderNum)
                    ));

                    buttonsInline.add(List.of(
                            new InlineKeyboardButton("📦 Товар не отгружен [Список]").callbackData("not_shipped_menu:" + orderNum)
                    ));
                }

                // Кнопка "Назад" к основному меню заказа
                buttonsInline.add(List.of(
                        new InlineKeyboardButton("◀️ Назад").callbackData("order:" + orderNum)
                ));

                Main.getInstance().editMessage(chatId, messageId, info, buttonsInline);
                return;
            }
        }

        if (data.startsWith("not_shipped_menu:")) {
            String orderNum = data.substring("not_shipped_menu:".length());

            List<List<InlineKeyboardButton>> kb = new ArrayList<>();
            kb.add(List.of(
                    new InlineKeyboardButton("📄 Нет счёта")
                            .callbackData("NotShipped:NO_INVOICE:" + orderNum)
            ));
            kb.add(List.of(
                    new InlineKeyboardButton("📦 Нет товара на складе")
                            .callbackData("NotShipped:NO_STOCK:" + orderNum)
            ));
            kb.add(List.of(
                    new InlineKeyboardButton("🚚 Не влезло в машину")
                            .callbackData("NotShipped:NO_SPACE:" + orderNum)
            ));
            kb.add(List.of(
                    new InlineKeyboardButton("🔄 Не забрал у другого водителя")
                            .callbackData("NotShipped:NOT_PICKED:" + orderNum)
            ));
            kb.add(List.of(
                    new InlineKeyboardButton("◀️ Назад")
                            .callbackData("cancel_menu:" + orderNum)
            ));


            Main.getInstance().editMessage(chatId, messageId,
                    "Выберите причину «Товар не отгружен» для заказа №" + orderNum + ":", kb);
            return;
        }

        if (data.startsWith("rescheduled_menu:")) {
            String orderNum = data.substring("rescheduled_menu:".length());

            List<List<InlineKeyboardButton>> kb = new ArrayList<>();
            kb.add(List.of(
                    new InlineKeyboardButton("👤 По просьбе клиента")
                            .callbackData("OrderStatus:RESCHEDULED_BY_CLIENT:" + orderNum)
            ));
            kb.add(List.of(
                    new InlineKeyboardButton("🏬 По вине магазина")
                            .callbackData("OrderStatus:RESCHEDULED_BY_STORE:" + orderNum)
            ));
            kb.add(List.of(
                    new InlineKeyboardButton("◀️ Назад")
                            .callbackData("cancel_menu:" + orderNum)
            ));

            Main.getInstance().editMessage(chatId, messageId,
                    "Выберите причину переноса для заказа №" + orderNum + ":", kb);
            return;
        }


        // Выбрана конкретная причина из "Товар не отгружен"
        if (data.startsWith("NotShipped:")) {
            String[] parts = data.split(":");
            if (parts.length < 3) {
                Main.getInstance().editMessage(chatId, messageId, "Некорректные данные.");
                return;
            }
            String reasonKey = parts[1]; // NO_INVOICE | NO_STOCK | NO_SPACE | NOT_PICKED
            String orderNum  = parts[2];

            // Сохраним reasonKey во временное сообщение — пойдёт в callback следующего шага
            List<List<InlineKeyboardButton>> askInvoiceKb = new ArrayList<>();
            askInvoiceKb.add(List.of(
                    new InlineKeyboardButton("✅ Да").callbackData("InvoiceIssued:YES:" + reasonKey + ":" + orderNum),
                    new InlineKeyboardButton("❌ Нет").callbackData("InvoiceIssued:NO:" + reasonKey + ":" + orderNum)
            ));
            askInvoiceKb.add(List.of(
                    new InlineKeyboardButton("◀️ Назад").callbackData("not_shipped_menu:" + orderNum)
            ));

            Main.getInstance().editMessage(chatId, messageId,
                    "Выписан ли счёт по заказу №" + orderNum + "?", askInvoiceKb);
            return;
        }

        // Ответ на вопрос о счёте
        if (data.startsWith("InvoiceIssued:")) {
            String[] parts = data.split(":");
            if (parts.length < 4) {
                Main.getInstance().editMessage(chatId, messageId, "Некорректные данные.");
                return;
            }
            String invoiceYesNo = parts[1]; // YES | NO
            String reasonKey    = parts[2]; // NO_INVOICE | NO_STOCK | NO_SPACE | NOT_PICKED
            String orderNum     = parts[3];

            // Найдём заказ
            Order order = OrderLoader.orders.stream()
                    .filter(o -> o.orderNumber != null && o.orderNumber.equals(orderNum))
                    .findFirst()
                    .orElse(null);

            if (order == null) {
                Main.getInstance().editMessage(chatId, messageId, "Заказ не найден.");
                return;
            }

            // Мэппинг reasonKey -> OrderStatus
            OrderStatus selectedStatus;
            switch (reasonKey) {
                case "NO_INVOICE":
                    selectedStatus = OrderStatus.NOT_SHIPPED_NO_INVOICE; break;
                case "NO_STOCK":
                    selectedStatus = OrderStatus.NOT_SHIPPED_NO_STOCK; break;
                case "NO_SPACE":
                    selectedStatus = OrderStatus.NOT_SHIPPED_NO_SPACE; break;
                case "NOT_PICKED":
                    selectedStatus = OrderStatus.NOT_SHIPPED_NOT_PICKED_FROM_DRIVER; break;
                default:
                    Main.getInstance().editMessage(chatId, messageId, "Неизвестная причина.");
                    return;
            }

            Long userId = update.callbackQuery().from().id();
            UserData currentUser = UserData.findUserById(userId);
            String driverName = currentUser != null ? currentUser.getName() : "Неизвестный водитель";
            String invoiceStr = "Счёт: " + ("YES".equals(invoiceYesNo) ? "выписан" : "не выписан");

            // Текст для менеджера
            String notifyManagerText = String.format(
                    "🚨 %s запросил отметку «%s %s» по заказу №%s\n%s\n\nПодтвердите или отклоните:",
                    driverName,
                    OrderStatus.getEmojiByStatus(selectedStatus),
                    selectedStatus.getDisplayName(),
                    orderNum,
                    invoiceStr
            );

            // Коллбэки менеджера (как у существующей логики отмен)
            String simplifiedOrderNum = orderNum.split(" ")[0];
            String confirmCallback = String.format("ManagerConfirm:%s:%s:%d",
                    selectedStatus.name(), simplifiedOrderNum, userId);
            String rejectCallback  = String.format("ManagerReject:%s:%d",
                    simplifiedOrderNum, userId);

            InlineKeyboardMarkup managerKb = new InlineKeyboardMarkup();
            managerKb.addRow(
                    new InlineKeyboardButton("✅ Подтвердить").callbackData(confirmCallback),
                    new InlineKeyboardButton("❌ Отклонить").callbackData(rejectCallback)
            );

            // Найдём нужного менеджера по order.clientManager
            String managerName = "";
            boolean managerNotified = false;
            for (UserData user : Main.users) {
                if (user.getRole() != null && user.getRole().equalsIgnoreCase("MANAGER")) {
                    if (order.clientManager != null && order.clientManager.contains(user.getName())) {
                        Main.getInstance().sendMessage(user.getId(), notifyManagerText, managerKb);
                        managerNotified = true;
                        managerName = user.getName();
                    }
                }
            }

            if (managerNotified) {
                Main.getInstance().editMessage(chatId, messageId,
                        "Запрос по причине «" + selectedStatus.getDisplayName() + "» отправлен менеджеру [" + managerName + "]. Ожидайте решения.");

                // Уведомим логистов/админов
                for (UserData user : Main.users) {
                    if (user.getRole() != null) {
                        String role = user.getRole().toUpperCase();
                        if (role.equals("LOGISTIC") || role.equals("ADMIN")) {
                            if (user.getId() != null) {
                                Main.getInstance().sendMessage(
                                        user.getId(),
                                        "Водитель " + driverName + " по заказу " + orderNum +
                                                " отправил запрос: " + selectedStatus.getDisplayName() + " (" + invoiceStr + ")" +
                                                " менеджеру " + managerName
                                );
                            }
                        }
                    }
                }

                // Локально пометим выбранный статус и передадим «Менеджеру»
                order.orderStatus = selectedStatus.getDisplayName();
                OrderStatusUpdater.updateOrderStatus(order.orderNumber, OrderStatus.HANDED_TO_MANAGER.getDisplayName());
                ManagerMenu.ManagerRequestStore.startTimer(managerName, orderNum, selectedStatus);

            } else {
                Main.getInstance().editMessage(chatId, messageId,
                        "Не удалось найти менеджера для подтверждения. Попробуйте позже.");
            }
            return;
        }



        if (data.startsWith("OrderStatus:")) {
            String[] parts = data.split(":");
            if (parts.length < 3) {
                Main.getInstance().editMessage(chatId, messageId, "Некорректные данные.");
                return;
            }

            String statusKey = parts[1];
            String orderNum = parts[2];

            OrderStatus status;
            try {
                status = OrderStatus.valueOf(statusKey);
            } catch (IllegalArgumentException e) {
                Main.getInstance().editMessage(chatId, messageId, "Неизвестный статус.");
                return;
            }

            Order order = OrderLoader.orders.stream()
                    .filter(o -> o.orderNumber != null && o.orderNumber.equals(orderNum))
                    .findFirst()
                    .orElse(null);

            if (order == null) {
                Main.getInstance().editMessage(chatId, messageId, "Заказ не найден.");
                return;
            }

            Long userId = update.callbackQuery().from().id();
            UserData currentUser = UserData.findUserById(userId);

            // Если статус "Доставлено" - запрашиваем подтверждение
            if (status == OrderStatus.DELIVERED) {
                List<List<InlineKeyboardButton>> confirmButtons = new ArrayList<>();
                confirmButtons.add(List.of(
                        new InlineKeyboardButton("✅ Подтвердить").callbackData("ConfirmStatus:DELIVERED:" + orderNum),
                        new InlineKeyboardButton("❌ Отменить").callbackData("order:" + orderNum)
                ));

                Main.getInstance().editMessage(chatId, messageId,
                        "Вы уверены, что заказ №" + orderNum + " доставлен?\n\n" +
                                "После подтверждения статус будет изменен.",
                        confirmButtons);
                return;
            }

            // Если это статус отмены - отправляем запрос менеджеру
            // Статусы, которые требуют подтверждения менеджером
            if (status == OrderStatus.NO_RESPONSE
                    || status == OrderStatus.CANCELED_BY_PHONE
                    || status == OrderStatus.CANCELED_AT_HANDOVER
                    || status == OrderStatus.RESCHEDULED_BY_CLIENT
                    || status == OrderStatus.RESCHEDULED_BY_STORE
                    || status == OrderStatus.PARTIALLY_DELIVERED) {

                String driverName = currentUser != null ? currentUser.getName() : "Неизвестный водитель";

                String managerMessage;
                if (status == OrderStatus.RESCHEDULED_BY_CLIENT || status == OrderStatus.RESCHEDULED_BY_STORE) {
                    managerMessage = String.format(
                            "🚨 %s запросил перенос заказа №%s\nПодтвердите или отклоните:",
                            driverName, orderNum
                    );
                } else if (status == OrderStatus.PARTIALLY_DELIVERED) {
                    managerMessage = String.format(
                            "🚨 %s запросил отметку «%s %s» по заказу №%s\n\nПодтвердите или отклоните:",
                            driverName,
                            OrderStatus.getEmojiByStatus(status),
                            status.getDisplayName(),
                            orderNum
                    );
                } else {
                    managerMessage = String.format(
                            "🚨 %s запросил отмену заказа №%s\nПричина: %s %s\n\nПодтвердите или отклоните:",
                            driverName,
                            orderNum,
                            OrderStatus.getEmojiByStatus(status),
                            status.getDisplayName()
                    );
                }

                String simplifiedOrderNum = orderNum.split(" ")[0];

                String confirmCallback = String.format(
                        "ManagerConfirm:%s:%s:%d",
                        status.name(), simplifiedOrderNum, userId
                );
                String rejectCallback = String.format(
                        "ManagerReject:%s:%d",
                        simplifiedOrderNum, userId
                );

                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                        .addRow(
                                new InlineKeyboardButton("✅ Подтвердить").callbackData(confirmCallback),
                                new InlineKeyboardButton("❌ Отклонить").callbackData(rejectCallback)
                        );

                String manager = "";
                boolean managerNotified = false;
                for (UserData user : Main.users) {
                    if (user.getRole() != null && user.getRole().equalsIgnoreCase("MANAGER")) {
                        if (order.clientManager != null && order.clientManager.contains(user.getName())) {
                            Main.getInstance().sendMessage(user.getId(), managerMessage, keyboard);
                            managerNotified = true;
                            manager = user.getName();
                        }
                    }
                }

                if (managerNotified) {
                    String sentMsg;
                    if (status == OrderStatus.RESCHEDULED_BY_CLIENT || status == OrderStatus.RESCHEDULED_BY_STORE) {
                        sentMsg = "Запрос на перенос заказа №" + orderNum + " отправлен менеджеру [" + manager + "]. Ожидайте подтверждения.";
                    } else if (status == OrderStatus.PARTIALLY_DELIVERED) {
                        sentMsg = "Запрос на отметку «Частично доставлен» по заказу №" + orderNum + " отправлен менеджеру [" + manager + "]. Ожидайте подтверждения.";
                    } else {
                        sentMsg = "Запрос на отмену заказа №" + orderNum + " отправлен менеджеру [" + manager + "]. Ожидайте подтверждения.";
                    }
                    Main.getInstance().editMessage(chatId, messageId, sentMsg);

                    for (UserData user : Main.users) {
                        if (user.getRole() != null) {
                            String role = user.getRole().toUpperCase();
                            if (role.equals("LOGISTIC") || role.equals("ADMIN")) {
                                if (user.getId() != null) {
                                    Main.getInstance().sendMessage(
                                            user.getId(),
                                            "Водитель " + driverName + " по заказу " + orderNum +
                                                    " отправил запрос: " + status.getDisplayName() + " менеджеру " + manager
                                    );
                                }
                            }
                        }
                    }

                    order.orderStatus = status.getDisplayName();
                    OrderStatusUpdater.updateOrderStatus(order.orderNumber, OrderStatus.HANDED_TO_MANAGER.getDisplayName());
                } else {
                    Main.getInstance().editMessage(chatId, messageId,
                            "Не удалось найти менеджера для подтверждения. Попробуйте позже.");
                }
                return;
            }


            String driverName = currentUser != null ? currentUser.getName() : "Неизвестный пользователь";
            String notifyText = String.format(
                    "🚨 %s изменил статус заказа №%s на %s %s",
                    driverName,
                    orderNum,
                    OrderStatus.getEmojiByStatus(status),
                    status.getDisplayName()
            );

            // Уведомляем логистов и админов
            for (UserData user : Main.users) {
                if (user.getRole() != null) {
                    String role = user.getRole().toUpperCase();
                    if (role.equals("LOGISTIC") || role.equals("ADMIN")) {
                        if(user.getId() == null) return;
                        Main.getInstance().sendMessage(user.getId(), notifyText);
                    }
                }
            }

            // Обновляем список заказов
            if (OrderLoader.orders.isEmpty()) {
                Main.getInstance().editMessage(chatId, messageId, "Нет доступных заказов.");
                return;
            }

            if (currentUser != null &&
                    (currentUser.getRole().equals("LOGISTIC") ||
                            currentUser.getRole().equals("ADMIN") ||
                            currentUser.getRole().equals("MANAGER"))) {
                OrderLoader.drivers(update);
                return;
            }

            List<List<InlineKeyboardButton>> buttonsInline = OrderLoader.buildOrderButtons(OrderLoader.orders,
                    currentUser != null ? currentUser.getName() : "", dateToCheck);

            if (buttonsInline.isEmpty()) {
                Main.getInstance().editMessage(chatId, messageId, "Нет доступных заказов.");
                return;
            }

                Main.getInstance().editMessage(chatId, messageId, "Выберите заказ:", buttonsInline);

        }

// Обработка подтверждения доставки
        if (data.startsWith("ConfirmStatus:")) {
            String[] parts = data.split(":");
            if (parts.length < 3) {
                Main.getInstance().editMessage(chatId, messageId, "Некорректные данные.");
                return;
            }

            String statusKey = parts[1];
            String orderNum = parts[2];

            OrderStatus status;
            try {
                status = OrderStatus.valueOf(statusKey);
            } catch (IllegalArgumentException e) {
                Main.getInstance().editMessage(chatId, messageId, "Неизвестный статус.");
                return;
            }

            Order order = OrderLoader.orders.stream()
                    .filter(o -> o.orderNumber != null && o.orderNumber.equals(orderNum))
                    .findFirst()
                    .orElse(null);

            if (order == null) {
                Main.getInstance().editMessage(chatId, messageId, "Заказ не найден.");
                return;
            }
            OrderStatus currentStatus = OrderStatus.fromDisplayName(order.orderStatus);

            if (!order.orderStatus.isEmpty() &&

                    !(currentStatus == OrderStatus.RESCHEDULED_BY_STORE ||
                            currentStatus == OrderStatus.RESCHEDULED_BY_CLIENT ||
                            currentStatus == OrderStatus.HANDED_TO_MANAGER ||
                            currentStatus == OrderStatus.PARTIALLY_DELIVERED ||
                            currentStatus == OrderStatus.NOT_SHIPPED_NO_SPACE ||
                            currentStatus == OrderStatus.NOT_SHIPPED_NO_STOCK||
                            currentStatus == OrderStatus.NOT_SHIPPED_NO_INVOICE||
                            currentStatus == OrderStatus.NOT_SHIPPED_NOT_PICKED_FROM_DRIVER)) {
                Main.getInstance().editMessage(chatId, messageId, "Заказ уже имеет статус.");
                return;
            }

            order.orderStatus = status.getDisplayName();
            OrderStatusUpdater.updateOrderStatus(order.orderNumber, order.orderStatus);
            OrderStatusUpdater.updateWebOrderStatus(order.webOrderNumber, status.getCode());

            Long userId = update.callbackQuery().from().id();
            UserData currentUser = UserData.findUserById(userId);
            String driverName = currentUser != null ? currentUser.getName() : "Неизвестный пользователь";

            String notifyText = String.format(
                    "✅ Заказ №%s подтвержден как доставленный\n" +
                            "Изменил статус: %s",
                    orderNum,
                    driverName
            );

            ReportManager.updateRouteStats(currentUser, dateToCheck);

            // Уведомляем логистов, админов и менеджеров
            for (UserData user : Main.users) {
                if (user.getRole() != null) {
                    String role = user.getRole().toUpperCase();
                    if (role.equals("LOGISTIC") || role.equals("ADMIN") || role.equals("MANAGER")) {
                        Main.getInstance().sendMessage(user.getId(), notifyText);
                    }
                }
            }

            if (status == OrderStatus.DELIVERED) {
                Main.pendingPhotoUpload.put(update.callbackQuery().from().id(), orderNum);
                Main.getInstance().editMessage(chatId, messageId,
                        "📸 Заказ №" + orderNum + " отмечен как доставленный.\nПожалуйста, загрузите фото подтверждения.");
                return;
            }

            // Возвращаемся к списку заказов
            if (currentUser != null &&
                    (currentUser.getRole().equals("LOGISTIC") ||
                            currentUser.getRole().equals("ADMIN") ||
                            currentUser.getRole().equals("MANAGER"))) {
                OrderLoader.drivers(update);
            } else {
                List<List<InlineKeyboardButton>> buttonsInline = OrderLoader.buildOrderButtons(OrderLoader.orders,
                        currentUser != null ? currentUser.getName() : "", dateToCheck);

                if (buttonsInline.isEmpty()) {
                    Main.getInstance().editMessage(chatId, messageId, "Нет доступных заказов.");
                    return;
                }

                    Main.getInstance().editMessage(chatId, messageId, "Выберите заказ:", buttonsInline);

            }
        }
    }

}
