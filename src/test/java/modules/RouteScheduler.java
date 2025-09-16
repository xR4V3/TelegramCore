package modules;

import core.Main;
import utils.UserData;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.concurrent.*;

public class RouteScheduler {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static void scheduleRouteAutoAccept(UserData driver, LocalDate routeDate, long delayMinutes) {

            // Напоминание за 5 минут
            scheduler.schedule(() -> {
                if (!driver.getRouteStatus(routeDate).isConfirmed() && driver.getRouteStatus(routeDate).isRequested()) {
                    if(routeDate.getDayOfWeek() == DayOfWeek.SATURDAY){
                        Main.getInstance().sendMessage(driver.getId(),
                                "⏳ Осталось 5 минут до автоматического принятия маршрута на " + routeDate + "," + routeDate.plusDays(2) + ".");
                    } else {
                        Main.getInstance().sendMessage(driver.getId(),
                                "⏳ Осталось 5 минут до автоматического принятия маршрута на " + routeDate + ".");
                    }

                }
            }, delayMinutes - 5, TimeUnit.MINUTES);

            // Автоматическое принятие
            scheduler.schedule(() -> {
                if (!driver.getRouteStatus(routeDate).isConfirmed() && driver.getRouteStatus(routeDate).isRequested()) {
                    if(routeDate.getDayOfWeek() == DayOfWeek.SATURDAY){
                        driver.getRouteStatus(routeDate).setConfirmed(true);
                        driver.getRouteStatus(routeDate.plusDays(2)).setConfirmed(true);
                        driver.getRouteStatus(routeDate).setRequested(false);
                        driver.getRouteStatus(routeDate.plusDays(2)).setRequested(false);
                        Routes.notifyAdminsAndLogistics(driver, false, true, routeDate);
                        Routes.notifyAdminsAndLogistics(driver, false, true, routeDate.plusDays(2));
                        Main.getInstance().sendMessage(driver.getId(),
                                "✅ Маршрут на " + routeDate + ", " + routeDate.plusDays(2)+ " принят автоматически (время ожидания истекло).");

                    } else {
                        driver.getRouteStatus(routeDate).setConfirmed(true);
                        driver.getRouteStatus(routeDate).setRequested(false);
                        Routes.notifyAdminsAndLogistics(driver, false, true, routeDate);
                        Main.getInstance().sendMessage(driver.getId(),
                                "✅ Маршрут на " + routeDate + " принят автоматически (время ожидания истекло).");
                    }

                }
            }, delayMinutes, TimeUnit.MINUTES);
    }
}

