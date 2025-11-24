package utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import core.Main;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserData {

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Long id;
    private String phone;
    private String role;
    private String name;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    private String pendingAction;
    public void setPendingAction(String a) { this.pendingAction = a; }
    public String getPendingAction() { return pendingAction; }


    // Карта статусов маршрутов по дате
    public Map<LocalDate, RouteStatus> routes = new HashMap<>();

    public Map<LocalDate, RouteStatus> getRoutes() {
        return routes;
    }

    public void addRoute(LocalDate date) {
        routes.putIfAbsent(date, new RouteStatus());
        saveUsersToFile();
    }

    public void removeRoute(LocalDate date) {
        if (routes.containsKey(date)) {
            routes.remove(date);
            saveUsersToFile();
        }
    }


    public void setRoutes(Map<LocalDate, RouteStatus> routes) {
        this.routes = routes;
        saveUsersToFile();
    }

    public RouteStatus getRouteStatus(LocalDate date) {
        return routes.getOrDefault(date, new RouteStatus());
    }

    public void setRouteStatus(LocalDate date, RouteStatus status) {
        routes.put(date, status);
        saveUsersToFile();
    }

    private RouteStats routeStats = new RouteStats();

    public RouteStats getRouteStats() { return routeStats; }
    public void setRouteStats(RouteStats routeStats) {
        this.routeStats = routeStats;
        saveUsersToFile();
    }

    public static void saveUsersToFile() {
        if (Main.users == null) {
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            // Сохраняем во временный файл сначала
            File tempFile = new File("users.tmp");
            mapper.writeValue(tempFile, Main.users);

            // Заменяем основной файл
            File mainFile = new File("users.json");
            if (mainFile.exists()) {
                mainFile.delete();
            }
            tempFile.renameTo(mainFile);
        } catch (IOException e) {
            System.err.println("Error saving users: " + e.getMessage());
        }
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.replaceAll("[^0-9]", ""); // удаляем всё кроме цифр

        if (phone.startsWith("8")) {
            phone = "7" + phone.substring(1); // заменяем 8 на 7
        } else if (phone.length() == 11 && phone.startsWith("7")) {
            // уже нормальный формат
        } else if (phone.length() == 10) {
            phone = "7" + phone; // без кода страны
        }

        return phone;
    }


    public static UserData findUserById(Long id) {
        return Main.users.stream()
                .filter(u -> u.getId() != null && u.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static UserData findUserByPhone(String phone) {
        String normalized = normalizePhone(phone);
        return Main.users.stream()
                .filter(u -> u.getPhone() != null && normalizePhone(u.getPhone()).equals(normalized))
                .findFirst()
                .orElse(null);
    }


    public static Users findUserRoleByPhone(String phone) {
        for (UserData u : Main.users) {
            if (u.getPhone().equals(phone)) {
                try {
                    return Users.valueOf(u.getRole().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return Users.UNKNOWN;
                }
            }
        }
        return null;
    }

    public static UserData findUserByName(String name) {
        if (name == null) return null;
        String search = name.trim().toLowerCase();
        return Main.users.stream()
                .filter(u -> u.getName() != null && u.getName().trim().toLowerCase().contains(search))
                .findFirst()
                .orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteStatus {
        private boolean started;
        private boolean finished;
        private boolean requested;
        private boolean confirmed;

        @JsonSetter(nulls = Nulls.AS_EMPTY)
        private List<String> ordersSnapshot;

        public RouteStatus() {}

        public boolean isStarted() { return started; }
        public void setStarted(boolean started) { this.started = started; saveUsersToFile(); }

        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; saveUsersToFile(); }

        public boolean isRequested() { return requested; }
        public void setRequested(boolean requested) { this.requested = requested; saveUsersToFile(); }

        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; saveUsersToFile(); }

        public List<String> getOrdersSnapshot() { return ordersSnapshot; }
        public void setOrdersSnapshot(List<String> snapshot) {
            this.ordersSnapshot = snapshot;
            saveUsersToFile();
        }

        private static String getStatusIcon(RouteStatus status) {
            if (status == null) return "";
            if (status.confirmed) return "✅";
            if (status.requested) return "⏳";
            return "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteStats {
        private Map<LocalDate, DailyStats> dailyStats = new HashMap<>();

        // Статистика за день
        public static class DailyStats {
            private int totalOrders;
            private int completedOrders;
            private double totalOrderValue;
            private double completedOrderValue;
            private LocalDate date;

            // constructors, getters, setters
            public DailyStats() {}

            public DailyStats(LocalDate date) {
                this.date = date;
            }

            public void addOrder(double value) {
                this.totalOrders++;
                this.totalOrderValue += value;
            }

            public void addCompletedOrder(double value) {
                this.completedOrders++;
                this.completedOrderValue += value;
            }
            @JsonIgnore
            public double getCompletionRateByCount() {
                return totalOrders == 0 ? 0 : (double) completedOrders / totalOrders * 100;
            }

            @JsonIgnore
            public double getCompletionRateByValue() {
                return totalOrderValue == 0 ? 0 : completedOrderValue / totalOrderValue * 100;
            }

            public void reset() {
                this.totalOrders = 0;
                this.completedOrders = 0;
                this.totalOrderValue = 0.0;
                this.completedOrderValue = 0.0;
            }

            // Getters and setters
            public int getTotalOrders() { return totalOrders; }
            public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }

            public int getCompletedOrders() { return completedOrders; }
            public void setCompletedOrders(int completedOrders) { this.completedOrders = completedOrders; }

            public double getTotalOrderValue() { return totalOrderValue; }
            public void setTotalOrderValue(double totalOrderValue) { this.totalOrderValue = totalOrderValue; }

            public double getCompletedOrderValue() { return completedOrderValue; }
            public void setCompletedOrderValue(double completedOrderValue) { this.completedOrderValue = completedOrderValue; }

            public LocalDate getDate() { return date; }
            public void setDate(LocalDate date) { this.date = date; }
        }

        public Map<LocalDate, DailyStats> getDailyStats() { return dailyStats; }
        public void setDailyStats(Map<LocalDate, DailyStats> dailyStats) { this.dailyStats = dailyStats; }

        public DailyStats getOrCreateDailyStats(LocalDate date) {
            return dailyStats.computeIfAbsent(date, DailyStats::new);
        }
    }

    // Внутри UserData
    private ManagerStats managerStats = new ManagerStats();
    public ManagerStats getManagerStats() { return managerStats; }
    public void setManagerStats(ManagerStats managerStats) { this.managerStats = managerStats; saveUsersToFile(); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ManagerStats {
        private Map<LocalDate, ManagerDailyStats> daily = new HashMap<>();
        public Map<LocalDate, ManagerDailyStats> getDaily() { return daily; }
        public void setDaily(Map<LocalDate, ManagerDailyStats> daily) { this.daily = daily; }

        public ManagerDailyStats getOrCreate(LocalDate d) { return daily.computeIfAbsent(d, ManagerDailyStats::new); }

        public static class ManagerDailyStats {
            private int accepted;            // принятые запросы (подтверждено)
            private int rejected;            // отклонённые запросы
            private long totalResponseMs;    // суммарное время ответа (мс)
            private int responses;           // количество ответов (для среднего)
            private LocalDate date;

            public ManagerDailyStats() {}
            public ManagerDailyStats(LocalDate date) { this.date = date; }

            public void addAccepted(long durationMs) { accepted++; totalResponseMs += durationMs; responses++; }
            public void addRejected(long durationMs) { rejected++; totalResponseMs += durationMs; responses++; }

            @JsonIgnore
            public double getAvgResponseMinutes() { return responses == 0 ? 0 : (totalResponseMs / 60000.0) / responses; }

            // getters/setters
            public int getAccepted() { return accepted; }
            public void setAccepted(int accepted) { this.accepted = accepted; }
            public int getRejected() { return rejected; }
            public void setRejected(int rejected) { this.rejected = rejected; }
            public long getTotalResponseMs() { return totalResponseMs; }
            public void setTotalResponseMs(long totalResponseMs) { this.totalResponseMs = totalResponseMs; }
            public int getResponses() { return responses; }
            public void setResponses(int responses) { this.responses = responses; }
            public LocalDate getDate() { return date; }
            public void setDate(LocalDate date) { this.date = date; }
        }
    }


}

