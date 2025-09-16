package utils;

/**
 * Enum с текстами кнопок меню Админа
 */
public enum EAdminMenuBtn {
    USERS("Пользователи 👥"),
    ORDERS("Водители\uD83D\uDE9B"),
    ROUTES("Маршруты 🗺️"), // новая кнопка
    OTHER("Другое ✨");

    private final String buttonText;

    EAdminMenuBtn(String buttonText) {
        this.buttonText = buttonText;
    }

    public String getButtonText() {
        return buttonText;
    }
}
