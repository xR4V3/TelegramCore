package core;

import Menus.*;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import events.GlobalEvents;
import events.PhotoEvent;
import modules.OrderLoader;
import modules.ReportManager;
import modules.ReturnsDeadlineNotifier;
import modules.parsers.lemana.LemanaRestScheduler;
import ru.xr4v3.bot.TelegramCore;
import ru.xr4v3.bot.events.EventHandler;
import utils.Settings;
import utils.UserData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends TelegramCore {

    public Main(String token) {
        super(token);
    }

    public static Map<Long, String> pendingPhotoUpload = new HashMap<>();
    public static Map<Long, String> waitingForOrderNumber = new HashMap<>();
    public static Map<Long, String> pendingReturnPhotoUpload = new HashMap<>();

    private static Main instance;
    public static List<UserData> users;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public static void main(String[] args) throws IOException {
        // Загружаем настройки
        Settings.load("settings.json");

        if (instance == null) {
            instance = new Main(Settings.get().getBotToken());
        }

        EventHandler.getInstance().registerEventHandler(new GlobalEvents(
                new DriverMenu(),
                new LogistMenu(),
                new ManagerMenu(),
                new AdminMenu(),
                new CourierMenu(),
                new OperatorMenu()
        ));
        EventHandler.getInstance().registerEventHandler(new PhotoEvent());

        System.out.println("Bot Start by TelegramCore!");
        users = loadUsersFromFile();

        UserData.saveUsersToFile();
        OrderLoader.startAutoReload(60);
        ReportManager.startReportScheduler();
        ReturnsDeadlineNotifier.startSchedulerAt(java.time.LocalTime.of(8, 0)); // каждый день в 08:00
        //LemanaRestScheduler.init(); ПАРСЕРС
    }

    public static synchronized Main getInstance() {
        return instance;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public static List<UserData> loadUsersFromFile() {
        File file = new File("users.json");
        if (!file.exists() || file.length() == 0) {
            return new ArrayList<>();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.readValue(file, new TypeReference<List<UserData>>() {});
        } catch (Exception e) {
            System.err.println("Error loading users: " + e.getMessage());
            return new ArrayList<>();
        }
    }

}

