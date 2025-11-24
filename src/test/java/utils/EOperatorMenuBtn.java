package utils;

public enum EOperatorMenuBtn {

    ROUTES("Заказы🗺"),
    PARSERS("Парсеры©️");

    private final String buttonText;

    EOperatorMenuBtn(String buttonText) {
        this.buttonText = buttonText;
    }

    public String getButtonText() {
        return buttonText;
    }

}
