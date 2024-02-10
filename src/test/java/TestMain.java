import ru.xr4v3.bot.TelegramCore;
import ru.xr4v3.bot.events.EventHandler;

public class TestMain extends TelegramCore {

    public TestMain(String token) {
        super(token);
    }

    private static TestMain instance;

    public static void main(String[] args){
        if (instance == null) {
            instance = new TestMain("6473821941:AAGatoh7pA0HH5znmB8tFWDg45Vk3BC5KQw");
        }
            EventHandler.getInstance().registerEventHandler(new TestEvents());
            System.out.println("Bot Start by TelegramCore!");
    }

    public static synchronized TestMain getInstance() {
        return instance;
    }
}
