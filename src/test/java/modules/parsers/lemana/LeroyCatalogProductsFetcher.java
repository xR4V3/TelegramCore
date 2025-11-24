package modules.parsers.lemana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LeroyCatalogProductsFetcher {

    private static final String BASE_URL = "https://b2b.lemanapro.ru";
    private static final String API_URL  = BASE_URL + "/execute/GetProductsForCatalogQuery";

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private String authToken;

    // фильтр по складу при разборе ответа
    private static final String TARGET_STORE_NAME = "Лемана ПРО Алтуфьево";

    // 🔹 Сюда будем складывать SelectedEnumFilters из URL
    private final List<EnumFilter> enumFilters = new ArrayList<>();

    // 🔹 sort из URL (&sort=8)
    private int sortId = 0;

    public LeroyCatalogProductsFetcher() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public LeroyCatalogProductsFetcher(OkHttpClient client) {
        this.client = client;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    /**
     * Прямой вызов по CategoryFamilyId, БЕЗ фильтров из URL.
     */
    public List<String> getVendorCodesByCategoryFamilyId(String categoryFamilyId) throws IOException {
        // на всякий случай очищаем фильтры
        enumFilters.clear();
        sortId = 0;
        return fetchVendorCodes(categoryFamilyId);
    }

    /**
     * Вызов по URL вида:
     * https://b2b.lemanapro.ru/catalog-fam/.../CategoryFamilyId?06575=AUTOEXPRESS&sort=8&...
     * Берём последний сегмент пути как CategoryFamilyId (5fdd5ce0-...),
     * а query-параметры превращаем в SelectedEnumFilters / SortType.Id.
     */
    public List<String> getVendorCodesByCatalogUrl(String catalogUrl) throws IOException {
        String categoryFamilyId = extractCategoryFamilyIdFromUrl(catalogUrl);
        if (categoryFamilyId == null || categoryFamilyId.isEmpty()) {
            throw new IllegalArgumentException("Не удалось извлечь CategoryFamilyId из URL: " + catalogUrl);
        }
        System.out.println("CategoryFamilyId из URL: " + categoryFamilyId);

        // 🔹 Разбираем query-параметры (sort, 06575=AUTOEXPRESS, 22088=… и т.п.)
        parseFiltersFromUrl(catalogUrl);

        return fetchVendorCodes(categoryFamilyId);
    }

    private String extractCategoryFamilyIdFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null) return null;
            String[] parts = path.split("/");
            String last = null;
            for (String p : parts) {
                if (p != null && !p.isEmpty()) last = p;
            }
            return last;
        } catch (Exception e) {
            System.err.println("Ошибка при парсинге URL: " + e.getMessage());
            return null;
        }
    }

    /**
     * Разбор query-параметров URL:
     *  - sort=8          → sortId = 8
     *  - 06575=AUTOEXPRESS → enumFilters: Id=06575, Values=[AUTOEXPRESS]
     *  - 22088=...       → enumFilters: Id=22088, Values=[...]
     */
    private void parseFiltersFromUrl(String url) {
        enumFilters.clear();
        sortId = 0;

        try {
            URI uri = URI.create(url);
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isEmpty()) {
                System.out.println("В URL нет query-параметров, фильтры не используются.");
                return;
            }

            Map<String, EnumFilter> byId = new LinkedHashMap<>();

            for (String pair : rawQuery.split("&")) {
                if (pair.isEmpty()) continue;
                int idx = pair.indexOf('=');
                String name, value;
                if (idx >= 0) {
                    name  = pair.substring(0, idx);
                    value = pair.substring(idx + 1);
                } else {
                    name  = pair;
                    value = "";
                }

                name  = URLDecoder.decode(name,  StandardCharsets.UTF_8);
                value = URLDecoder.decode(value, StandardCharsets.UTF_8);

                if ("sort".equals(name)) {
                    try {
                        sortId = Integer.parseInt(value);
                    } catch (NumberFormatException ignore) {}
                    continue;
                }

                // eligibilityByStores можно здесь разобрать, но сейчас фильтр по складу делается по Amounts,
                // поэтому в тело запроса его не кладём, только местный TARGET_STORE_NAME.
                if ("eligibilityByStores".equals(name)) {
                    // если захочешь — можно тут сохранять список магазинов
                    continue;
                }

                // 🔹 Все чисто цифровые имена считаем Id для SelectedEnumFilters
                if (name.matches("\\d+")) {
                    EnumFilter f = byId.computeIfAbsent(name, id -> new EnumFilter(id));
                    // значение может быть одно или несколько через запятую
                    for (String v : value.split(",")) {
                        String vv = v.trim();
                        if (!vv.isEmpty() && !f.values.contains(vv)) {
                            f.values.add(vv);
                        }
                    }
                }
            }

            enumFilters.addAll(byId.values());

            System.out.println("Из URL получено enum-фильтров: " + enumFilters.size() +
                    ", sortId=" + sortId);

        } catch (Exception e) {
            System.err.println("Ошибка при разборе фильтров из URL: " + e.getMessage());
        }
    }

    private List<String> fetchVendorCodes(String categoryFamilyId) throws IOException {
        final int pageSize = 20;
        int skip = 0;

        Set<String> vendorCodes = new LinkedHashSet<>();

        while (true) {
            String requestJson = buildRequestBody(categoryFamilyId, skip, pageSize);
            System.out.println("==> Catalog request: Skip=" + skip + ", Take=" + pageSize);

            RequestBody body = RequestBody.create(
                    requestJson,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("authorization", authToken)
                    .addHeader("Accept", "*/*")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                System.out.println("HTTP " + response.code() + " " + response.message());
                if (!response.isSuccessful()) {
                    System.err.println("Response body: " + responseBody);
                    throw new IOException("HTTP error " + response.code() + " - " + response.message());
                }

                System.out.println("RAW catalog response (обрезано до 1000 символов):");
                System.out.println(responseBody.substring(0, Math.min(1000, responseBody.length())));

                PageResult page = parsePage(responseBody);
                List<String> pageCodes = page.codes;
                int totalOnPage = page.totalOnPage;

                System.out.println("Товаров на странице (от сервера): " + totalOnPage +
                        ", подошло по фильтру склада: " + pageCodes.size());

                if (totalOnPage == 0) {
                    break;
                }

                boolean anyNew = vendorCodes.addAll(pageCodes);
                if (!anyNew && totalOnPage < pageSize) {
                    break;
                }

                if (totalOnPage < pageSize) {
                    break;
                }

                skip += pageSize;
            }
        }

        return new ArrayList<>(vendorCodes);
    }

    private String buildRequestBody(String categoryFamilyId, int skip, int take) throws IOException {
        ObjectNode inner = mapper.createObjectNode();
        inner.put("Skip", skip);
        inner.put("Take", take);

        inner.putNull("CategoryId");
        inner.putNull("SubcategoryId");
        inner.putNull("FamilySubcategoryId");
        inner.put("CategoryFamilyId", categoryFamilyId);
        inner.putNull("SubcategoryFamilyId");
        inner.putNull("FamilyId");

        // 🔹 SelectedDoubleFilters пока пустые
        inner.putArray("SelectedDoubleFilters");

        // 🔹 SelectedEnumFilters — из URL
        ArrayNode enumArr = inner.putArray("SelectedEnumFilters");
        for (EnumFilter f : enumFilters) {
            ObjectNode fNode = enumArr.addObject();
            // в примере от фронта Id был строкой ("06575"), так и делаем
            fNode.put("Id", f.id);
            ArrayNode vals = fNode.putArray("Values");
            for (String v : f.values) {
                vals.add(v);
            }
        }

        ObjectNode sortType = inner.putObject("SortType");
        sortType.put("Id", sortId);  // 0 по умолчанию, либо то, что пришло из &sort=
        sortType.putNull("Name");

        inner.putNull("StoresCodes"); // как в примере тела

        String innerJsonString = mapper.writeValueAsString(inner);

        ObjectNode outer = mapper.createObjectNode();
        outer.put("commandOrQueryName", "GetProductsForCatalogQuery");
        outer.put("input", innerJsonString);

        return mapper.writeValueAsString(outer);
    }

    private PageResult parsePage(String jsonResponse) throws IOException {
        JsonNode root = mapper.readTree(jsonResponse);

        if (!root.has("result")) {
            throw new IOException("No 'result' field in response");
        }

        JsonNode resultNode = root.get("result");
        JsonNode data;

        if (resultNode.isTextual()) {
            String resultJson = resultNode.asText();
            System.out.println("result как TEXT, длина=" + resultJson.length());
            data = mapper.readTree(resultJson);
        } else {
            System.out.println("result как JSON-объект типа: " + resultNode.getNodeType());
            data = resultNode;
        }

        PageResult pr = new PageResult();
        pr.codes = new ArrayList<>();
        pr.totalOnPage = 0;

        JsonNode foundProducts = data.get("FoundProducts");
        if (foundProducts != null && foundProducts.isArray()) {
            pr.totalOnPage = foundProducts.size();

            for (JsonNode productNode : foundProducts) {
                JsonNode codeNode = productNode.get("LeroyMerlinId");
                if (codeNode == null || !codeNode.isTextual()) {
                    continue;
                }
                String vendorCode = codeNode.asText();

                JsonNode amountsNode = productNode.get("Amounts");
                if (amountsNode == null || !amountsNode.isArray()) {
                    continue;
                }

                boolean ok = false;
                for (JsonNode storeNode : amountsNode) {
                    JsonNode nameNode  = storeNode.get("Name");
                    JsonNode stockNode = storeNode.get("AmountInStock");
                    if (nameNode == null || stockNode == null) continue;

                    String storeName = nameNode.asText();
                    int amount       = stockNode.asInt();

                    if (TARGET_STORE_NAME.equals(storeName) && amount > 1) {
                        ok = true;
                        break;
                    }
                }

                if (ok) {
                    pr.codes.add(vendorCode);
                }
            }

            System.out.println("На странице всего товаров: " + pr.totalOnPage +
                    ", подошло по складу: " + pr.codes.size());
            return pr;
        }

        // fallback – если нет FoundProducts
        Set<String> fallbackCodes = new LinkedHashSet<>();
        collectLeroyMerlinIds(data, fallbackCodes);
        pr.totalOnPage = fallbackCodes.size();
        pr.codes.addAll(fallbackCodes);
        System.out.println("Fallback: найдено LeroyMerlinId: " + pr.totalOnPage);
        return pr;
    }

    private void collectLeroyMerlinIds(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) return;

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode value   = entry.getValue();

                if ("LeroyMerlinId".equals(fieldName) && value.isTextual()) {
                    out.add(value.asText());
                }

                collectLeroyMerlinIds(value, out);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectLeroyMerlinIds(child, out);
            }
        }
    }

    private static class PageResult {
        List<String> codes;
        int totalOnPage;
    }

    // внутренний класс для SelectedEnumFilters
    private static class EnumFilter {
        final String id;
        final List<String> values = new ArrayList<>();

        EnumFilter(String id) {
            this.id = id;
        }
    }
}
