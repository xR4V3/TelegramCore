package utils;

/**
 * Enum с текстами кнопок меню водителя
 */
public enum EDriverMenuBtn {
    SALARY("Зарплата💰"),
    RETURNS("Возвраты🔙"),
    ROUTES("Маршруты🗺"); // новая кнопка

    private final String buttonText;

    EDriverMenuBtn(String buttonText) {
        this.buttonText = buttonText;
    }

    public String getButtonText() {
        return buttonText;
    }
}
