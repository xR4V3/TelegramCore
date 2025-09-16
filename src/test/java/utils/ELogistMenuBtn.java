package utils;

/**
 * Enum с текстами кнопок меню Логиста
 */
public enum ELogistMenuBtn {
    DRIVERS("Водители\uD83D\uDE9B"),      // Эмодзи: грузовик
    SALARIES("Зарплаты\uD83D\uDCB8"),     // Эмодзи: летящие деньги
    FINES("Штрафы\uD83D\uDEA8"),          // Эмодзи: треугольный флаг
    ROUTES("Маршруты\uD83D\uDDFA");       // Эмодзи: карта

    private final String buttonText;

    ELogistMenuBtn(String buttonText) {
        this.buttonText = buttonText;
    }

    public String getButtonText() {
        return buttonText;
    }
}
