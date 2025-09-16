package events;

import com.pengrad.telegrambot.model.Document;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import core.Main;
import modules.OrderLoader;
import ru.xr4v3.bot.events.TelegramEvent;
import ru.xr4v3.bot.events.annotations.OnMessage;

public class PhotoEvent implements TelegramEvent {

    @OnMessage
    public void onPhotoMessage(Update update) {
        if(update.message() == null) return;

        Message message = update.message();
        Long userId = message.from().id();

        if (!Main.pendingPhotoUpload.containsKey(userId)) return;
        String orderNum = Main.pendingPhotoUpload.remove(userId);

        boolean found = false;

        PhotoSize[] photoArray = message.photo();
        if (photoArray != null && photoArray.length > 0) {
            PhotoSize largestPhoto = photoArray[photoArray.length - 1];
            String fileId = largestPhoto.fileId();
            OrderLoader.savePhotoToLocal(fileId, orderNum);
            Main.getInstance().sendMessage(userId, "✅ Фото получено для заказа №" + orderNum);
            found = true;
        }


        Document doc = message.document();
        if (doc != null && doc.mimeType() != null && doc.mimeType().startsWith("image/")) {
            String fileId = doc.fileId();
            OrderLoader.savePhotoToLocal(fileId, orderNum);
            Main.getInstance().sendMessage(userId, "✅ Изображение-файл получено для заказа №" + orderNum);
            found = true;
        }

        if (!found) {
            Main.getInstance().sendMessage(userId, "⚠️ Пожалуйста, отправьте изображение как фото или файл.");
        }
    }

}
