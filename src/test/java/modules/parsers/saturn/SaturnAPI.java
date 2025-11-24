package modules.parsers.saturn;

import modules.parsers.Product;
import modules.parsers.XmlExporter;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;

public class SaturnAPI {

    private static final String BASE_URL = "https://msk.saturn.net";

    /**
     * 1) ПАРС ОДНОГО КАТАЛОГА
     * Пример: https://msk.saturn.net/catalog/trotuarnaya-plitka/
     */
    public static File startSaturnParseCategory(String catalogUrl) {
        SaturnCatalogParser catalogParser = new SaturnCatalogParser();
        SaturnProductParser productParser = new SaturnProductParser();

        try {
            List<String> productLinks = catalogParser.collectProductLinks(catalogUrl);
            System.out.println("[Saturn] Всего ссылок товаров в каталоге: " + productLinks.size());

            File outputDir = new File("export");
            String groupLink = catalogUrl;

            return parseProductsAndExport(productLinks, productParser, outputDir, groupLink);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 2) ТОЧЕЧНЫЙ ПАРС
     *
     * На вход список строк:
     *  - ссылка на товар:   https://msk.saturn.net/product/...
     *  - ссылка на каталог: https://msk.saturn.net/catalog/...
     *  - или просто slug товара: my-tovar-123
     */
    public static File startSaturnParse(List<String> inputs) {
        SaturnCatalogParser catalogParser = new SaturnCatalogParser();
        SaturnProductParser productParser = new SaturnProductParser();

        try {
            LinkedHashSet<String> allProductLinks = new LinkedHashSet<>();

            for (String raw : inputs) {
                if (raw == null) continue;
                String s = raw.trim();
                if (s.isEmpty()) continue;

                try {
                    if (looksLikeUrl(s)) {
                        if (s.contains("/catalog/")) {
                            // URL каталога → разворачиваем в товары
                            List<String> fromCatalog = catalogParser.collectProductLinks(s);
                            allProductLinks.addAll(fromCatalog);
                        } else if (s.contains("/product/")) {
                            // URL товара
                            allProductLinks.add(s);
                        } else {
                            // какой-то другой URL – пробуем как товар
                            allProductLinks.add(s);
                        }
                    } else {
                        // не URL → считаем, что это код (slug) товара
                        String url = BASE_URL + "/product/" + s + "/";
                        allProductLinks.add(url);
                    }
                } catch (Exception ex) {
                    System.err.println("[Saturn] Ошибка при обработке '" + s + "': " + ex.getMessage());
                }
            }

            if (allProductLinks.isEmpty()) {
                System.out.println("[Saturn] Ни одной валидной ссылки/кода, файл не создаём.");
                return null;
            }

            File outputDir = new File("export");
            String groupLink = ""; // как в LemanaAPI для точечного парса

            return parseProductsAndExport(new ArrayList<>(allProductLinks),
                    productParser, outputDir, groupLink);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 3) ПАРС ПО vendors.xml
     *
     * vendors.xml такого же формата, как у Леманы:
     * <vendors>
     *   <vendor>https://msk.saturn.net/product/.../</vendor>
     *   <vendor>my-product-slug</vendor>
     *   ...
     * </vendors>
     */
    public static File startSaturnParseVendors() {
        try {
            List<String> vendors = loadVendors("vendors.xml");
            if (vendors.isEmpty()) {
                System.out.println("[Saturn] vendors.xml пустой, парсить нечего.");
                return null;
            }
            return startSaturnParse(vendors);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ================= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =================

    private static File parseProductsAndExport(List<String> productLinks,
                                               SaturnProductParser productParser,
                                               File outputDir,
                                               String groupLink) throws Exception {

        List<Product> products = new ArrayList<>();

        for (String link : productLinks) {
            try {
                Product p = productParser.parseProduct(link);
                if (p != null) {
                    products.add(p);
                } else {
                    System.out.println("[Saturn] Товар " + link + " пропущен (остаток 0 или ошибка парсинга).");
                }
            } catch (Exception e) {
                System.err.println("[Saturn] Ошибка при парсинге товара " + link + ": " + e.getMessage());
            }
        }

        if (products.isEmpty()) {
            System.out.println("[Saturn] Не удалось спарсить ни одного товара, файл не создаём.");
            return null;
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        return XmlExporter.writeGroupToFile(products, groupLink, outputDir);
    }

    private static boolean looksLikeUrl(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * Читаем vendors.xml, полностью аналогично LemanaAPI.loadVendors
     */
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

        System.out.println("[Saturn] Loaded " + ids.size() + " vendors from " + vendorsPath);
        return ids;
    }
}
