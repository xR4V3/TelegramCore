package modules;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import core.Main;
import modules.parsers.lemana.LemanaRestScheduler;
import modules.parsers.saturn.SaturnAPI;
import utils.UserData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParsersManager {

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
        if (data.startsWith("parsers:")) {
            if (!"ADMIN".equalsIgnoreCase(user.getRole()) &&
                    !"OPERATOR".equalsIgnoreCase(user.getRole())) {
                Main.getInstance().sendMessage(chatId, "⚠️ У вас недостаточно прав!");
                return;
            }
        }

        // === главное меню парсеров ===
        if (data.startsWith("parsers:list")) {
            List<List<InlineKeyboardButton>> kb = new ArrayList<>();

            kb.add(Collections.singletonList(
                    new InlineKeyboardButton("ЛеманаПро").callbackData("parsers:lemanapro")
            ));

            kb.add(Collections.singletonList(
                    new InlineKeyboardButton("Сатурн").callbackData("parsers:saturn")
            ));

            Main.getInstance().sendInlineKeyboard(
                    chatId,
                    kb,
                    "Выберите раздел:"
            );
            return;
        }

        // ===================== ЛЕМАНА =====================

        // === меню ЛеманаПро ===
        if (data.equals("parsers:lemanapro")) {
            List<List<InlineKeyboardButton>> kb = new ArrayList<>();

            // Кнопка открытия подменю парсера остатков/цен
            kb.add(Collections.singletonList(
                    new InlineKeyboardButton("📊 Парсер цен и остатков")
                            .callbackData("parsers:lemanapro:rest_menu")
            ));

            kb.add(Collections.singletonList(
                    new InlineKeyboardButton("🎯 Запарсить точечно")
                            .callbackData("parsers:lemanapro:single")
            ));
            kb.add(Collections.singletonList(
                    new InlineKeyboardButton("📂 Запарсить каталог")
                            .callbackData("parsers:lemanapro:category")
            ));

            Main.getInstance().sendInlineKeyboard(
                    chatId,
                    kb,
                    "Выберите режим:"
            );
            return;
        }

        // === ПОДМЕНЮ "Парсер цен и остатков" ЛеманаПро ===
        if (data.equals("parsers:lemanapro:rest_menu")) {

            List<List<InlineKeyboardButton>> kb = new ArrayList<>();

            boolean autoEnabled = LemanaRestScheduler.isAutoEnabled();
            String autoText = autoEnabled
                    ? "🟢 Автопарсер: ВКЛ (бесконечно)"
                    : "🔴 Автопарсер: ВЫКЛ";

            // 1) Тумблер ВКЛ/ВЫКЛ
            kb.add(Collections.singletonList(
                    new InlineKeyboardButton(autoText)
                            .callbackData("parsers:lemanapro:auto_toggle")
            ));

            // Можно сразу показать краткий статус под кнопками
            LemanaRestScheduler.LemanaRestStats stats = LemanaRestScheduler.getStats();
            StringBuilder status = new StringBuilder();
            status.append("Статус автопарсера цен/остатков:\n");
            status.append(autoEnabled ? "🟢 Включен\n" : "🔴 Выключен\n");

            if (stats.lastRunEnd != null) {
                status.append("Последний парс: ").append(stats.lastRunEnd).append("\n");
            } else {
                status.append("Последний парс: ещё не запускался\n");
            }

            status.append("Запусков за сегодня: ").append(stats.runsToday).append("\n");
            if (stats.runsToday > 0 && stats.avgDurationMs > 0) {
                double minutes = stats.avgDurationMs / 60000.0; // из ms в минуты
                String minutesStr = String.format("%.1f", minutes);
                status.append("Среднее время парса: ")
                        .append(minutesStr)
                        .append(" мин.\n");
            }

            Main.getInstance().sendInlineKeyboard(
                    chatId,
                    kb,
                    "Парсер цен и остатков ЛеманаПро:\n\n" + status
            );
            return;
        }

        // === переключатель автопарсера цен/остатков ЛеманаПро ===
        if (data.equals("parsers:lemanapro:auto_toggle")) {
            boolean enabled = LemanaRestScheduler.toggleAuto();

            String msg = enabled
                    ? "🟢 Автопарсер цен и остатков ЛеманаПро включён. Он теперь будет запускаться бесконечно подряд."
                    : "🔴 Автопарсер цен и остатков ЛеманаПро выключен.";

            Main.getInstance().sendMessage(chatId, msg);

            // Обновляем подменю
            List<List<InlineKeyboardButton>> kb = new ArrayList<>();

            boolean autoEnabled = LemanaRestScheduler.isAutoEnabled();
            String autoText = autoEnabled
                    ? "🟢 Автопарсер: ВКЛ (бесконечно)"
                    : "🔴 Автопарсер: ВЫКЛ";

            kb.add(Collections.singletonList(
                    new InlineKeyboardButton(autoText)
                            .callbackData("parsers:lemanapro:auto_toggle")
            ));

            LemanaRestScheduler.LemanaRestStats stats =
                    LemanaRestScheduler.getStats();

            StringBuilder status = new StringBuilder();
            status.append("Статус автопарсера цен/остатков:\n");
            status.append(autoEnabled ? "🟢 Включен\n" : "🔴 Выключен\n");
            if (stats.lastRunEnd != null) {
                status.append("Последний парс: ").append(stats.lastRunEnd).append("\n");
            } else {
                status.append("Последний парс: ещё не запускался\n");
            }
            status.append("Запусков за сегодня: ").append(stats.runsToday).append("\n");
            if (stats.runsToday > 0 && stats.avgDurationMs > 0) {
                double minutes = stats.avgDurationMs / 60000.0; // из ms в минуты
                String minutesStr = String.format("%.1f", minutes);
                status.append("Среднее время парса: ")
                        .append(minutesStr)
                        .append(" мин.\n");
            }

            Main.getInstance().sendInlineKeyboard(
                    chatId,
                    kb,
                    "Парсер цен и остатков ЛеманаПро:\n\n" + status
            );

            return;
        }

        // === точечный парс ЛеманаПро: ждём список от юзера ===
        if (data.equals("parsers:lemanapro:single")) {
            user.setPendingAction("LEMANAPRO_SINGLE");
            Main.getInstance().sendMessage(chatId,
                    "Отправьте список артикулов или ссылок, каждый с новой строки.\n" +
                            "Ссылка должна быть вида: https://b2b.lemanapro.ru/product/...");
            return;
        }

        // === парс каталога ЛеманаПро: ждём URL каталога ===
        if (data.equals("parsers:lemanapro:category")) {
            user.setPendingAction("LEMANAPRO_CATEGORY");
            Main.getInstance().sendMessage(chatId,
                    "Отправьте ссылку на каталог ЛеманаПро.\n" +
                            "Ссылка должна быть вида: https://b2b.lemanapro.ru/catalog-fam/...");
            return;
        }

        // ===================== САТУРН =====================

        // меню Сатурн
        if (data.equals("parsers:saturn")) {
            List<List<InlineKeyboardButton>> kb = new ArrayList<>();

            // Парсер по vendors.xml (аналог Лемана-парсера цен/остатков, но без авто)
            kb.add(Collections.singletonList(
                    new InlineKeyboardButton("📊 Парсер цен и остатков")
                            .callbackData("parsers:saturn:vendors")
            ));

            // точечный парс
            kb.add(Collections.singletonList(
                    new InlineKeyboardButton("🎯 Запарсить точечно")
                            .callbackData("parsers:saturn:single")
            ));

            // парс каталога
            kb.add(Collections.singletonList(
                    new InlineKeyboardButton("📂 Запарсить каталог")
                            .callbackData("parsers:saturn:category")
            ));

            Main.getInstance().sendInlineKeyboard(
                    chatId,
                    kb,
                    "Парсер Сатурн. Выберите режим:"
            );
            return;
        }

        // запуск парсера Saturn по vendors.xml (разовый)
        if (data.equals("parsers:saturn:vendors")) {
            Main.getInstance().sendMessage(chatId,
                    "Запускаю парсер Сатурн по vendors.xml. Это может занять некоторое время.");

            Main.getInstance().getExecutor().submit(() -> {
                try {
                    File file = SaturnAPI.startSaturnParseVendors();

                    if (file != null && file.exists()) {
                        Main.getInstance().sendDocument(
                                chatId,
                                file,
                                "Вот файл с товарами Сатурн по vendors.xml ✅"
                        );
                    } else {
                        Main.getInstance().sendMessage(chatId,
                                "❌ Не удалось сформировать файл для Сатурн по vendors.xml.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Main.getInstance().sendMessage(chatId,
                            "❌ Ошибка при выполнении парсера Сатурн: " + e.getMessage());
                }
            });
            return;
        }

        // точечный парс Сатурн
        if (data.equals("parsers:saturn:single")) {
            user.setPendingAction("SATURN_SINGLE");
            Main.getInstance().sendMessage(chatId,
                    "Отправьте список ссылок или кодов товаров Сатурн, каждый с новой строки.\n" +
                            "Ссылку на товар: https://msk.saturn.net/product/...");
            return;
        }

        // парс каталога Сатурн
        if (data.equals("parsers:saturn:category")) {
            user.setPendingAction("SATURN_CATEGORY");
            Main.getInstance().sendMessage(chatId,
                    "Отправьте ссылку на каталог Сатурн.\n" +
                            "Пример: https://msk.saturn.net/catalog/...");
        }
    }
}
