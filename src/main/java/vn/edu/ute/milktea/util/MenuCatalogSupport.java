package vn.edu.ute.milktea.util;

import vn.edu.ute.milktea.entity.catalog.Product;

import java.math.BigDecimal;
import java.util.*;

/**
 * Shared helpers for rendering the public UTE Tea menu as one card per drink.
 * Multiple size variants remain available in the customize modal.
 */
public final class MenuCatalogSupport {

    private static final String FALLBACK = "/images/ute-tea-product-fallback.svg";

    private MenuCatalogSupport() {}

    public static List<Product> toDisplayProducts(Collection<Product> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        Map<String, List<Product>> grouped = new LinkedHashMap<>();
        products.stream()
                .filter(Objects::nonNull)
                .forEach(product -> grouped.computeIfAbsent(displayKey(product), key -> new ArrayList<>()).add(product));

        return grouped.values().stream()
                .map(MenuCatalogSupport::toDisplayProduct)
                .filter(Objects::nonNull)
                .toList();
    }

    public static Product toDisplayProduct(List<Product> variants) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        List<Product> sorted = variants.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((Product p) -> sizeRank(p.getSize()))
                        .thenComparing(Product::getPrice, Comparator.nullsLast(BigDecimal::compareTo))
                        .thenComparing(Product::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        if (sorted.isEmpty()) {
            return null;
        }

        Product preferred = sorted.get(0);
        BigDecimal minPrice = sorted.stream()
                .map(Product::getPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(preferred.getPrice());
        String description = sorted.stream()
                .map(Product::getDescription)
                .filter(MenuCatalogSupport::hasText)
                .findFirst()
                .orElse(preferred.getDescription());
        String resolvedImage = sorted.stream()
                .map(Product::getImageUrl)
                .filter(MenuCatalogSupport::isMeaningfulImage)
                .findFirst()
                .orElse(resolveIllustration(preferred.getName(), preferred.getCategory() != null ? preferred.getCategory().getName() : null, preferred.getImageUrl()));
        long distinctSizes = sorted.stream()
                .map(Product::getSize)
                .filter(MenuCatalogSupport::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .count();
        String displaySize = distinctSizes > 1 ? "" : preferred.getSize();

        return Product.builder()
                .id(preferred.getId())
                .shop(preferred.getShop())
                .deliveryStock(preferred.getDeliveryStock())
                .catalogVersion(preferred.getCatalogVersion())
                .category(preferred.getCategory())
                .name(preferred.getName())
                .searchName(preferred.getSearchName())
                .menuCode(preferred.getMenuCode())
                .size(displaySize)
                .description(description)
                .price(minPrice)
                .imageUrl(resolvedImage)
                .imagePublicId(preferred.getImagePublicId())
                .active(preferred.getActive())
                .build();
    }

    public static String resolveIllustration(Product product) {
        if (product == null) {
            return FALLBACK;
        }
        return resolveIllustration(product.getName(), product.getCategory() != null ? product.getCategory().getName() : null, product.getImageUrl());
    }

    public static String resolveIllustration(String productName, String categoryName, String currentImage) {
        if (isMeaningfulImage(currentImage)) {
            return currentImage;
        }
        String haystack = normalize(productName) + " " + normalize(categoryName);
        if (haystack.contains("topping") || haystack.contains("tran chau") || haystack.contains("pudding") || haystack.contains("thach")) {
            return "/images/products/ute-topping.webp";
        }
        if (haystack.contains("matcha") || haystack.contains("tra xanh")) {
            return "/images/products/ute-matcha.webp";
        }
        if (haystack.contains("ca phe") || haystack.contains("coffee") || haystack.contains("bac xiu") || haystack.contains("espresso") || haystack.contains("latte") || haystack.contains("muoi")) {
            return "/images/products/ute-coffee.webp";
        }
        if (haystack.contains("xoai") || haystack.contains("chanh day") || haystack.contains("dao") || haystack.contains("cam") || haystack.contains("chanh") || haystack.contains("vai") || haystack.contains("dau") || haystack.contains("fruit") || haystack.contains("trai cay") || haystack.contains("nhiet doi") || haystack.contains("passion")) {
            return "/images/products/ute-fruit-tea.webp";
        }
        if (haystack.contains("tra sua") || haystack.contains("milk tea") || haystack.contains("sua") || haystack.contains("oolong") || haystack.contains("hong tra") || haystack.contains("jasmine") || haystack.contains("lai") || haystack.contains("sen") || haystack.contains("bac xiu") || haystack.contains("kem") || haystack.contains("foam")) {
            return "/images/products/ute-milk-tea.webp";
        }
        return FALLBACK;
    }

    public static String displayKey(Product product) {
        if (product == null) {
            return "";
        }
        String shopKey = product.getShop() == null || product.getShop().getId() == null ? "public" : String.valueOf(product.getShop().getId());
        String categoryKey = product.getCategory() == null || product.getCategory().getId() == null ? "0" : String.valueOf(product.getCategory().getId());
        return shopKey + "|" + categoryKey + "|" + normalize(product.getName());
    }

    private static int sizeRank(String size) {
        String normalized = normalize(size);
        return switch (normalized) {
            case "m" -> 0;
            case "l" -> 1;
            case "s" -> 2;
            default -> 3;
        };
    }

    private static boolean isMeaningfulImage(String imageUrl) {
        if (!hasText(imageUrl)) {
            return false;
        }
        String normalized = imageUrl.trim();
        return !normalized.endsWith("/ute-tea-product-fallback.svg") && !normalized.endsWith("ute-tea-product-fallback.svg") && !normalized.equals(FALLBACK) && !normalized.endsWith("/MilkTea.png");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return Product.normalizeSearch(value == null ? "" : value);
    }
}
