package modules.parsers.lemana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import modules.parsers.Product;
import okhttp3.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeroyMerlinParser {
    private static final String BASE_URL = "https://b2b.lemanapro.ru";
    private static final String API_URL = BASE_URL + "/execute/GetProductByVendorCodeQuery";

    private final OkHttpClient client;
    private String authToken;

    public LeroyMerlinParser() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    // =========================
    // НОВОЕ: по ОДНОЙ ссылке
    // =========================
    public Product parseProductByUrl(String productUrl) throws IOException {
        String vendorCode = extractVendorCodeFromUrl(productUrl);
        if (vendorCode == null) {
            throw new IllegalArgumentException("Не удалось вытащить артикул из URL: " + productUrl);
        }
        return parseProduct(vendorCode);
    }

    // =========================
    // НОВОЕ: по СПИСКУ артикулов
    // =========================
    public List<Product> parseProductsByVendorCodes(List<String> vendorCodes) {
        List<Product> result = new ArrayList<>();
        for (String code : vendorCodes) {
            try {
                Product p = parseProduct(code);
                result.add(p);
            } catch (Exception e) {
                System.err.println("Ошибка при парсе артикула " + code + ": " + e.getMessage());
            }
        }
        return result;
    }

    // =========================
    // НОВОЕ: по СПИСКУ ссылок
    // =========================
    public List<Product> parseProductsByUrls(List<String> urls) {
        List<Product> result = new ArrayList<>();
        for (String url : urls) {
            try {
                Product p = parseProductByUrl(url);
                result.add(p);
            } catch (Exception e) {
                System.err.println("Ошибка при парсе ссылки " + url + ": " + e.getMessage());
            }
        }
        return result;
    }

    private String extractVendorCodeFromUrl(String url) {
        Pattern p = Pattern.compile("(\\d+)(?:\\D*)$");
        Matcher m = p.matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public Product parseProduct(String productId) throws IOException {
        String jsonBody = String.format(
                "{\"commandOrQueryName\":\"GetProductByVendorCodeQuery\"," +
                        "\"input\":\"{\\\"ProductLeroyMerlinId\\\":\\\"%s\\\",\\\"InternalUserId\\\":null,\\\"StoresCodes\\\":null}\"}",
                productId
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .addHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Authorization", authToken)
                .addHeader("Referer", BASE_URL + "/product/" + productId)
                .addHeader("Origin", BASE_URL)
                .build();


        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                System.err.println("HTTP error for product " + productId + ": " +
                        response.code() + " - " + response.message());
                System.err.println("Response body: " + responseBody);

                if (response.code() == 422) {
                    // Товар не найден / не проходит валидацию — вернем "пустой" объект
                    Product empty = new Product(productId);
                    empty.setUrl(BASE_URL + "/product/" + productId);
                    empty.setPrice(BigDecimal.ZERO);
                    empty.setSpecialPrice(BigDecimal.ZERO);
                    empty.setUnit("шт.");
                    empty.setTotalStock(0);
                    empty.setProStock(0);
                    return empty;
                }

                throw new IOException("HTTP error: " + response.code());
            }

            return parseProductData(responseBody, productId);
        }
    }

    private Product parseProductData(String jsonResponse, String productId) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonResponse);

        if (!root.has("result")) {
            throw new IOException("No 'result' field in response");
        }

        String resultJson = root.get("result").asText();
        JsonNode productData = mapper.readTree(resultJson);

        Product product = new Product();
        product.setId(productId);
        product.setUrl(BASE_URL + "/product/" + productId);

        if (productData.has("CategoryGroupName")) {
            product.setCategoryGroupName(productData.get("CategoryGroupName").asText());
        }

        if (productData.has("CategoryName")) {
            product.setCategoryName(productData.get("CategoryName").asText());
        }

        if (productData.has("SubcategoryName")) {
            product.setSubcategoryName(productData.get("SubcategoryName").asText());
        }

        if (productData.has("Name")) {
            product.setName(productData.get("Name").asText());
        }

        if (productData.has("RetailPrice")) {
            try {
                product.setPrice(new BigDecimal(productData.get("RetailPrice").asText()));
            } catch (NumberFormatException e) {
                product.setPrice(BigDecimal.ZERO);
            }
        }

        if (productData.has("SpecialPrice")) {
            try {
                BigDecimal specialPrice = new BigDecimal(productData.get("SpecialPrice").asText());
                if (product.getPrice() != null &&
                        specialPrice.multiply(BigDecimal.valueOf(2)).compareTo(product.getPrice()) < 0) {
                    product.setSpecialPrice(product.getPrice());
                } else {
                    product.setSpecialPrice(specialPrice);
                }
            } catch (NumberFormatException e) {
                product.setSpecialPrice(product.getPrice());
            }
        }

        if (productData.has("Description")) {
            product.setDescription(productData.get("Description").asText());
        }

        if (productData.has("SellingUnitName")) {
            product.setUnit(productData.get("SellingUnitName").asText());
        }

        // Остатки
        StockInfo stockInfo = calculateStockInfo(productData);
        product.setTotalStock(stockInfo.total);
        product.setProStock(stockInfo.pro);

        // Характеристики
        product.setCharacteristics(extractCharacteristics(productData));

        // Бренд / страна
        if (productData.has("Brand") && !productData.get("Brand").isNull()) {
            product.setBrand(productData.get("Brand").asText());
        }
        if (productData.has("CountryOfOrigin") && !productData.get("CountryOfOrigin").isNull()) {
            product.setCountry(productData.get("CountryOfOrigin").asText());
        }

        // === ИСПРАВЛЕННАЯ ЧАСТЬ: обработка изображений ===
        List<String> allImages = new ArrayList<>();

        // Главное изображение
        if (productData.has("MainImage") && productData.get("MainImage").has("ImageURL")) {
            String originalUrl = productData.get("MainImage").get("ImageURL").asText();
            String webpUrl = convertToWebpUrl(originalUrl);
            product.setImageUrl(webpUrl);
            allImages.add(webpUrl);
        }

        // Дополнительные изображения
        if (productData.has("ImageModel") && productData.get("ImageModel").isArray()) {
            for (JsonNode imgNode : productData.get("ImageModel")) {
                if (imgNode.has("ImageURL")) {
                    String originalUrl = imgNode.get("ImageURL").asText();
                    String webpUrl = convertToWebpUrl(originalUrl);
                    // Добавляем только если это не дубликат главного изображения
                    if (!allImages.contains(webpUrl)) {
                        allImages.add(webpUrl);
                    }
                }
            }
        }

        product.setAllImages(allImages);


        // Вся мелочёвка из PropertiesValues
        ExtraFields extra = extractExtraFromProperties(productData);
        product.setBrand(extra.brand);                     // <brand>
        product.setCountry(extra.country);                 // <country>
        product.setWeight(extra.weight);                   // <weight>
        product.setCharacteristicsText(extra.characteristics); // <characteristics>

        return product;
    }

    private static class StockInfo {
        int total;
        int pro;
    }

    private StockInfo calculateStockInfo(JsonNode productData) {
        StockInfo info = new StockInfo();
        info.total = 0;
        info.pro = 0;

        JsonNode stocks = productData.get("LMProductInStocks");

        if (stocks != null && stocks.isArray()) {
            List<String> targetWarehouses = Arrays.asList(
                    "Лемана ПРО Алтуфьево", "Лемана ПРО Мытищи", "Лемана ПРО Каширское шоссе",
                    "Лемана ПРО Новая Рига", "Лемана ПРО Химки", "Лемана ПРО Варшавское шоссе",
                    "Лемана ПРО Киевское шоссе", "Лемана ПРО Красногорск", "Лемана ПРО Лефортовo",
                    "Лемана ПРО Люберцы", "Лемана ПРО Выхино"
            );

            for (JsonNode stock : stocks) {
                if (stock.has("Name") && stock.has("AmountInStock")) {
                    int amount = stock.get("AmountInStock").asInt();
                    info.total += amount;

                    String warehouseName = stock.get("Name").asText();
                    if (targetWarehouses.contains(warehouseName)) {
                        info.pro += amount;
                    }
                }
            }
        }

        return info;
    }

    private Map<String, String> extractCharacteristics(JsonNode productData) {
        Map<String, String> characteristics = new LinkedHashMap<>();

        if (productData.has("Brand") && !productData.get("Brand").isNull()) {
            characteristics.put("Бренд", productData.get("Brand").asText());
        }
        if (productData.has("VendorCode") && !productData.get("VendorCode").isNull()) {
            characteristics.put("Артикул", productData.get("VendorCode").asText());
        }
        if (productData.has("CountryOfOrigin") && !productData.get("CountryOfOrigin").isNull()) {
            characteristics.put("Страна", productData.get("CountryOfOrigin").asText());
        }

        JsonNode attributes = productData.get("Attributes");
        if (attributes != null && attributes.isArray()) {
            for (JsonNode attr : attributes) {
                if (attr.has("Name") && attr.has("Value")) {
                    String name = attr.get("Name").asText();
                    String value = attr.get("Value").asText();
                    if (!name.isEmpty() && !value.isEmpty()) {
                        characteristics.put(name, value);
                    }
                }
            }
        }

        return characteristics;
    }

    private String convertToWebpUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            return originalUrl;
        }

        try {
            // Убираем параметры из URL для чистого пути
            String baseUrl = originalUrl.split("\\?")[0];

            // Заменяем расширение на .webp
            if (baseUrl.matches(".*\\.(jpe?g|png|JPE?G|PNG)$")) {
                return baseUrl.replaceAll("\\.(jpe?g|png|JPE?G|PNG)$", ".webp");
            }

            // Если уже webp или другое расширение - возвращаем как есть
            return originalUrl;
        } catch (Exception e) {
            System.err.println("Ошибка при конвертации URL: " + originalUrl + ", ошибка: " + e.getMessage());
            return originalUrl; // В случае ошибки возвращаем оригинальный URL
        }
    }

    private String extractImageUrl(JsonNode productData) {
        if (productData.has("ImageUrl") && !productData.get("ImageUrl").isNull()) {
            return productData.get("ImageUrl").asText();
        }
        return null;
    }

    public static class ExtraFields {
        public String brand;             // Марка
        public String country;           // Страна производства
        public String weight;            // Вес нетто (кг)
        public String characteristics;   // Все характеристики одной строкой с переносами
    }

    private ExtraFields extractExtraFromProperties(JsonNode productData) {
        ExtraFields extra = new ExtraFields();
        StringBuilder chars = new StringBuilder();

        JsonNode props = productData.get("PropertiesValues");
        if (props != null && props.isArray()) {
            for (JsonNode prop : props) {
                JsonNode nameNode = prop.get("Name");
                JsonNode valueNode = prop.get("Value");
                if (nameNode == null || valueNode == null) continue;

                String name = nameNode.asText().trim();
                String value = valueNode.asText().trim();
                if (name.isEmpty() || value.isEmpty()) continue;

                // Копим текст для <characteristics>
                if (chars.length() > 0) {
                    chars.append("\n");
                }
                chars.append(name).append(": ").append(value);

                // Точечно вытаскиваем нужные поля
                switch (name) {
                    case "Марка":
                        extra.brand = value;
                        break;
                    case "Страна производства":
                        extra.country = value;
                        break;
                    case "Вес нетто (кг)":
                        extra.weight = value;
                        break;
                    default:
                        // остальные просто попадают в characteristics
                        break;
                }
            }
        }

        extra.characteristics = chars.toString();
        return extra;
    }

    private String toWebp(String url) {
        if (url == null) return null;

        // Меняем только конечное расширение файла
        return url.replaceAll("\\.(jpe?g|png)(\\?.*)?$", ".webp$2");
    }


}
