package modules.parsers.lemana;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import modules.parsers.Product;
import modules.parsers.XmlExporter;
import okhttp3.OkHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class LemanaAPI {
    public static String authToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy93cy8yMDA1LzA1L2lkZW50aXR5L2NsYWltcy9uYW1lIjoibXVoYWNoZXZhQHJlbW9udDMwMDAucnUiLCJodHRwOi8vc2NoZW1hcy5taWNyb3NvZnQuY29tL3dzLzIwMDgvMDYvaWRlbnRpdHkvY2xhaW1zL3JvbGUiOiJVc2VyIiwiaHR0cDovL3NjaGVtYXMueG1sc29hcC5vcmcvd3MvMjAwNS8wNS9pZGVudGl0eS9jbGFpbXMvbmFtZWlkZW50aWZpZXIiOiJiZjkwMWU2Mi1mZGZiLTRhMjItODFjYy1iODhjZjIzMGE2ZjciLCJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy93cy8yMDA1LzA1L2lkZW50aXR5L2NsYWltcy9naXZlbm5hbWUiOiIyNTlhZWYyYi1kZjAyLTRmNjUtYjlkOC0xYjgyZjQwZjZjNzMiLCJodHRwOi8vc2NoZW1hcy5taWNyb3NvZnQuY29tL3dzLzIwMDgvMDYvaWRlbnRpdHkvY2xhaW1zL2dyb3Vwc2lkIjoiNzlhODI3NTYtNTU5NC00NDlhLWJjZDAtNjJhNzBkMjIwZWRkIiwiaXNzIjoiTE1CUF9Qb3J0YWwiLCJhdWQiOiJMTUJQX1BvcnRhbCJ9.2lT40gQ0IMfJQUe_IMIX5JKnTu8RFyWaT4xLWL8liT0";

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private static final List<String> lastDiscontinued = Collections.synchronizedList(new ArrayList<>());

    public static List<String> getLastDiscontinued() {
        synchronized (lastDiscontinued) {
            return new ArrayList<>(lastDiscontinued);
        }
    }

    public static File startLemanaParseCategory(String catalogUrl) {
        LeroyMerlinParser parser = new LeroyMerlinParser();
        parser.setAuthToken(authToken);
        OkHttpClient httpClient = new OkHttpClient();
        LeroyCatalogProductsFetcher catalogFetcher = new LeroyCatalogProductsFetcher(httpClient);
        catalogFetcher.setAuthToken(authToken);
        try {
            List<String> vendorCodes = catalogFetcher.getVendorCodesByCatalogUrl(catalogUrl);
            File outputDir = new File("export");
            return parseMixedInputsAndExport(vendorCodes, parser, outputDir);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File startLemanaParse(List<String> vendorCodes) {
        LeroyMerlinParser parser = new LeroyMerlinParser();
        parser.setAuthToken(authToken);
        try {
            File outputDir = new File("export");
            return parseMixedInputsAndExport(vendorCodes, parser, outputDir);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File startLemanaParseVendors() {
        LeroyMerlinParser parser = new LeroyMerlinParser();
        parser.setAuthToken(authToken);
        try {
            List<String> productIds = loadVendors("vendors.xml");
            List<Product> products = parseProductsParallel(productIds, parser);
            return writeDataRest("data_rest.xml", products);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static File parseMixedInputsAndExport(List<String> inputs,
                                                  LeroyMerlinParser parser,
                                                  File outputDir) throws Exception {
        List<Product> products = new ArrayList<>();

        for (String raw : inputs) {
            if (raw == null || raw.isBlank()) continue;
            String s = raw.trim();

            try {
                Product product;

                if (looksLikeUrl(s)) {
                    // Парс по ссылке
                    product = parseByUrl(s, parser);
                } else {
                    // Парс по артикулу
                    product = parseByVendorCode(s, parser);
                }

                if (product != null) {
                    products.add(product);
                }
            } catch (Exception e) {
                System.err.println("Ошибка при обработке '" + s + "': " + e.getMessage());
                // Можно создать заглушку, если хочешь, чтобы всё равно попал в файл:
                // products.add(createEmptyProduct(s));
            }
        }

        if (products.isEmpty()) {
            System.out.println("Нет успешно обработанных товаров, файл не создаётся.");
            return outputDir;
        }

        // groupLink можно оставить пустым или подставить свою ссылку на категорию
        String groupLink = "";
        return XmlExporter.writeGroupToFile(products, groupLink, outputDir);
    }

    /**
     * Парс товара по артикулу (код Леруа).
     */
    private static Product parseByVendorCode(String vendorCode,
                                             LeroyMerlinParser parser) throws Exception {
        System.out.println("Парсим по артикулу: " + vendorCode);
        Product product = parser.parseProduct(vendorCode);
        // Если в самом продукте url не задан — зададим B2B-ссылку
        if (product.getUrl() == null || product.getUrl().isBlank()) {
            product.setUrl("https://b2b.lemanapro.ru/product/" + vendorCode);
        }
        return product;
    }

    /**
     * Парс товара по ссылке.
     * Сейчас просто вытаскиваем из ссылки артикул и используем B2B API.
     */
    private static Product parseByUrl(String url,
                                      LeroyMerlinParser parser) throws Exception {
        System.out.println("Парсим по ссылке: " + url);

        String vendorCode = extractVendorCodeFromUrl(url);
        if (vendorCode == null) {
            throw new IllegalArgumentException("Не удалось вытащить артикул из ссылки: " + url);
        }

        Product product = parser.parseProduct(vendorCode);
        // Для красоты оставим оригинальную ссылку
        product.setUrl(url);
        return product;
    }

    /**
     * Очень простой чек, что строка похожа на URL.
     */
    private static boolean looksLikeUrl(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * Вытаскиваем артикул из ссылки.
     * Берём ПОСЛЕДНЮЮ "длинную" группу цифр (5+), т.к. в урле могут быть id-шники.
     *
     * Пример:
     *  - https://b2b.lemanapro.ru/product/18580189  -> 18580189
     *  - https://leroymerlin.ru/product/...-18580189/ -> 18580189
     */
    private static String extractVendorCodeFromUrl(String url) {
        Pattern p = Pattern.compile("(\\d{5,})");
        Matcher m = p.matcher(url);
        String lastMatch = null;
        while (m.find()) {
            lastMatch = m.group(1);
        }
        return lastMatch;
    }

    // ================== СТАРЫЕ МЕТОДЫ НИЖЕ БЕЗ ИЗМЕНЕНИЙ ==================

    private static List<String> loadVendors(String vendorsPath) throws Exception {
        List<String> ids = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        try (InputStream is = new FileInputStream(Paths.get(vendorsPath).toFile())) {
            Document doc = builder.parse(is);
            NodeList vendorNodes = doc.getElementsByTagName("vendor");
            for (int i = 0; i < vendorNodes.getLength(); i++) {
                String code = vendorNodes.item(i).getTextContent().trim();
                if (!code.isEmpty()) {
                    ids.add(code);
                }
            }
        }

        System.out.println("Loaded " + ids.size() + " vendor codes from " + vendorsPath);
        return ids;
    }

    private static List<Product> parseProductsParallel(List<String> productIds,
                                                       LeroyMerlinParser parser) {

        int total = productIds.size();
        AtomicInteger counter = new AtomicInteger(0);

        List<Product> products = productIds
                .parallelStream()
                .map(productId -> {
                    Product product;

                    try {
                        product = parser.parseProduct(productId);
                    } catch (Exception e) {
                        System.err.println("Ошибка при парсинге товара "
                                + productId + ": " + e.getMessage());

                        product = createEmptyProduct(productId);
                    }

                    try {
                        Thread.sleep(1000 + (long) (Math.random() * 500));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    return product;
                })
                .collect(Collectors.toList());

        System.out.println("Все товары обработаны: " + products.size());
        return products;
    }

    private static Product createEmptyProduct(String productId) {
        Product product = new Product(productId);
        product.setUrl("https://b2b.lemanapro.ru/product/" + productId);
        product.setPrice(BigDecimal.ZERO);
        product.setSpecialPrice(BigDecimal.ZERO);
        product.setUnit("шт.");
        product.setTotalStock(0);
        product.setProStock(0);
        return product;
    }

    private static File writeDataRest(String outputPath,
                                      List<Product> products) throws Exception {

        // → тут готовим lastDiscontinued
        synchronized (lastDiscontinued) {
            lastDiscontinued.clear();

            for (Product p : products) {
                BigDecimal price        = p.getPrice();
                BigDecimal specialPrice = p.getSpecialPrice();
                Integer total           = p.getTotalStock();
                Integer pro             = p.getProStock();

                boolean isZeroPrice =
                        (price == null || price.compareTo(BigDecimal.ZERO) == 0) &&
                                (specialPrice == null || specialPrice.compareTo(BigDecimal.ZERO) == 0);

                boolean isZeroStock =
                        (total == null || total == 0) &&
                                (pro == null   || pro == 0);

                // Наш критерий "возможно снят с продажи"
                if (isZeroPrice && isZeroStock) {
                    lastDiscontinued.add(p.getId());
                }
            }
        }

        // дальше — твоя запись XML, как выше (без рекурсивного вызова!)
        XMLOutputFactory xof = XMLOutputFactory.newInstance();

        try (FileOutputStream fos = new FileOutputStream(outputPath);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

            XMLStreamWriter xml = xof.createXMLStreamWriter(writer);

            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");
            xml.writeStartElement("goods");

            for (Product product : products) {
                String productId = product.getId();
                String nowTs = LocalDateTime.now().format(TS_FORMAT);

                xml.writeCharacters("\n\t");
                xml.writeStartElement("good");

                writeSimpleElement(xml, "code", productId);
                writeSimpleElement(xml, "link", product.getUrl() != null
                        ? product.getUrl()
                        : "https://b2b.lemanapro.ru/product/" + productId);
                writeSimpleElement(xml, "price", bdToString(product.getPrice()));
                writeSimpleElement(xml, "special_price", bdToString(product.getSpecialPrice()));

                String unit = product.getUnit() != null ? product.getUnit() : "шт.";
                writeSimpleElement(xml, "unit", unit);
                writeSimpleElement(xml, "unit_id", mapUnitId(unit));

                writeSimpleElement(xml, "rest", String.valueOf(
                        product.getTotalStock() != null ? product.getTotalStock() : 0
                ));
                writeSimpleElement(xml, "rest1", String.valueOf(
                        product.getProStock() != null ? product.getProStock() : 0
                ));
                writeSimpleElement(xml, "updated_at", nowTs);

                xml.writeEndElement(); // </good>
            }

            xml.writeCharacters("\n");
            xml.writeEndElement(); // </goods>
            xml.writeEndDocument();
            xml.flush();
        }

        return new File(outputPath);
    }


    private static void writeSimpleElement(XMLStreamWriter xml, String name, String value) throws Exception {
        xml.writeCharacters("\n\t\t");
        xml.writeStartElement(name);
        if (value != null) {
            xml.writeCharacters(value);
        }
        xml.writeEndElement();
    }

    private static String bdToString(BigDecimal bd) {
        if (bd == null) return "0";
        return bd.stripTrailingZeros().toPlainString();
    }

    private static String mapUnitId(String unit) {
        if (unit == null) return "796";
        unit = unit.trim().toLowerCase();

        if (unit.startsWith("шт")) {
            return "796";
        } else if (unit.startsWith("упак")) {
            return "778";
        }
        return "796";
    }
}