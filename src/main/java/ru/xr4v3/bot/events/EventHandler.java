package ru.xr4v3.bot.events;

import java.util.ArrayList;
import java.util.List;

public class EventHandler {

    private static EventHandler instance;
    private final List<TelegramEvent> eventHandlers = new ArrayList<>();

    private EventHandler() {}

    public static EventHandler getInstance() {
        if (instance == null) {
            instance = new EventHandler();
        }
        return instance;
    }

    public void registerEventHandler(TelegramEvent handler) {
        eventHandlers.add(handler);
    }

    public List<TelegramEvent> getEventHandlers() {
        return eventHandlers;
    }

}
