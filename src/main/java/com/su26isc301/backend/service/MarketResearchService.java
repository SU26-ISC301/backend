package com.su26isc301.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.su26isc301.backend.dto.response.MarketResearchResponse;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MarketResearchService {

    private static final String DEFAULT_CATEGORY_ID = "dt-do-dien-tu";
    private static final int MAX_PRODUCTS_PER_SOURCE = 8;
    private static final List<String> QUERY_STOP_WORDS = List.of("va", "do", "the", "bi", "may");
    private static final List<String> DEVICE_INTENT_TERMS = List.of(
            "iphone", "ipad", "samsung", "galaxy", "oppo", "xiaomi", "redmi", "realme", "vivo",
            "nokia", "honor", "huawei", "macbook", "laptop", "dien thoai", "may tinh bang"
    );
    private static final List<String> ACCESSORY_INTENT_TERMS = List.of(
            "phu kien", "op lung", "bao da", "sac", "cap", "cable", "kinh cuong luc", "cuong luc",
            "pin du phong", "tai nghe", "loa", "soundbar", "day deo", "mieng dan", "adapter"
    );
    private static final List<String> ACCESSORY_NOISE_TERMS = List.of(
            "op lung", "bao da", "kinh cuong luc", "cuong luc", "mieng dan", "dan man hinh",
            "sac", "cap", "cable", "cu sac", "day sac", "adapter", "pin du phong", "gia do", "pop socket"
    );
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/125.0 Safari/537.36";
    private static final List<MarketResearchResponse.MarketCategoryOption> CATEGORY_TREE = buildCategoryTree();
    private static final String CELLPHONES_GRAPHQL_URL =
            "https://api.cellphones.com.vn/graphql-search/v2/graphql/query";
    private static final String CDN_CELLPHONES = "https://cdn2.cellphones.com.vn/358x,webp,q100/media/catalog/product";
    private static final List<SourceTarget> SOURCE_TARGETS = List.of(
            new SourceTarget("CellPhoneS", "https://cellphones.com.vn/catalogsearch/result?q=%s", "https://cellphones.com.vn", 94, "GRAPHQL"),
            new SourceTarget("FPT Shop", "https://fptshop.com.vn/tim-kiem?s=%s", "https://fptshop.com.vn", 95, "HTML"),
            new SourceTarget("Điện Máy Xanh", "https://www.dienmayxanh.com/tim-kiem?key=%s", "https://www.dienmayxanh.com", 94, "HTML"),
            new SourceTarget("Di Động Việt", "https://didongviet.vn/search/?q=%s", "https://didongviet.vn", 93, "HTML"),
            new SourceTarget("TopZone", "https://www.topzone.vn/tim-kiem?key=%s", "https://www.topzone.vn", 93, "TGDD_DOM")
    );

    private final ProfileRepository profileRepository;
    private final VendorRepository vendorRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MarketResearchResponse.MarketCategoryOption> getCategoryTree() {
        return CATEGORY_TREE;
    }

    public MarketResearchResponse getVendorMarketResearch(String email, String categoryId, String source, String query) {
        MarketResearchResponse.MarketCategoryOption parent = getVendorParentCategory(email);
        String selectedCategoryId = resolveSelectedCategoryId(parent, categoryId);
        String selectedName = findCategoryPath(List.of(parent), selectedCategoryId).stream()
                .reduce((first, second) -> second)
                .map(MarketResearchResponse.MarketCategoryOption::getName)
                .orElse(parent.getName());
        return buildRealMarketResearch(List.of(parent), parent, selectedCategoryId, selectedName, source, query);
    }

    public MarketResearchResponse getAdminMarketResearch(String categoryId, String source, String query) {
        MarketResearchResponse.MarketCategoryOption parent = CATEGORY_TREE.stream()
                .filter(item -> categoryContainsId(item, categoryId))
                .findFirst()
                .orElse(CATEGORY_TREE.getFirst());
        String selectedCategoryId = resolveSelectedCategoryId(parent, categoryId);
        String selectedName = findCategoryPath(CATEGORY_TREE, selectedCategoryId).stream()
                .reduce((first, second) -> second)
                .map(MarketResearchResponse.MarketCategoryOption::getName)
                .orElse(parent.getName());
        return buildRealMarketResearch(CATEGORY_TREE, parent, selectedCategoryId, selectedName, source, query);
    }

    public MarketResearchResponse syncAdminMarketResearch(String categoryId) {
        return getAdminMarketResearch(categoryId, null, null);
    }

    private MarketResearchResponse buildRealMarketResearch(
            List<MarketResearchResponse.MarketCategoryOption> categoryTree,
            MarketResearchResponse.MarketCategoryOption parent,
            String selectedCategoryId,
            String selectedName,
            String source,
            String query
    ) {
        String effectiveQuery = StringUtils.hasText(query) ? query.trim() : selectedName;
        List<MarketResearchResponse.PriceSource> sources = SOURCE_TARGETS.stream()
                .filter(target -> !StringUtils.hasText(source) || normalize(target.name()).equals(normalize(source)))
                .map(target -> scrapeSource(target, effectiveQuery))
                .toList();
        List<MarketResearchResponse.ProductItem> products = sources.stream()
                .flatMap(item -> Optional.ofNullable(item.getProducts()).orElse(List.of()).stream())
                .filter(item -> item.getPrice() != null && item.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        return MarketResearchResponse.builder()
                .dataMode("REAL")
                .updatedAt(ZonedDateTime.now())
                .categories(categoryTree)
                .selectedCategory(buildInsight(parent, selectedCategoryId, selectedName, effectiveQuery, products))
                .overview(buildOverview(sources))
                .sources(sources)
                .build();
    }

    private MarketResearchResponse.MarketCategoryInsight buildInsight(
            MarketResearchResponse.MarketCategoryOption parent,
            String categoryId,
            String categoryName,
            String keyword,
            List<MarketResearchResponse.ProductItem> products
    ) {
        BigDecimal marketAverage = averagePrice(products);
        BigDecimal recommended = marketAverage.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : roundMoney(marketAverage.multiply(BigDecimal.valueOf(0.98)));

        return MarketResearchResponse.MarketCategoryInsight.builder()
                .id(categoryId)
                .name(categoryName)
                .parentCategoryId(parent.getId())
                .parentCategoryName(parent.getName())
                .keyword(keyword)
                .demand(Math.min(100, products.size() * 8))
                .trend(products.isEmpty() ? "Chưa đủ dữ liệu" : "Dữ liệu public mới nhất")
                .recommendedPrice(recommended)
                .marketAverage(marketAverage)
                .competitorCount((int) SOURCE_TARGETS.stream().filter(source -> hasProducts(source.name(), products)).count())
                .sampleCount(products.size())
                .status(products.isEmpty() ? "Chưa có dữ liệu" : "Có dữ liệu thật")
                .strategy(products.isEmpty()
                        ? "Chưa lấy được dữ liệu public cho từ khóa này. Hãy thử từ khóa cụ thể hơn hoặc cập nhật lại sau."
                        : "Giá đề xuất được tính từ dữ liệu public vừa lấy trên 5 shop. Nên đối chiếu lại trước khi nhập hàng hoặc chạy khuyến mãi.")
                .categoryPath(findCategoryPath(List.of(parent), categoryId).stream()
                        .map(MarketResearchResponse.MarketCategoryOption::getName)
                        .toList())
                .build();
    }

    private boolean hasProducts(String sourceName, List<MarketResearchResponse.ProductItem> products) {
        return products.stream().anyMatch(item -> item.getUrl() != null && item.getUrl().contains(sourceHost(sourceName)));
    }

    private String sourceHost(String sourceName) {
        return SOURCE_TARGETS.stream()
                .filter(item -> item.name().equals(sourceName))
                .map(item -> item.rootUrl().replace("https://", ""))
                .findFirst()
                .orElse("");
    }

    private MarketResearchResponse.PriceSource scrapeSource(SourceTarget target, String query) {
        String url = target.searchUrl().formatted(URLEncoder.encode(query, StandardCharsets.UTF_8));
        try {
            if ("GRAPHQL".equals(target.scrapeMode())) {
                return scrapeCellPhonesS(target, query);
            }
            Document document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .referrer(target.rootUrl())
                    .timeout(15000)
                    .followRedirects(true)
                    .get();
            List<MarketResearchResponse.ProductItem> products;
            if ("TGDD_DOM".equals(target.scrapeMode())) {
                products = extractProductsFromTgddDom(document, target, query);
            } else {
                products = extractProducts(document, target, query);
            }
            return buildPriceSource(target, url, products, null);
        } catch (Exception ex) {
            return unavailableSource(target, url, "Chưa lấy được dữ liệu public từ nguồn này, sẽ cập nhật sau.");
        }
    }

    /**
     * Scrapes CellPhoneS using their internal GraphQL search API.
     * The search page uses Nuxt.js client-side rendering so products are NOT available
     * in the SSR HTML. The API endpoint was discovered from their JS bundle.
     * Province 1 = Hà Nội (returns national results when no specific province stock).
     */
    private MarketResearchResponse.PriceSource scrapeCellPhonesS(SourceTarget target, String query) {
        String searchUrl = target.searchUrl().formatted(URLEncoder.encode(query, StandardCharsets.UTF_8));
        try {
            String graphqlQuery = String.format(
                    "{\"query\":\"query advanced_search { advanced_search(user_query: { terms: \\\"%s\\\", province: 1 } page: 1) { products { product_id name sku url_path price special_price thumbnail } } }\",\"variables\":{}}",
                    query.replace("\"", "\\\"")
            );
            String responseBody = Jsoup.connect(CELLPHONES_GRAPHQL_URL)
                    .userAgent(USER_AGENT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Origin", "https://cellphones.com.vn")
                    .header("Referer", "https://cellphones.com.vn/")
                    .header("X-Client-Type", "web")
                    .requestBody(graphqlQuery)
                    .ignoreContentType(true)
                    .timeout(15000)
                    .post()
                    .body()
                    .text();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode productsNode = root.path("data").path("advanced_search").path("products");
            List<MarketResearchResponse.ProductItem> products = new ArrayList<>();
            if (productsNode.isArray()) {
                for (JsonNode p : productsNode) {
                    String name = firstText(p, "name");
                    BigDecimal price = firstMoney(p, "special_price", "price");
                    if (!StringUtils.hasText(name) || price == null || !productMatchesQuery(name, query)) continue;
                    String urlPath = firstText(p, "url_path");
                    String thumbnail = firstText(p, "thumbnail");
                    String productUrl = urlPath != null ? target.rootUrl() + "/" + urlPath : null;
                    String imageUrl = thumbnail != null
                            ? (thumbnail.startsWith("http") ? thumbnail : CDN_CELLPHONES + thumbnail)
                            : null;
                    products.add(MarketResearchResponse.ProductItem.builder()
                            .name(cleanText(name))
                            .price(price)
                            .originalPrice(firstMoney(p, "price"))
                            .url(productUrl)
                            .imageUrl(imageUrl)
                            .build());
                    if (products.size() >= MAX_PRODUCTS_PER_SOURCE) break;
                }
            }
            return buildPriceSource(target, searchUrl, products, null);
        } catch (Exception ex) {
            return unavailableSource(target, searchUrl, "Chưa lấy được dữ liệu từ CellPhoneS, sẽ cập nhật sau.");
        }
    }

    /**
     * Extracts products from TopZone / TGDD-family sites using the
     * data-name and data-price HTML attributes embedded directly on anchor tags.
     * These sites use server-side rendering with data attributes for analytics/tracking
     * that also contain product data we can scrape directly.
     */
    private List<MarketResearchResponse.ProductItem> extractProductsFromTgddDom(
            Document document,
            SourceTarget target,
            String query
    ) {
        List<MarketResearchResponse.ProductItem> products = new ArrayList<>();
        Elements cards = document.select("a[data-name][data-price]");
        for (Element card : cards) {
            String href = card.attr("href");
            String name = card.attr("data-name");
            String priceAttr = card.attr("data-price").trim();
            // data-price may be a decimal like "36990000.0" - parse as BigDecimal and round to int
            BigDecimal price = null;
            try {
                if (StringUtils.hasText(priceAttr)) {
                    price = new java.math.BigDecimal(priceAttr).setScale(0, java.math.RoundingMode.HALF_UP);
                    if (price.compareTo(BigDecimal.valueOf(10000)) < 0) price = null;
                }
            } catch (NumberFormatException ignored) {
                price = parseMoney(priceAttr.replace(",", "."));
            }
            if (!StringUtils.hasText(name) || price == null || !productMatchesQuery(name, query)) continue;
            String imageUrl = firstImageUrl(card);
            products.add(MarketResearchResponse.ProductItem.builder()
                    .name(cleanText(name))
                    .price(price)
                    .url(toAbsoluteUrl(target.rootUrl(), href))
                    .imageUrl(toSourceImageUrl(target, imageUrl))
                    .build());
            if (products.size() >= MAX_PRODUCTS_PER_SOURCE) break;
        }
        return products;
    }

    private List<MarketResearchResponse.ProductItem> extractProducts(
            Document document,
            SourceTarget target,
            String query
    ) {
        Map<String, MarketResearchResponse.ProductItem> products = new LinkedHashMap<>();
        Map<String, String> imageUrlsByName = extractImageUrlsByName(document, target, query);
        addProducts(products, extractProductsFromJson(document, target, query));
        addProducts(products, extractProductsFromDom(document, target, query));
        addProducts(products, extractProductsFromText(document.html(), target, query));
        return products.values().stream()
                .peek(item -> {
                    if (!StringUtils.hasText(item.getImageUrl())) {
                        item.setImageUrl(imageUrlsByName.get(normalize(item.getName())));
                    }
                })
                .limit(MAX_PRODUCTS_PER_SOURCE)
                .toList();
    }

    private Map<String, String> extractImageUrlsByName(
            Document document,
            SourceTarget target,
            String query
    ) {
        Map<String, String> images = new LinkedHashMap<>();
        Elements cards = document.select("a[aria-label][href]:has(img), a[data-name][href]:has(img)");
        for (Element card : cards) {
            String name = firstNonBlank(card.attr("aria-label"), card.attr("data-name"), card.attr("title"), card.text());
            if (!StringUtils.hasText(name) || !productMatchesQuery(name, query)) continue;
            String imageUrl = toSourceImageUrl(target, firstImageUrl(card));
            if (StringUtils.hasText(imageUrl)) {
                images.putIfAbsent(normalize(name), imageUrl);
            }
        }
        return images;
    }

    private List<MarketResearchResponse.ProductItem> extractProductsFromJson(
            Document document,
            SourceTarget target,
            String query
    ) {
        List<MarketResearchResponse.ProductItem> products = new ArrayList<>();
        for (Element script : document.select("script[type=application/ld+json], script#__NEXT_DATA__")) {
            String json = script.data();
            if (!StringUtils.hasText(json)) {
                json = script.html();
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                collectJsonProducts(root, target, query, products);
            } catch (Exception ignored) {
                // Some sites use React Server Components instead of plain JSON.
            }
        }
        return products;
    }

    private void collectJsonProducts(
            JsonNode node,
            SourceTarget target,
            String query,
            List<MarketResearchResponse.ProductItem> products
    ) {
        if (node == null || node.isNull()) return;

        if (node.isObject()) {
            String name = firstText(node, "displayName", "product", "name", "productName", "title");
            BigDecimal price = firstMoney(node, "currentPrice", "price", "salePrice", "special_price", "final_price", "lowPrice");
            if (price == null && node.has("offers")) {
                price = firstMoney(node.get("offers"), "price", "lowPrice", "highPrice");
            }
            if (StringUtils.hasText(name) && price != null && productMatchesQuery(name, query)) {
                String slug = firstText(node, "slug", "productSlug", "redirect_url", "url", "href");
                String image = firstText(node, "thumbnail", "imageUrl", "image");
                if (!StringUtils.hasText(image) && node.has("image") && node.get("image").isObject()) {
                    image = firstText(node.get("image"), "src", "url");
                }
                products.add(MarketResearchResponse.ProductItem.builder()
                        .name(cleanText(name))
                        .price(price)
                        .originalPrice(firstMoney(node, "originalPrice", "list_price", "oldPrice"))
                        .url(toAbsoluteUrl(target.rootUrl(), slug))
                        .imageUrl(toSourceImageUrl(target, image))
                        .promo(cleanHtml(firstText(node, "promotion_info", "promo", "description")))
                        .rating(firstMoney(node, "avg_point", "rating", "avgRating"))
                        .availability(firstText(node, "status", "product_status", "availability"))
                        .build());
            }
        }

        if (node.isContainerNode()) {
            node.elements().forEachRemaining(child -> collectJsonProducts(child, target, query, products));
        }
    }

    private List<MarketResearchResponse.ProductItem> extractProductsFromDom(
            Document document,
            SourceTarget target,
            String query
    ) {
        List<MarketResearchResponse.ProductItem> products = new ArrayList<>();
        Elements cards = document.select(".listproduct .item, li.item, .product-item, .product-card, [class*=ProductCard], [class*=product-card], div:has(> div.cardInfo):has(a[aria-label][href])");
        for (Element card : cards) {
            String name = firstOwnText(card, ".cardInfo h3, .cardInfo a[title], .product-title, h3, h2, [class*=name], [class*=Name], a[aria-label], a[title]");
            BigDecimal price = firstMoneyText(card, ".price, [class*=price], [class*=Price], .cardInfo .b1-semibold");
            if (!StringUtils.hasText(name) || price == null || !productMatchesQuery(name, query)) {
                continue;
            }
            Element link = card.selectFirst("a[href]");
            products.add(MarketResearchResponse.ProductItem.builder()
                    .name(cleanText(name))
                    .price(price)
                    .url(toAbsoluteUrl(target.rootUrl(), link == null ? null : link.attr("href")))
                    .imageUrl(toSourceImageUrl(target, firstImageUrl(card)))
                    .promo(cleanText(firstOwnText(card, ".item-gift, [class*=promo], [class*=Promotion]")))
                    .rating(parseMoney(firstOwnText(card, ".vote-txt, [class*=rating], [class*=Rating]")))
                    .availability(StringUtils.hasText(firstOwnText(card, ".item-txt-online")) ? firstOwnText(card, ".item-txt-online") : null)
                    .build());
        }
        return products;
    }

    private List<MarketResearchResponse.ProductItem> extractProductsFromText(
            String html,
            SourceTarget target,
            String query
    ) {
        String text = html
                .replace("\\u002F", "/")
                .replace("\\u0026", "&")
                .replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("\\\"", "\"");
        Pattern pattern = Pattern.compile(
                "\"(?:displayName|product|name)\"\\s*:\\s*\"([^\"<>]{4,180})\"(.{0,1800}?)\"(?:currentPrice|price|salePrice|lowPrice)\"\\s*:\\s*\"?(\\d{5,12})\"?",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(text);
        List<MarketResearchResponse.ProductItem> products = new ArrayList<>();
        while (matcher.find() && products.size() < MAX_PRODUCTS_PER_SOURCE) {
            String name = cleanText(matcher.group(1));
            if (!productMatchesQuery(name, query)) continue;
            String scope = matcher.group(2);
            BigDecimal price = parseMoney(matcher.group(3));
            String slug = firstRegex(scope, "\"(?:slug|productSlug|redirect_url|url|href)\"\\s*:\\s*\"([^\"]+)\"");
            String image = firstRegex(scope, "\"(?:thumbnail|src|imageUrl)\"\\s*:\\s*\"([^\"]+)\"");
            String promo = firstRegex(scope, "\"(?:promotion_info|description|promo)\"\\s*:\\s*\"([^\"]{1,220})\"");
            products.add(MarketResearchResponse.ProductItem.builder()
                    .name(name)
                    .price(price)
                    .url(toAbsoluteUrl(target.rootUrl(), slug))
                    .imageUrl(toSourceImageUrl(target, image))
                    .promo(cleanHtml(promo))
                    .build());
        }
        return products;
    }

    private void addProducts(Map<String, MarketResearchResponse.ProductItem> target, List<MarketResearchResponse.ProductItem> items) {
        for (MarketResearchResponse.ProductItem item : items) {
            if (!StringUtils.hasText(item.getName()) || item.getPrice() == null) continue;
            String key = normalize(item.getName()) + "-" + item.getPrice();
            MarketResearchResponse.ProductItem existing = target.get(key);
            if (existing == null || (!StringUtils.hasText(existing.getImageUrl()) && StringUtils.hasText(item.getImageUrl()))) {
                target.put(key, item);
            }
        }
    }

    private MarketResearchResponse.PriceSource buildPriceSource(
            SourceTarget target,
            String url,
            List<MarketResearchResponse.ProductItem> products,
            String message
    ) {
        if (products.isEmpty()) {
            return unavailableSource(target, url, message == null
                    ? "Chưa có dữ liệu phù hợp, sẽ cập nhật sau."
                    : message);
        }

        BigDecimal min = products.stream().map(MarketResearchResponse.ProductItem::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = products.stream().map(MarketResearchResponse.ProductItem::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal avg = averagePrice(products);
        BigDecimal rating = products.stream()
                .map(MarketResearchResponse.ProductItem::getRating)
                .filter(item -> item != null && item.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long ratingCount = products.stream()
                .filter(item -> item.getRating() != null && item.getRating().compareTo(BigDecimal.ZERO) > 0)
                .count();

        return MarketResearchResponse.PriceSource.builder()
                .source(target.name())
                .min(min)
                .avg(avg)
                .max(max)
                .sales(products.size() + " sản phẩm")
                .rating(ratingCount == 0 ? BigDecimal.ZERO : rating.divide(BigDecimal.valueOf(ratingCount), 1, RoundingMode.HALF_UP))
                .promo("Dữ liệu public")
                .trust(target.trust())
                .status("OK")
                .message("Đã lấy dữ liệu thật từ trang public.")
                .url(url)
                .productCount(products.size())
                .products(products)
                .build();
    }

    private MarketResearchResponse.PriceSource unavailableSource(SourceTarget target, String url, String message) {
        return MarketResearchResponse.PriceSource.builder()
                .source(target.name())
                .min(BigDecimal.ZERO)
                .avg(BigDecimal.ZERO)
                .max(BigDecimal.ZERO)
                .sales("0 sản phẩm")
                .rating(BigDecimal.ZERO)
                .promo("Chưa có")
                .trust(target.trust())
                .status("PENDING")
                .message(message)
                .url(url)
                .productCount(0)
                .products(List.of())
                .build();
    }

    private MarketResearchResponse.MarketOverview buildOverview(List<MarketResearchResponse.PriceSource> sources) {
        List<MarketResearchResponse.PriceSource> available = sources.stream()
                .filter(item -> "OK".equals(item.getStatus()))
                .toList();
        if (available.isEmpty()) {
            return MarketResearchResponse.MarketOverview.builder()
                    .totalSources(0)
                    .priceSpreadPercent(BigDecimal.ZERO)
                    .averageTrust(BigDecimal.ZERO)
                    .build();
        }

        MarketResearchResponse.PriceSource lowest = available.stream()
                .min(Comparator.comparing(MarketResearchResponse.PriceSource::getMin))
                .orElse(available.getFirst());
        MarketResearchResponse.PriceSource trusted = available.stream()
                .max(Comparator.comparing(MarketResearchResponse.PriceSource::getTrust))
                .orElse(available.getFirst());
        BigDecimal min = available.stream().map(MarketResearchResponse.PriceSource::getMin).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = available.stream().map(MarketResearchResponse.PriceSource::getMax).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal spread = min.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : max.subtract(min).multiply(BigDecimal.valueOf(100)).divide(min, 1, RoundingMode.HALF_UP);
        BigDecimal averageTrust = BigDecimal.valueOf(available.stream()
                .mapToInt(MarketResearchResponse.PriceSource::getTrust)
                .average()
                .orElse(0)).setScale(1, RoundingMode.HALF_UP);

        return MarketResearchResponse.MarketOverview.builder()
                .totalSources(available.size())
                .lowestPriceSource(lowest.getSource())
                .lowestPrice(lowest.getMin())
                .highestTrustSource(trusted.getSource())
                .highestTrust(trusted.getTrust())
                .priceSpreadPercent(spread)
                .averageTrust(averageTrust)
                .build();
    }

    private MarketResearchResponse.MarketCategoryOption getVendorParentCategory(String email) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản Vendor"));
        Vendor vendor = vendorRepository.findByProfile(profile)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy shop của Vendor"));
        String vendorCategory = vendor.getCategory();
        return CATEGORY_TREE.stream()
                .filter(item -> normalize(item.getId()).equals(normalize(vendorCategory)) ||
                        normalize(item.getName()).equals(normalize(vendorCategory)))
                .findFirst()
                .orElseGet(() -> CATEGORY_TREE.stream()
                        .filter(item -> item.getId().equals(DEFAULT_CATEGORY_ID))
                        .findFirst()
                        .orElse(CATEGORY_TREE.getFirst()));
    }

    private String resolveSelectedCategoryId(MarketResearchResponse.MarketCategoryOption parent, String categoryId) {
        if (StringUtils.hasText(categoryId) && categoryContainsId(parent, categoryId.trim())) {
            return categoryId.trim();
        }
        return parent.getId();
    }

    private boolean categoryContainsId(MarketResearchResponse.MarketCategoryOption node, String categoryId) {
        if (!StringUtils.hasText(categoryId) || node == null) return false;
        if (node.getId().equals(categoryId)) return true;
        return node.getChildren() != null && node.getChildren().stream().anyMatch(child -> categoryContainsId(child, categoryId));
    }

    private List<MarketResearchResponse.MarketCategoryOption> findCategoryPath(
            List<MarketResearchResponse.MarketCategoryOption> nodes,
            String categoryId
    ) {
        List<MarketResearchResponse.MarketCategoryOption> path = new ArrayList<>();
        return findCategoryPath(nodes, categoryId, path) ? path : List.of();
    }

    private boolean findCategoryPath(
            List<MarketResearchResponse.MarketCategoryOption> nodes,
            String categoryId,
            List<MarketResearchResponse.MarketCategoryOption> path
    ) {
        for (MarketResearchResponse.MarketCategoryOption node : nodes) {
            path.add(node);
            if (node.getId().equals(categoryId)) return true;
            if (node.getChildren() != null && findCategoryPath(node.getChildren(), categoryId, path)) {
                return true;
            }
            path.removeLast();
        }
        return false;
    }

    private boolean productMatchesQuery(String name, String query) {
        if (!StringUtils.hasText(query)) return true;
        String normalizedName = normalize(name);
        String normalizedQuery = normalize(query);
        List<String> tokens = List.of(normalizedQuery.split("\\s+")).stream()
                .filter(token -> token.length() >= 2 && !QUERY_STOP_WORDS.contains(token))
                .toList();
        if (tokens.isEmpty()) return true;
        boolean hasDeviceIntent = hasAnyTerm(normalizedQuery, DEVICE_INTENT_TERMS);
        boolean strictModelSearch = hasDeviceIntent && tokens.size() <= 5;
        boolean matches = strictModelSearch
                ? tokens.stream().allMatch(normalizedName::contains)
                : tokens.stream().anyMatch(normalizedName::contains);
        return matches && !isAccessoryNoise(normalizedName, normalizedQuery, hasDeviceIntent);
    }

    private boolean isAccessoryNoise(String normalizedName, String normalizedQuery, boolean hasDeviceIntent) {
        if (hasAnyTerm(normalizedQuery, ACCESSORY_INTENT_TERMS)) return false;
        boolean shouldExcludeAccessories = hasDeviceIntent
                || normalizedQuery.contains("dien thoai")
                || normalizedQuery.contains("may tinh bang");
        return shouldExcludeAccessories && hasAnyTerm(normalizedName, ACCESSORY_NOISE_TERMS);
    }

    private boolean hasAnyTerm(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return "";
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String firstText(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return null;
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) continue;
            if (value.isArray() && !value.isEmpty()) value = value.get(0);
            if (value.isObject()) continue;
            String text = value.asText();
            if (StringUtils.hasText(text)) return text;
        }
        return null;
    }

    private BigDecimal firstMoney(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return null;
        for (String key : keys) {
            JsonNode raw = node.get(key);
            BigDecimal value = null;
            if (raw != null && raw.isNumber()) {
                value = raw.decimalValue().setScale(0, RoundingMode.HALF_UP);
            } else {
                value = parseMoney(firstText(node, key));
            }
            if (value != null) return value;
        }
        return null;
    }

    private String firstOwnText(Element element, String selector) {
        Element found = element.selectFirst(selector);
        if (found == null) return null;
        String ariaLabel = found.attr("aria-label");
        if (StringUtils.hasText(ariaLabel)) return ariaLabel;
        String title = found.attr("title");
        if (StringUtils.hasText(title)) return title;
        return found.text();
    }

    private BigDecimal firstMoneyText(Element element, String selector) {
        Elements matches = element.select(selector);
        for (Element match : matches) {
            BigDecimal money = parseMoney(match.text());
            if (money != null) return money;
        }
        return null;
    }

    private String firstRegex(String value, String pattern) {
        if (!StringUtils.hasText(value)) return null;
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    private String firstImageUrl(Element element) {
        for (Element image : element.select("img")) {
            String value = firstNonBlank(
                    image.attr("data-src"),
                    image.attr("src"),
                    firstSrcsetUrl(image.attr("srcset")),
                    firstSrcsetUrl(image.attr("srcSet")),
                    firstSrcsetUrl(image.attr("data-srcset")),
                    image.attr("data-original")
            );
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    private String firstSrcsetUrl(String value) {
        if (!StringUtils.hasText(value)) return null;
        String first = value.split(",")[0].trim();
        int spaceIndex = first.indexOf(' ');
        return spaceIndex > 0 ? first.substring(0, spaceIndex).trim() : first;
    }

    private BigDecimal parseMoney(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        if (trimmed.matches("(?i)^\\d+(\\.\\d+)?e\\d+$")) {
            try {
                return new BigDecimal(trimmed).setScale(0, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (!StringUtils.hasText(digits)) return null;
        try {
            BigDecimal number = new BigDecimal(digits);
            return number.compareTo(BigDecimal.valueOf(10000)) >= 0 ? number : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal averagePrice(List<MarketResearchResponse.ProductItem> products) {
        List<BigDecimal> prices = products.stream()
                .map(MarketResearchResponse.ProductItem::getPrice)
                .filter(item -> item != null && item.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (prices.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return roundMoney(total.divide(BigDecimal.valueOf(prices.size()), 0, RoundingMode.HALF_UP));
    }

    private BigDecimal roundMoney(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(1000));
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.replaceAll("\\s+", " ").trim();
    }

    private String cleanHtml(String value) {
        if (!StringUtils.hasText(value)) return null;
        return cleanText(Jsoup.parse(value.replace("_quot", "\"")).text());
    }

    private String toAbsoluteUrl(String rootUrl, String value) {
        if (!StringUtils.hasText(value)) return null;
        String cleaned = value.trim().replace("\\/", "/");
        if (cleaned.startsWith("//")) return "https:" + cleaned;
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) return cleaned;
        if (cleaned.startsWith("/")) return rootUrl + cleaned;
        if (cleaned.contains(".html") || !cleaned.contains(" ")) return rootUrl + "/" + cleaned;
        return null;
    }

    private String toSourceImageUrl(SourceTarget target, String value) {
        if (!StringUtils.hasText(value)) return null;
        String cleaned = value.trim().replace("\\/", "/");
        if ("Di Động Việt".equals(target.name()) && cleaned.startsWith("files/")) {
            return "https://cdn-v2.didongviet.vn/" + cleaned;
        }
        return toAbsoluteUrl(target.rootUrl(), cleaned);
    }

    private static List<MarketResearchResponse.MarketCategoryOption> buildCategoryTree() {
        return List.of(
                option("dt-do-dien-tu", "Điện thoại & Đồ điện tử", false, List.of(
                        option("phu-kien-dt", "Phụ kiện điện thoại", false, List.of(
                                option("op-lung-bao-da", "Ốp lưng & Bao da"),
                                option("sac-cap-dt", "Sạc & Cáp điện thoại"),
                                option("kinh-cuong-luc", "Kính cường lực"),
                                option("pin-du-phong", "Pin dự phòng"),
                                option("tai-nghe-co-day", "Tai nghe có dây"),
                                option("de-sac-khong-day", "Đế sạc không dây")
                        )),
                        option("am-thanh-video", "Âm thanh & Video", false, List.of(
                                option("loa-bluetooth", "Loa Bluetooth"),
                                option("tai-nghe-bluetooth", "Tai nghe Bluetooth"),
                                option("soundbar", "Soundbar")
                        )),
                        option("thiet-bi-thong-minh", "Thiết bị thông minh & Thiết bị đeo", false, List.of(
                                option("dong-ho-thong-minh", "Đồng hồ thông minh"),
                                option("vong-suc-khoe", "Vòng đeo sức khỏe"),
                                option("smarthome", "Thiết bị nhà thông minh")
                        )),
                        option("dien-thoai-may-tinh-bang", "Điện thoại & Máy tính bảng", false, List.of(
                                option("dien-thoai-thong-minh", "Điện thoại thông minh"),
                                option("may-tinh-bang", "Máy tính bảng")
                        ))
                )),
                option("may-tinh-van-phong", "Máy tính & Thiết bị Văn phòng", false, List.of(
                        option("may-tinh-laptop-bang", "Máy tính để bàn, Laptop & Máy tính bảng", false, List.of(
                                option("may-tinh-de-ban", "Máy tính để bàn"),
                                option("laptop", "Máy tính xách tay"),
                                option("may-tinh-bang-pc", "Máy tính bảng (PC)")
                        )),
                        option("phu-kien-may-tinh", "Phụ kiện máy tính", false, List.of(
                                option("ban-phim", "Bàn phím"),
                                option("chuot-pc", "Chuột máy tính"),
                                option("man-hinh", "Màn hình")
                        ))
                )),
                option("thiet-bi-mang", "Thiết bị mạng", false, List.of(
                        option("router-ap", "Router & Access Point", false, List.of(
                                option("router-wifi", "Router WiFi"),
                                option("access-point", "Access Point"),
                                option("mesh-wifi", "Mesh WiFi System")
                        )),
                        option("switch-hub", "Switch & Hub mạng", false, List.of(
                                option("network-switch", "Network Switch"),
                                option("kvm-switch", "KVM Switch")
                        ))
                )),
                option("tv-giai-tri", "TV & Thiết bị giải trí", false, List.of(
                        option("smart-tv", "Smart TV", false, List.of(
                                option("android-tv", "Android TV"),
                                option("qled-tv", "QLED TV"),
                                option("oled-tv", "OLED TV")
                        )),
                        option("may-chieu", "Máy chiếu", false, List.of(
                                option("projector-mini", "Máy chiếu mini"),
                                option("projector-chuan", "Máy chiếu chuẩn")
                        ))
                ))
        );
    }

    private static MarketResearchResponse.MarketCategoryOption option(String id, String name) {
        return option(id, name, true, List.of());
    }

    private static MarketResearchResponse.MarketCategoryOption option(
            String id,
            String name,
            Boolean selectable,
            List<MarketResearchResponse.MarketCategoryOption> children
    ) {
        return MarketResearchResponse.MarketCategoryOption.builder()
                .id(id)
                .name(name)
                .selectable(selectable)
                .children(children)
                .build();
    }

    private record SourceTarget(String name, String searchUrl, String rootUrl, int trust, String scrapeMode) {
    }
}
