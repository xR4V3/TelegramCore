package utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.beans.Transient;
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

    @JsonIgnore
    public String getCleanOrderNumber() {
        if (orderNumber == null) return "";

        // Извлечь номер до "от"
        String[] parts = orderNumber.split("от");
        if (parts.length == 0) return "";

        // Удалить всё, кроме цифр и убрать ведущие нули
        String numPart = parts[0].replaceAll("\\D", "");
        return String.valueOf(Long.parseLong(numPart)); // Убирает ведущие нули
    }

    // Класс для описания структуры "ЗаказыПоставщику"
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
    }

}

