package utils;

public enum Users {
    UNKNOWN("Неизвестный"),
    ADMIN("Админ"),
    LOGISTIC("Логист"),
    MANAGER("Менеджер"),
    DRIVER("Водитель"),
    COURIER("Курьер"),
    OPERATOR("Оператор");

    private final String displayName;

    Users(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

