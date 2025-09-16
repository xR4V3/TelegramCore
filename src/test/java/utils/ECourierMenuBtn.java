package utils;

/**
 * Enum с текстами кнопок меню водителя
 */
public enum ECourierMenuBtn {
    ROUTES("Заказы🗺"); // новая кнопка

    private final String buttonText;

    ECourierMenuBtn(String buttonText) {
        this.buttonText = buttonText;
    }

    public String getButtonText() {
        return buttonText;
    }

}
