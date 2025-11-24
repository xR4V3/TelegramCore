package modules;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import utils.Order;
import utils.Settings;

public class OrderStatusUpdater {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static synchronized void updateWebOrderStatus(String orderNumber, String status) {
        if (status.isEmpty()) {
            System.out.println("Статус заказа : " + orderNumber + " не изменен! т.к. status.IsEmpty");
            return;
        }

        executor.submit(() -> {
            int attempt = 0;
            boolean success = false;

            while (attempt < MAX_RETRIES && !success) {
                attempt++;
                try {
                    System.out.println("Отправка статуса (попытка " + attempt + " из " + MAX_RETRIES + ")");
                    sendOrderStatus(orderNumber, status);
                    success = true;
                } catch (Exception e) {
                    System.err.println("Ошибка при попытке " + attempt + ": " + e.getMessage());
                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ignored) {}
                    }
                }
            }

            if (!success) {
                System.err.println("Не удалось обновить статус заказа " + orderNumber + " после " + MAX_RETRIES + " попыток.");
            }
        });
    }

    private static void sendOrderStatus(String orderNumber, String status) throws IOException {
        Map<String, String> data = Map.of(
                "api_key", Settings.get().getApiKey(),
                "order_number", orderNumber,
                "status", status
        );

        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(data);

        URL url = new URL(Settings.get().getApiUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        int responseCode = conn.getResponseCode();
        Scanner scanner = new Scanner(
                responseCode >= 200 && responseCode < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream(),
                "UTF-8"
        );

        String response = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        scanner.close();

        System.out.println("Response Code: " + responseCode);
        System.out.println("Response Body: " + response);

        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("Сервер вернул ошибку: " + responseCode);
        }
    }


    public static synchronized boolean updateOrderStatus(String targetOrderNumber, String newStatus) {
        ObjectMapper mapper = new ObjectMapper();
        Path ordersDir = Paths.get("orders");

        if (!Files.exists(ordersDir) || !Files.isDirectory(ordersDir)) {
            System.err.println("Папка заказов не найдена: " + ordersDir.toAbsolutePath());
            return false;
        }

        DateTimeFormatter auditFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String auditLineTemplate = "[%s] Заказ %s: статус изменён с \"%s\" на \"%s\"%n";

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ordersDir, "*.json")) {
            for (Path file : stream) {
                try {
                    Order order = mapper.readValue(file.toFile(), Order.class);
                    if (order.orderNumber != null && order.orderNumber.trim().equals(targetOrderNumber.trim())) {
                        String oldStatus = order.orderStatus;

                        if (oldStatus != null && oldStatus.equals(newStatus)) {
                            // Ничего не менять
                            return true;
                        }

                        order.orderStatus = newStatus;

                        // Резервное копирование текущего файла (на случай ошибки)
                        Path backup = file.resolveSibling(file.getFileName().toString() + ".bak");
                        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);

                        // Перезапись файла с обновлённым статусом
                        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), order);

                        // Запись в audit log
                        String timestamp = java.time.LocalDateTime.now().format(auditFormatter);
                        String auditLine = String.format(auditLineTemplate, timestamp, targetOrderNumber, oldStatus, newStatus);
                        Files.writeString(Paths.get("", "order_status_audit.log"), auditLine,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                        System.out.println("Статус заказа обновлён: " + targetOrderNumber + " -> " + newStatus);
                        return true;
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка при обработке файла " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка доступа к папке заказов: " + e.getMessage());
            return false;
        }

        System.err.println("Заказ не найден: " + targetOrderNumber);
        return false;
    }

    public static synchronized boolean updateReturnStatus(String targetReturnNumber, String newStatus) {
        ObjectMapper mapper = new ObjectMapper();
        Path ordersDir = Paths.get("orders");

        if (!Files.exists(ordersDir) || !Files.isDirectory(ordersDir)) {
            System.err.println("Папка заказов не найдена: " + ordersDir.toAbsolutePath());
            return false;
        }

        if (targetReturnNumber == null || targetReturnNumber.trim().isEmpty()) {
            System.err.println("Пустой номер возврата.");
            return false;
        }

        String target = targetReturnNumber.trim();

        DateTimeFormatter auditFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String auditLineTemplate = "[%s] Возврат %s (заказ %s): статус изменён с \"%s\" на \"%s\"%n";

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ordersDir, "*.json")) {
            for (Path file : stream) {
                try {
                    Order order = mapper.readValue(file.toFile(), Order.class);
                    if (order == null || order.supplierOrders == null || order.supplierOrders.isEmpty()) {
                        continue;
                    }

                    boolean updatedInThisFile = false;
                    String orderNumForLog = (order.orderNumber == null ? "—" : order.orderNumber.trim());

                    // Ищем по всем поставкам и их возвратам
                    for (Order.SupplierOrder so : order.supplierOrders) {
                        if (so == null || so.returns == null || so.returns.isEmpty()) continue;

                        for (Order.ReturnItem ri : so.returns) {
                            if (ri == null) continue;

                            String rNum = (ri.returnNumber == null ? "" : ri.returnNumber.trim());
                            if (!rNum.equals(target)) continue;

                            String oldStatus = ri.status;

                            // Если уже такой же статус — считаем успехом и выходим
                            if (oldStatus != null && oldStatus.equals(newStatus)) {
                                return true;
                            }

                            // Обновляем
                            ri.status = newStatus;
                            updatedInThisFile = true;

                            // Делаем бэкап файла
                            Path backup = file.resolveSibling(file.getFileName().toString() + ".bak");
                            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);

                            // Перезаписываем файл
                            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), order);

                            // Пишем в аудит-лог
                            String timestamp = java.time.LocalDateTime.now().format(auditFormatter);
                            String auditLine = String.format(
                                    auditLineTemplate,
                                    timestamp,
                                    target,
                                    orderNumForLog,
                                    oldStatus,
                                    newStatus
                            );
                            Files.writeString(
                                    Paths.get("", "return_status_audit.log"),
                                    auditLine,
                                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
                            );

                            System.out.println("Статус возврата обновлён: " + target + " -> " + newStatus
                                    + " (заказ " + orderNumForLog + ")");
                            return true; // нашли и обновили — выходим
                        }
                    }

                    // если в файле не нашли — идём дальше
                } catch (Exception e) {
                    System.err.println("Ошибка при обработке файла " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка доступа к папке заказов: " + e.getMessage());
            return false;
        }

        System.err.println("Возврат не найден: " + targetReturnNumber);
        return false;
    }


}