package utils;

/**
 * Enum с текстами кнопок меню Логиста
 */
public enum EManagerMenuBtn {
    DRIVERS("Водители\uD83D\uDE9B");

    private final String buttonText;

    EManagerMenuBtn(String buttonText) {
        this.buttonText = buttonText;
    }

    public String getButtonText() {
        return buttonText;
    }
}
