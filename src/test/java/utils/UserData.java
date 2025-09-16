package utils;

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


}

