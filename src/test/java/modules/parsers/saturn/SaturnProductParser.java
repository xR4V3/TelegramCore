package modules.parsers.saturn;

import modules.parsers.Product;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SaturnProductParser {

    private static final String BASE_URL = "https://msk.saturn.net";

    // XPATH'ы для цены
    private static final String XPATH_PRICE_MAIN =
            "/html/body/main/div[2]/div/div[2]/div[1]/div[2]/div[1]/div[2]/div[2]/div[2]/span[1]";
    private static final String XPATH_PRICE_ALT =
            "/html/body/main/div[2]/div/div[2]/div[1]/div[1]/div[1]/div[2]/div[2]/div[2]/span[1]";

    // XPATH корня характеристик (#details)
    private static final String XPATH_DETAILS_ROOT =
            "/html/body/main/div[2]/div/div[1]/div[2]/div[2]/div[2]";

    public Product parseProduct(String productUrl) throws IOException {
        System.out.println("[Saturn] Парсим товар: " + productUrl);

        Document doc = Jsoup.connect(productUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.0.0 Safari/537.36")
                .timeout(30000)
                .get();

        Product p = new Product();
        p.setUrl(productUrl);

        // ---------- second_name / third_name ----------
        String secondName = textByXpath(doc, "/html/body/main/div[1]/ul/li[2]/a/span");
        String thirdName  = textByXpath(doc, "/html/body/main/div[1]/ul/li[3]/a/span");

        p.setCategoryName(secondName);
        p.setSubcategoryName(thirdName);

        // ---------- link (hidden value) ----------
        String hiddenLink = attrByXpath(doc,
                "/html/body/div[14]/div/div/div/form/input[3]", "value");
        if (hiddenLink != null && !hiddenLink.isBlank()) {
            if (!hiddenLink.startsWith("http")) {
                hiddenLink = BASE_URL + hiddenLink;
            }
            p.setUrl(hiddenLink);
        }

        // ---------- code ----------
        String codeText = textByXpath(doc,
                "/html/body/main/div[2]/div/div[1]/div[1]/div/div[2]/div[1]");
        String code = null;
        if (codeText != null) {
            code = codeText.replaceAll("[^0-9]", "");
        }
        if (code == null || code.isBlank()) {
            code = extractCodeFromUrl(productUrl);
        }
        p.setId(code);

        // ---------- name ----------
        String name = textByXpath(doc,
                "/html/body/main/div[2]/div/div[1]/div[1]/h1");
        p.setName(name);

        // ---------- price с fallback ----------
        BigDecimal price = null;

        String priceTextMain = textByXpath(doc, XPATH_PRICE_MAIN);
        price = parsePrice(priceTextMain);

        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            String priceTextAlt = textByXpath(doc, XPATH_PRICE_ALT);
            BigDecimal priceAlt = parsePrice(priceTextAlt);
            if (priceAlt != null && priceAlt.compareTo(BigDecimal.ZERO) > 0) {
                price = priceAlt;
            }
        }

        if (price == null) {
            price = BigDecimal.ZERO;
        }

        p.setPrice(price);
        p.setSpecialPrice(price); // спеццена = обычная

        // ---------- rest + unit ----------
        String restUnitText = textByXpath(doc,
                "/html/body/main/div[2]/div/div[2]/div[2]/div[3]/ul/li[2]/div[2]");
        int rest = 0;
        String unit = "шт.";

        if (restUnitText != null && !restUnitText.isBlank()) {
            String digits = restUnitText.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    rest = Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {}
            }
            String unitPart = restUnitText.replaceAll("[0-9\\s.,]", "").trim();
            if (!unitPart.isEmpty()) {
                unit = unitPart;
            }
        }

        if (rest <= 0) {
            System.out.println("[Saturn] Остаток 0, пропускаем товар: " + productUrl);
            return null;
        }

        p.setTotalStock(rest);
        p.setProStock(0);
        p.setUnit(unit+".");

        // ---------- description ----------
        String description = textByXpath(doc,
                "/html/body/main/div[2]/div/div[1]/div[2]/div[2]/div[1]/div/div/div[2]/div[2]/div[1]");
        p.setDescription(description);

        // ---------- характеристики + бренд/страна/вес ----------
        fillCharacteristicsAndBasic(p, doc);

        // ---------- photos ----------
        String mainPhoto = attrByXpath(doc,
                "/html/body/main/div[2]/div/div[1]/div[2]/div[2]/div[1]/div/div/div[1]/div[1]/div/div[1]/div[1]/span/img",
                "src");

        List<String> allPhotos = new ArrayList<>();
        if (mainPhoto != null && !mainPhoto.isBlank()) {
            allPhotos.add(absUrl(mainPhoto));
        }

        List<String> extraPhotos = extractGalleryPhotos(doc);
        for (String ph : extraPhotos) {
            if (!allPhotos.contains(ph)) {
                allPhotos.add(ph);
            }
        }

        if (!allPhotos.isEmpty()) {
            p.setImageUrl(allPhotos.get(0));
            p.setAllImages(allPhotos);
        }

        return p;
    }

    // ================== ХАРАКТЕРИСТИКИ ==================

    private void fillCharacteristicsAndBasic(Product p, Document doc) {
        // Корень блока характеристик
        Element root = doc.selectXpath(XPATH_DETAILS_ROOT).first();
        if (root == null) {
            // fallback — по id="details"
            root = doc.getElementById("details");
        }
        if (root == null) {
            return;
        }

        StringBuilder chars = new StringBuilder();

        // На примере у тебя li с классом catalog__goods__blockwithdots
        for (Element li : root.select("li.catalog__goods__blockwithdots")) {
            Element left = li.selectFirst(
                    "div.catalog__goods__blockwithdots__title--left span");
            Element right = li.select("div.catalog__goods__blockwithdots__title")
                    .last();

            if (left == null || right == null) continue;

            String label = left.text().trim();
            String value = right.text().trim();
            if (label.isEmpty() || value.isEmpty()) continue;

            // Добавляем в общий текст характеристик
            if (chars.length() > 0) {
                chars.append("\n");
            }
            chars.append(label).append(": ").append(value);

            // Одновременно вытаскиваем бренд/страну/вес
            String lower = label.toLowerCase();

            if (lower.contains("бренд")) {
                p.setBrand(value);
            } else if (lower.contains("страна")) {
                // "Страна", "Страна происхождения" и т.п.
                p.setCountry(value);
            } else if (lower.contains("вес брутто")) {
                p.setWeight(value);
            }
        }

        String characteristicsText = chars.toString();
        if (!characteristicsText.isEmpty()) {
            // ВАЖНО: здесь используй тот сеттер, который у тебя реально есть.
            // В Лемане ты делал product.setCharacteristicsText(...)
            // Если метод называется иначе — поправь.
            p.setCharacteristicsText(characteristicsText);
        }
    }

    // ================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==================

    private String textByXpath(Document doc, String xpath) {
        Element el = doc.selectXpath(xpath).first();
        return el != null ? el.text().trim() : null;
    }

    private String attrByXpath(Document doc, String xpath, String attr) {
        Element el = doc.selectXpath(xpath).first();
        return el != null ? el.attr(attr) : null;
    }

    private String extractCodeFromUrl(String url) {
        String[] parts = url.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isBlank()) return parts[i];
        }
        return url;
    }

    private BigDecimal parsePrice(String text) {
        if (text == null) return null;
        String numeric = text.replaceAll("[^0-9,\\.]", "").replace(",", ".");
        if (numeric.isBlank()) return null;
        try {
            return new BigDecimal(numeric);
        } catch (Exception e) {
            return null;
        }
    }

    private String absUrl(String src) {
        if (src == null) return null;
        if (src.startsWith("http://") || src.startsWith("https://")) return src;
        if (!src.startsWith("/")) src = "/" + src;
        return BASE_URL + src;
    }

    private List<String> extractGalleryPhotos(Document doc) {
        Set<String> photos = new LinkedHashSet<>();

        String galleryRootXpath =
                "/html/body/main/div[2]/div/div[1]/div[2]/div[2]/div[1]/div/div/div[1]/div[1]/div/div[1]";
        Element root = doc.selectXpath(galleryRootXpath).first();
        if (root != null) {
            for (Element img : root.select("img")) {
                String src = img.attr("src");
                if (src == null || src.isBlank()) continue;
                String full = absUrl(src);
                photos.add(full);
            }
        }

        return new ArrayList<>(photos);
    }
}
