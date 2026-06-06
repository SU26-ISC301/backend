package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.MarketResearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class MarketResearchService {

    private static final String DEFAULT_CATEGORY_ID = "op-lung-bao-da";
    private static final List<MarketResearchResponse.MarketCategoryOption> CATEGORY_TREE = buildCategoryTree();
    private static final List<MarketSample> MARKET_SAMPLES = buildMarketSamples();

    public List<MarketResearchResponse.MarketCategoryOption> getCategoryTree() {
        return CATEGORY_TREE;
    }

    public MarketResearchResponse getAdminMarketResearch(String categoryId, String source, String query) {
        String selectedCategoryId = StringUtils.hasText(categoryId) ? categoryId.trim() : DEFAULT_CATEGORY_ID;
        MarketSample sample = findSample(selectedCategoryId).orElseGet(() -> buildFallbackSample(selectedCategoryId));
        List<MarketResearchResponse.PriceSource> filteredSources = filterSources(sample.sources(), source, query);

        return MarketResearchResponse.builder()
                .dataMode("MOCK")
                .updatedAt(ZonedDateTime.now())
                .categories(CATEGORY_TREE)
                .selectedCategory(toInsight(sample))
                .overview(buildOverview(filteredSources))
                .sources(filteredSources)
                .build();
    }

    public MarketResearchResponse syncAdminMarketResearch(String categoryId) {
        return getAdminMarketResearch(categoryId, null, null);
    }

    private List<MarketResearchResponse.PriceSource> filterSources(
            List<MarketResearchResponse.PriceSource> sources,
            String source,
            String query
    ) {
        String normalizedSource = normalize(source);
        String normalizedQuery = normalize(query);

        return sources.stream()
                .filter(item -> !StringUtils.hasText(source) || normalize(item.getSource()).equals(normalizedSource))
                .filter(item -> !StringUtils.hasText(query) ||
                        normalize(item.getSource() + " " + item.getPromo()).contains(normalizedQuery))
                .toList();
    }

    private MarketResearchResponse.MarketCategoryInsight toInsight(MarketSample sample) {
        return MarketResearchResponse.MarketCategoryInsight.builder()
                .id(sample.id())
                .name(sample.name())
                .keyword(sample.keyword())
                .demand(sample.demand())
                .trend(sample.trend())
                .recommendedPrice(sample.recommendedPrice())
                .marketAverage(sample.marketAverage())
                .competitorCount(sample.competitorCount())
                .sampleCount(sample.sampleCount())
                .status(sample.status())
                .strategy(sample.strategy())
                .categoryPath(findCategoryPath(sample.id()).stream()
                        .map(MarketResearchResponse.MarketCategoryOption::getName)
                        .toList())
                .build();
    }

    private MarketResearchResponse.MarketOverview buildOverview(List<MarketResearchResponse.PriceSource> sources) {
        if (sources.isEmpty()) {
            return MarketResearchResponse.MarketOverview.builder()
                    .totalSources(0)
                    .priceSpreadPercent(BigDecimal.ZERO)
                    .averageTrust(BigDecimal.ZERO)
                    .build();
        }

        MarketResearchResponse.PriceSource lowest = sources.stream()
                .min(Comparator.comparing(MarketResearchResponse.PriceSource::getMin))
                .orElse(sources.getFirst());
        MarketResearchResponse.PriceSource highest = sources.stream()
                .max(Comparator.comparing(MarketResearchResponse.PriceSource::getTrust))
                .orElse(sources.getFirst());
        BigDecimal min = sources.stream()
                .map(MarketResearchResponse.PriceSource::getMin)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal max = sources.stream()
                .map(MarketResearchResponse.PriceSource::getMax)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal averageTrust = BigDecimal.valueOf(
                sources.stream().mapToInt(MarketResearchResponse.PriceSource::getTrust).average().orElse(0)
        ).setScale(1, RoundingMode.HALF_UP);
        BigDecimal spread = min.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : max.subtract(min)
                .multiply(BigDecimal.valueOf(100))
                .divide(min, 1, RoundingMode.HALF_UP);

        return MarketResearchResponse.MarketOverview.builder()
                .totalSources(sources.size())
                .lowestPriceSource(lowest.getSource())
                .lowestPrice(lowest.getMin())
                .highestTrustSource(highest.getSource())
                .highestTrust(highest.getTrust())
                .priceSpreadPercent(spread)
                .averageTrust(averageTrust)
                .build();
    }

    private Optional<MarketSample> findSample(String categoryId) {
        return MARKET_SAMPLES.stream()
                .filter(sample -> sample.id().equals(categoryId))
                .findFirst();
    }

    private MarketSample buildFallbackSample(String categoryId) {
        List<MarketResearchResponse.MarketCategoryOption> path = findCategoryPath(categoryId);
        MarketResearchResponse.MarketCategoryOption leaf = path.isEmpty()
                ? option(categoryId, "Hạng mục đang chọn")
                : path.getLast();
        int seed = leaf.getId().chars().sum();
        BigDecimal basePrice = money(180000 + (long) (seed % 34) * 85000);
        BigDecimal average = roundMoney(basePrice.multiply(BigDecimal.valueOf(1.08)));
        BigDecimal recommended = roundMoney(basePrice.multiply(BigDecimal.valueOf(0.98)));

        List<MarketResearchResponse.PriceSource> sources = List.of(
                source("Shopee Mall", roundMoney(basePrice.multiply(BigDecimal.valueOf(0.86))), roundMoney(basePrice.multiply(BigDecimal.valueOf(1.02))), roundMoney(basePrice.multiply(BigDecimal.valueOf(1.18))), "3.200+", "4.6", "Voucher ngành hàng", 84),
                source("TikTok Shop", roundMoney(basePrice.multiply(BigDecimal.valueOf(0.82))), roundMoney(basePrice.multiply(BigDecimal.valueOf(0.98))), roundMoney(basePrice.multiply(BigDecimal.valueOf(1.13))), "2.600+", "4.5", "Deal livestream", 79),
                source("CellphoneS", roundMoney(basePrice.multiply(BigDecimal.valueOf(0.98))), roundMoney(basePrice.multiply(BigDecimal.valueOf(1.08))), roundMoney(basePrice.multiply(BigDecimal.valueOf(1.22))), "780+", "4.8", "Bảo hành chính hãng", 94),
                source("FPT Shop", roundMoney(basePrice.multiply(BigDecimal.valueOf(1.02))), roundMoney(basePrice.multiply(BigDecimal.valueOf(1.12))), roundMoney(basePrice.multiply(BigDecimal.valueOf(1.26))), "640+", "4.7", "Trả góp 0%", 93),
                source("Điện Máy Xanh", roundMoney(basePrice.multiply(BigDecimal.valueOf(1.04))), roundMoney(basePrice.multiply(BigDecimal.valueOf(1.15))), roundMoney(basePrice.multiply(BigDecimal.valueOf(1.30))), "520+", "4.7", "Đổi trả nhanh", 92)
        );

        return new MarketSample(
                leaf.getId(),
                leaf.getName(),
                leaf.getName(),
                68 + seed % 24,
                seed % 3 == 0 ? "-1,6%" : "+" + (2 + seed % 8) + ",4%",
                recommended,
                average,
                sources.size(),
                54 + seed % 96,
                seed % 3 == 0 ? "Theo dõi" : "Có cơ hội",
                "Dữ liệu mô phỏng cho " + (path.isEmpty()
                        ? leaf.getName()
                        : String.join(" > ", path.stream().map(MarketResearchResponse.MarketCategoryOption::getName).toList()))
                        + "; nên kiểm tra thêm giá thực tế trước khi nhập hàng hoặc chạy khuyến mãi.",
                sources
        );
    }

    private List<MarketResearchResponse.MarketCategoryOption> findCategoryPath(String categoryId) {
        List<MarketResearchResponse.MarketCategoryOption> path = new ArrayList<>();
        return findCategoryPath(CATEGORY_TREE, categoryId, path) ? path : List.of();
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

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return "";
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d");
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

    private static List<MarketSample> buildMarketSamples() {
        return List.of(
                new MarketSample(
                        "op-lung-bao-da",
                        "Ốp lưng & Bao da",
                        "Ốp lưng MagSafe iPhone 15 Pro Max",
                        86,
                        "+12,6%",
                        money(249000),
                        money(272000),
                        6,
                        214,
                        "Có cơ hội",
                        "Đẩy combo cáp + củ sạc, dùng voucher theo giỏ hàng vì biên lợi nhuận phụ kiện còn tốt.",
                        List.of(
                                source("CellphoneS", 259000, 289000, 329000, "2.900+", "4.8", "Mua kèm giảm 10%", 94),
                                source("FPT Shop", 279000, 309000, 349000, "1.600+", "4.7", "Bảo hành 12 tháng", 93),
                                source("Shopee Mall", 219000, 252000, 299000, "9.800+", "4.6", "Flash voucher", 82),
                                source("TikTok Shop", 199000, 238000, 289000, "7.400+", "4.5", "Live deal", 78),
                                source("TopZone", 299000, 339000, 399000, "840+", "4.9", "Hàng Apple MFi", 97),
                                source("Điện Máy Xanh", 269000, 315000, 369000, "1.200+", "4.7", "Đổi trả 30 ngày", 94)
                        )
                ),
                new MarketSample(
                        "tai-nghe-bluetooth",
                        "Tai nghe Bluetooth",
                        "Tai nghe bluetooth chống ồn",
                        81,
                        "+6,9%",
                        money(1290000),
                        money(1385000),
                        5,
                        148,
                        "Nên chạy quảng cáo",
                        "Chạy quảng cáo theo nhóm khách hàng học tập/làm việc, dùng review và video demo chống ồn làm điểm khác biệt.",
                        List.of(
                                source("Shopee Mall", 1190000, 1320000, 1490000, "5.200+", "4.7", "Voucher 12%", 86),
                                source("TikTok Shop", 1090000, 1260000, 1450000, "4.600+", "4.6", "Live sale", 80),
                                source("CellphoneS", 1350000, 1490000, 1690000, "1.100+", "4.8", "Bảo hành chính hãng", 95),
                                source("FPT Shop", 1390000, 1530000, 1720000, "940+", "4.8", "Trả góp 0%", 94),
                                source("Điện Máy Xanh", 1450000, 1580000, 1790000, "860+", "4.7", "Đổi trả nhanh", 93)
                        )
                ),
                new MarketSample(
                        "dong-ho-thong-minh",
                        "Đồng hồ thông minh",
                        "Apple Watch SE GPS 40mm",
                        74,
                        "-1,8%",
                        money(5890000),
                        money(6210000),
                        5,
                        72,
                        "Cẩn trọng tồn kho",
                        "Không nhập thêm quá nhiều; ưu tiên bán kèm dây đeo và bảo hành mở rộng để tăng giá trị đơn hàng.",
                        List.of(
                                source("TopZone", 6190000, 6490000, 6990000, "520+", "4.9", "Thu cũ đổi mới", 98),
                                source("FPT Shop", 5990000, 6290000, 6790000, "610+", "4.8", "Voucher 300K", 96),
                                source("CellphoneS", 5790000, 6120000, 6590000, "780+", "4.8", "Tặng dây đeo", 95),
                                source("Shopee Mall", 5590000, 5920000, 6390000, "1.900+", "4.6", "Freeship", 85),
                                source("TikTok Shop", 5490000, 5840000, 6290000, "1.250+", "4.5", "Deal livestream", 79)
                        )
                ),
                new MarketSample(
                        "laptop",
                        "Máy tính xách tay",
                        "MacBook Air M2 13 inch 256GB",
                        78,
                        "+3,1%",
                        money(21990000),
                        money(22720000),
                        5,
                        84,
                        "Theo dõi",
                        "Không cần đua giá quá mạnh; nhấn bảo hành, đổi trả và hỗ trợ kỹ thuật để giữ biên lợi nhuận.",
                        List.of(
                                source("TopZone", 22490000, 22990000, 23690000, "680+", "4.9", "Trả góp 0%", 98),
                                source("FPT Shop", 22290000, 22850000, 23590000, "720+", "4.8", "Voucher 500K", 96),
                                source("CellphoneS", 21990000, 22690000, 23390000, "940+", "4.8", "Balo + Office", 95),
                                source("Shopee Mall", 21490000, 22190000, 22990000, "1.870+", "4.7", "Mã giảm 5%", 88),
                                source("TikTok Shop", 21290000, 21990000, 22890000, "1.140+", "4.6", "Deal livestream", 82)
                        )
                ),
                new MarketSample(
                        "dien-thoai-thong-minh",
                        "Điện thoại thông minh",
                        "iPhone 15 Pro Max 256GB",
                        92,
                        "+8,4%",
                        money(29290000),
                        money(30180000),
                        5,
                        126,
                        "Nên cạnh tranh",
                        "Giữ giá thấp hơn trung bình thị trường 2-3%, ưu tiên quà tặng phụ kiện và bảo hành mở rộng.",
                        List.of(
                                source("TopZone", 29990000, 30990000, 31990000, "1.240+", "4.9", "Trả góp 0%", 98),
                                source("FPT Shop", 29790000, 30590000, 31590000, "980+", "4.8", "Voucher 800K", 96),
                                source("CellphoneS", 29490000, 30290000, 31290000, "1.560+", "4.8", "Giảm 1 triệu", 95),
                                source("Shopee Mall", 28890000, 29820000, 30990000, "3.800+", "4.7", "Freeship + voucher", 89),
                                source("TikTok Shop", 28690000, 29650000, 30690000, "2.100+", "4.6", "Flash sale", 84)
                        )
                )
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

    private static MarketResearchResponse.PriceSource source(
            String source,
            long min,
            long avg,
            long max,
            String sales,
            String rating,
            String promo,
            Integer trust
    ) {
        return source(source, money(min), money(avg), money(max), sales, rating, promo, trust);
    }

    private static MarketResearchResponse.PriceSource source(
            String source,
            BigDecimal min,
            BigDecimal avg,
            BigDecimal max,
            String sales,
            String rating,
            String promo,
            Integer trust
    ) {
        return MarketResearchResponse.PriceSource.builder()
                .source(source)
                .min(min)
                .avg(avg)
                .max(max)
                .sales(sales)
                .rating(new BigDecimal(rating))
                .promo(promo)
                .trust(trust)
                .build();
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal roundMoney(BigDecimal value) {
        return value.divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(10000));
    }

    private record MarketSample(
            String id,
            String name,
            String keyword,
            Integer demand,
            String trend,
            BigDecimal recommendedPrice,
            BigDecimal marketAverage,
            Integer competitorCount,
            Integer sampleCount,
            String status,
            String strategy,
            List<MarketResearchResponse.PriceSource> sources
    ) {
    }
}
