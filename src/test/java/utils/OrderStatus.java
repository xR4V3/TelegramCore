package utils;

public enum OrderStatus {
    DELIVERED("Доставлено", "F"),
    NO_RESPONSE("Не отвечает", "O"),
    CANCELED_BY_PHONE("Отмена по телефону", "O"),
    RESCHEDULED("Перенос", ""),
    HANDED_TO_MANAGER("Передано менеджеру", ""),
    CANCELED_AT_HANDOVER("Отмена при вручении", "O"),
    NOT_SHIPPED_NO_INVOICE("Товар не отгружен: нет счёта", ""),
    NOT_SHIPPED_NO_STOCK("Товар не отгружен: нет товара на складе", ""),
    NOT_SHIPPED_NO_SPACE("Товар не отгружен: не влезло в машину", ""),
    PARTIALLY_DELIVERED("Частично доставлен", ""),
    NOT_SHIPPED_NOT_PICKED_FROM_DRIVER("Товар не отгружен: не забрал у другого водителя", "");


    private final String displayName;
    private final String code;

    OrderStatus(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    public static OrderStatus fromDisplayName(String text) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.displayName.equalsIgnoreCase(text.trim())) {
                return status;
            }
        }
        return null;
    }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static String getEmojiByStatus(OrderStatus status) {
        if (status == null) return "";
        switch (status) {
            case DELIVERED: return "📦";
            case PARTIALLY_DELIVERED: return "✂️"; // ✅ новый статус
            case NO_RESPONSE: return "📵";
            case CANCELED_BY_PHONE: return "📞";
            case CANCELED_AT_HANDOVER: return "🛑";
            case RESCHEDULED: return "⏳";
            case HANDED_TO_MANAGER: return "\uD83D\uDC68\u200D\uD83D\uDCBC"; // 👨‍💼
            case NOT_SHIPPED_NO_INVOICE: return "📄";
            case NOT_SHIPPED_NO_STOCK: return "📦";
            case NOT_SHIPPED_NO_SPACE: return "🚚";
            case NOT_SHIPPED_NOT_PICKED_FROM_DRIVER: return "🔄";
            default: return "❓";
        }
    }

}
