package ru.xr4v3.bot.events.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class BotScheduler {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Logger logger = Logger.getLogger(BotScheduler.class.getName());

    public static void startAsyncTimer(long delayMillis, Runnable task) {
        // Получаем имя класса, из которого был вызван метод
        String callerClassName = new Throwable().getStackTrace()[1].getClassName();

        logger.info(() -> String.format("Таймер запущен из: %s", callerClassName));

        scheduler.schedule(() -> {
            task.run();
            logger.info(() -> String.format("Таймер завершился в: %s", callerClassName));
            scheduler.shutdown();
            logger.info("Планировщик задач остановлен");
        }, delayMillis, TimeUnit.MILLISECONDS);
    }
}
