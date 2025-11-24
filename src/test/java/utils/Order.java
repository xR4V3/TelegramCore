package utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {
    @JsonProperty("Товарный состав")
    public String productDescription;

    @JsonProperty("Способ оплаты")
    public String paymentMethod;

    @JsonProperty("Дополнительный номер")
    public String additionalNumber;

    @JsonProperty("Статус оплаты")
    public String paymentStatus;

    @JsonProperty("Статус заказа")
    public String orderStatus;

    @JsonProperty("Номер заказа с сайта")
    public String webOrderNumber;

    @JsonProperty("Адрес доставки")
    public String deliveryAddress;

    @JsonProperty("Комментарий")
    public String comment;

    @JsonProperty("Принимает")
    public String recipientPhone;

    @JsonProperty("Длина")
    public String length;

    @JsonProperty("Вес")
    public String weight;

    @JsonProperty("Менеджер клиента")
    public String clientManager;

    @JsonProperty("Контактное лицо")
    public String contactPerson;

    @JsonProperty("Способ доставки")
    public String deliveryMethod;

    @JsonProperty("Запасной телефон")
    public String backupPhone;

    @JsonProperty("ВодительИД")
    public String driverId;

    @JsonProperty("Разгрузка и подъем")
    public String unloading;

    @JsonProperty("Водитель")
    public String driver;

    @JsonProperty("Объем")
    public String volume;

    @JsonProperty("Дата доставки")
    public String deliveryDate;

    @JsonProperty("Клиент")
    public String client;

    @JsonProperty("Заказ № ")
    public String orderNumber;

    @JsonProperty("Сумма заказа")
    public String orderTotal;

    @JsonProperty("Организация")
    public String organization;

    @JsonProperty("ЗаказыПоставщику")
    public List<SupplierOrder> supplierOrders;

    // ✳️ Новое: список перемещений товаров
    @JsonProperty("ПеремещениеТоваров")
    public List<Movement> movements;

    @JsonIgnore
    public String getCleanOrderNumber() {
        if (orderNumber == null) return "";
        String[] parts = orderNumber.split("от");
        if (parts.length == 0) return "";
        String numPart = parts[0].replaceAll("\\D", "");
        return numPart.isEmpty() ? "" : String.valueOf(Long.parseLong(numPart));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupplierOrder {
        @JsonProperty("Поставщик")
        public String supplier;

        @JsonProperty("СкладПоставщика")
        public String supplierWarehouse;

        @JsonProperty("СчетПоставщика")
        public String supplierInvoice;

        @JsonProperty("ВодительПогрузки")
        public String loadingDriver;

        @JsonProperty("ДатаПогрузки")
        public String loadingDate;

        @JsonProperty("ТоварныйСостав")
        public String productComposition;

        // ✳️ Новое: список возвратов по этому заказу поставщику
        @JsonProperty("Возвраты")
        public List<ReturnItem> returns;

        @JsonProperty("Организация")
        public String organization;
    }

    // ✳️ Новое: модель «Возврат»
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReturnItem {
        @JsonProperty("НомерВозврата")
        public String returnNumber;

        @JsonProperty("Комментарий")
        public String comment;

        @JsonProperty("ВодительВозврата")
        public String returnDriver;

        @JsonProperty("Статус")
        public String status;

        @JsonProperty("ТоварныйСостав")
        public String productComposition;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Movement {
        @JsonProperty("Отправитель")
        public String sender;

        @JsonProperty("Получатель")
        public String recipient;

        @JsonProperty("НомерПеремещения")
        public String movementNumber;

        @JsonProperty("ВодительПогрузки")
        public String loadingDriver;

        @JsonProperty("ТоварныйСостав")
        public String productComposition;

        @JsonProperty("ДатаПеремещения")
        public String movementDate;
    }

}
