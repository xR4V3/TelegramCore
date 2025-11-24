package modules.parsers.saturn;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SaturnCatalogParser {

    /**
     * Собирает ВСЕ ссылки товаров из каталога (со всех страниц).
     * Пример каталога: https://msk.saturn.net/catalog/trotuarnaya-plitka/
     */
    public List<String> collectProductLinks(String catalogUrl) throws IOException {
        // Убираем дубли и сохраняем порядок
        Set<String> result = new LinkedHashSet<>();

        String baseCatalogUrl = catalogUrl;
        int page = 1;

        while (true) {
            String currentUrl;

            if (page == 1) {
                currentUrl = baseCatalogUrl;
            } else {
                // Со 2-й страницы и далее добавляем ?page=N или &page=N
                if (baseCatalogUrl.contains("?")) {
                    currentUrl = baseCatalogUrl + "&page=" + page;
                } else {
                    currentUrl = baseCatalogUrl + "?page=" + page;
                }
            }

            System.out.println("[Saturn] Загружаю страницу каталога: " + currentUrl);

            Document doc = Jsoup.connect(currentUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.0.0 Safari/537.36")
                    .timeout(30000)
                    .get();

            // 1) Ссылки на товары на текущей странице
            Elements productLinks = doc.select(
                    "#goodslist div.catalog_Level2__goods_list__block ul li " +
                            "div.goods_card_wrap_container div.goods_card_link a"
            );

            if (productLinks.isEmpty()) {
                System.out.println("[Saturn] На странице " + page + " нет товаров, останавливаюсь.");
                break;
            }

            int addedOnPage = 0;

            for (Element a : productLinks) {
                // jsoup сам сделает абсолютный url относительно текущей страницы
                String href = a.absUrl("href");
                if (href == null || href.isBlank()) continue;

                if (result.add(href)) {
                    addedOnPage++;
                }
            }

            System.out.println("[Saturn] Страница " + page +
                    ": найдено ссылок в DOM = " + productLinks.size() +
                    ", уникально добавлено = " + addedOnPage +
                    ", всего собрано уникальных = " + result.size());

            page++;
        }

        return new ArrayList<>(result);
    }
}
