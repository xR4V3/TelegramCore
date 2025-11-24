package events;

import com.pengrad.telegrambot.model.Document;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import core.Main;
import modules.OrderLoader;
import modules.ReturnsManager;
import modules.Routes;
import ru.xr4v3.bot.events.TelegramEvent;
import ru.xr4v3.bot.events.annotations.OnMessage;
import utils.UserData;

public class PhotoEvent implements TelegramEvent {

    @OnMessage
    public void onPhotoMessage(Update update) {
        if (update.message() == null) return;

        Message message = update.message();
        Long chatId = message.chat().id();
        Long userId = message.from().id();

        // ====== ВЕТКА: фото для ВОЗВРАТА ======
        String pendingReturn = Main.getInstance().pendingReturnPhotoUpload.get(userId);
        if (pendingReturn != null) {
            boolean saved = false;

            // 1) Фото как "photo"
            PhotoSize[] ph = message.photo();
            if (ph != null && ph.length > 0) {
                String fileId = ph[ph.length - 1].fileId(); // самое большое
                OrderLoader.saveReturnPhotoToLocal(fileId, pendingReturn);
                saved = true;
            }
            // 2) Фото как "document"
            else if (message.document() != null
                    && message.document().mimeType() != null
                    && message.document().mimeType().startsWith("image/")) {
                String fileId = message.document().fileId();
                OrderLoader.saveReturnPhotoToLocal(fileId, pendingReturn);
                saved = true;
            }

            // снимем ожидание в любом случае
            Main.getInstance().pendingReturnPhotoUpload.remove(userId);

            // короткий фидбек
            if (saved) {
                Main.getInstance().sendMessage(chatId, "✅ Фото прикреплено к возврату №" + pendingReturn);
            } else {
                Main.getInstance().sendMessage(chatId, "⚠️ Пожалуйста, отправьте изображение (фото или файл) для возврата №" + pendingReturn + ".");
            }

            // Всегда возвращаем в меню возвратов (свои)
            ReturnsManager.openFromButton(chatId, userId);
            return;
        }

        // ====== ВЕТКА: фото для ЗАКАЗА ======
        if (!Main.pendingPhotoUpload.containsKey(userId)) return;
        String orderNum = Main.pendingPhotoUpload.remove(userId);

        boolean saved = false;

        // 1) Фото как "photo"
        PhotoSize[] photoArray = message.photo();
        if (photoArray != null && photoArray.length > 0) {
            PhotoSize largestPhoto = photoArray[photoArray.length - 1];
            String fileId = largestPhoto.fileId();
            OrderLoader.savePhotoToLocal(fileId, orderNum);
            Main.getInstance().sendMessage(userId, "✅ Фото получено для заказа №" + orderNum);
            saved = true;
        }

        // 2) Фото как "document"
        Document doc = message.document();
        if (!saved && doc != null && doc.mimeType() != null && doc.mimeType().startsWith("image/")) {
            String fileId = doc.fileId();
            OrderLoader.savePhotoToLocal(fileId, orderNum);
            Main.getInstance().sendMessage(userId, "✅ Изображение-файл получено для заказа №" + orderNum);
            saved = true;
        }

        if (!saved) {
            Main.getInstance().sendMessage(userId, "⚠️ Пожалуйста, отправьте изображение как фото или файл.");
        }

        // Всегда возвращаем к водителю (его маршруты)
        UserData me = UserData.findUserById(userId);
        if (me != null && me.getName() != null) {
            // показываем список дат/маршрутов водителя; из него легко вернуться к заказу
            Routes.showDriverRoutes(me.getName(), update);
        }
    }
}
