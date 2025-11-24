package modules.parsers.lemana;

import core.Main;
import utils.UserData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;

public class LemanaRestScheduler {

    // Один отдельный поток под вечный цикл
    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private static volatile boolean autoEnabled = true;   // по умолчанию включён
    private static Future<?> loopFuture;

    // статистика
    private static volatile LocalDateTime lastRunStart;
    private static volatile LocalDateTime lastRunEnd;
    private static volatile LocalDate statsDate = LocalDate.now();
    private static volatile int runsToday = 0;
    private static volatile long totalDurationMsToday = 0;

    // ================== ПУБЛИЧНЫЕ МЕТОДЫ ==================

    public static synchronized void init() {
        if (autoEnabled && (loopFuture == null || loopFuture.isDone())) {
            startLoop();
        }
    }

    /**
     * Переключить авто-режим.
     * @return новое состояние (true = включён)
     */
    public static synchronized boolean toggleAuto() {
        autoEnabled = !autoEnabled;

        if (autoEnabled) {
            if (loopFuture == null || loopFuture.isDone()) {
                startLoop();
            }
        } else {
            // Просто ставим флаг, цикл сам завершится после текущего парса
            // loopFuture.cancel(false);
        }

        return autoEnabled;
    }

    public static boolean isAutoEnabled() {
        return autoEnabled;
    }

    public static LemanaRestStats getStats() {
        LemanaRestStats s = new LemanaRestStats();
        s.autoEnabled   = autoEnabled;
        s.lastRunStart  = lastRunStart;
        s.lastRunEnd    = lastRunEnd;
        s.runsToday     = runsToday;

        if (runsToday > 0 && totalDurationMsToday > 0) {
            s.avgDurationMs = (double) totalDurationMsToday / runsToday;
        } else {
            s.avgDurationMs = 0;
        }
        return s;
    }

    // ================== ВНУТРЕННЯЯ ЛОГИКА ==================

    private static void startLoop() {
        loopFuture = executor.submit(() -> {
            while (autoEnabled) {
                try {
                    runOnce();
                } catch (Exception e) {
                    e.printStackTrace();
                    // чтобы при ошибке цикл не умирал
                }

                // если хочешь делать микропаузу между кругами, добавь сюда sleep
                // Thread.sleep(5000);
            }
        });
    }

    private static void runOnce() throws Exception {
        lastRunStart = LocalDateTime.now();
        long start = System.currentTimeMillis();

        File file = LemanaAPI.startLemanaParseVendors(); // тяжёлый парсер

        long end = System.currentTimeMillis();
        lastRunEnd = LocalDateTime.now();
        long duration = end - start;

        updateStats(duration);

        System.out.println("[LemanaRestScheduler] Автопарс завершён за " +
                duration + " ms, файл: " + (file != null ? file.getAbsolutePath() : "null"));

        // список "проблемных" артикулов
        List<String> discontinued = LemanaAPI.getLastDiscontinued();
        notifyAdminsAboutDiscontinued(discontinued);
    }

    private static synchronized void updateStats(long durationMs) {
        LocalDate today = LocalDate.now();
        if (!today.equals(statsDate)) {
            statsDate = today;
            runsToday = 0;
            totalDurationMsToday = 0;
        }
        runsToday++;
        totalDurationMsToday += durationMs;
    }

    /**
     * Вместо кучи сообщений — формируем файл со списком артикулов
     * и отправляем его всем админам.
     */
    private static void notifyAdminsAboutDiscontinued(List<String> discontinued) {
        if (discontinued == null || discontinued.isEmpty()) return;

        try {
            // 1) создаём файл
            File exportDir = new File("export");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            File outFile = new File(exportDir, "lemana_discontinued.txt");

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
                bw.write("Список товаров ЛеманаПро, которые возможно сняты с продажи.\n");
                bw.write("Требуется внести изменения в 1С.\n");
                bw.write("Всего артикулов: " + discontinued.size() + "\n\n");

                for (String code : discontinued) {
                    bw.write(code);
                    bw.newLine();
                }
            }

            // 2) шлём файл всем админам
            for (UserData u : Main.users) {
                if (u.getRole() == null) continue;
                String role = u.getRole().toUpperCase();
                if (!"ADMIN".equals(role) && !"OPERATOR".equals(role)) continue;

                Main.getInstance().sendDocument(
                        u.getId(),
                        outFile,
                        "⚠\uFE0F Файл со списком возможных снятых с продажи товаров ЛеманаПро (" +
                                discontinued.size() + " шт.)"
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            // если не смогли отправить файл — можно хотя бы одно короткое сообщение кинуть
            for (UserData u : Main.users) {
                if (u.getRole() == null) continue;
                String role = u.getRole().toUpperCase();
                if (!"ADMIN".equals(role)) continue;
                Main.getInstance().sendMessage(
                        u.getId(),
                        "⚠️ Ошибка при формировании файла со снятыми с продажи товарами: " + e.getMessage()
                );
            }
        }
    }

    // DTO
    public static class LemanaRestStats {
        public boolean autoEnabled;
        public LocalDateTime lastRunStart;
        public LocalDateTime lastRunEnd;
        public int runsToday;
        public double avgDurationMs;
    }
}
