package vn.edu.ute.milktea.service.market;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.common.ErrorCode;
import vn.edu.ute.milktea.dto.MarketDto.CatalogPage;
import vn.edu.ute.milktea.dto.MarketDto.MenuOptions;
import vn.edu.ute.milktea.entity.catalog.Product;
import vn.edu.ute.milktea.util.MenuCatalogSupport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CatalogSearchService {
    private final EntityManager em;
    private final MarketService market;
    private final vn.edu.ute.milktea.repository.catalog.ProductRepository products;

    @Transactional(readOnly = true)
    public MenuOptions options(Long id) {
        var source = products.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.PRODUCT_UNAVAILABLE, "Món không tồn tại"));

        Map<String, Product> unique = new LinkedHashMap<>();
        products.findAllByActiveTrue().stream()
                .sorted(Comparator.comparing(Product::getId))
                .forEach(p -> unique.putIfAbsent(p.menuVariantIdentity(), p));

        var selected = unique.get(source.menuVariantIdentity());
        if (selected == null) {
            throw BusinessException.badRequest(ErrorCode.PRODUCT_UNAVAILABLE, "Món đã ngừng bán");
        }

        var variants = unique.values().stream()
                .filter(p -> Product.normalizeSearch(p.getName()).equals(Product.normalizeSearch(selected.getName())))
                .sorted(Comparator.comparing(Product::getPrice))
                .map(market::productView)
                .toList();
        var toppings = unique.values().stream()
                .filter(p -> Product.normalizeSearch(p.getCategory().getName()).contains("topping"))
                .map(market::productView)
                .toList();
        boolean allowTopping = products.findAllByActiveTrue().stream().anyMatch(
                p -> p.menuVariantIdentity().equals(selected.menuVariantIdentity())
                        && Boolean.TRUE.equals(p.getCategory().getAllowTopping())
        );
        return new MenuOptions(
                market.productView(selected),
                variants,
                toppings,
                allowTopping && !Product.normalizeSearch(selected.getCategory().getName()).contains("topping")
        );
    }

    @Transactional(readOnly = true)
    public CatalogPage search(String term, Long category, Long shop, BigDecimal min, BigDecimal max, String sort, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100
                || (min != null && min.signum() < 0)
                || (max != null && max.signum() < 0)
                || (min != null && max != null && min.compareTo(max) > 0)
                || term != null && term.length() > 120) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Bộ lọc hoặc phân trang không hợp lệ");
        }

        String normalizedTerm = Product.normalizeSearch(term == null ? "" : term);
        List<Product> matched = products.findAllByActiveTrue().stream()
                .filter(p -> p.getCategory() != null && Boolean.TRUE.equals(p.getCategory().getActive()))
                .filter(p -> p.getShop() == null || p.getShop().isActive())
                .filter(p -> !hasText(normalizedTerm)
                        || Product.normalizeSearch(p.getName()).contains(normalizedTerm)
                        || Product.normalizeSearch(p.getCategory().getName()).contains(normalizedTerm))
                .filter(p -> category == null || Objects.equals(p.getCategory().getId(), category))
                .filter(p -> shop == null || (p.getShop() != null && Objects.equals(p.getShop().getId(), shop)))
                .toList();

        List<Product> displayProducts = new ArrayList<>(MenuCatalogSupport.toDisplayProducts(matched));
        if (min != null) {
            displayProducts.removeIf(p -> p.getPrice() == null || p.getPrice().compareTo(min) < 0);
        }
        if (max != null) {
            displayProducts.removeIf(p -> p.getPrice() == null || p.getPrice().compareTo(max) > 0);
        }

        displayProducts.sort(comparator(sort));

        long total = displayProducts.size();
        int fromIndex = Math.min(page * size, displayProducts.size());
        int toIndex = Math.min(fromIndex + size, displayProducts.size());
        var items = displayProducts.subList(fromIndex, toIndex).stream().map(market::productView).toList();
        return new CatalogPage(items, total, page, size);
    }

    private Comparator<Product> comparator(String sort) {
        String normalizedSort = sort == null ? "newest" : sort.toLowerCase(Locale.ROOT);
        Comparator<Product> newestTieBreak = Comparator.comparing(Product::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (normalizedSort) {
            case "newest" -> newestTieBreak;
            case "price_asc" -> Comparator.comparing(Product::getPrice, Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(newestTieBreak);
            case "price_desc" -> Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(newestTieBreak);
            case "favorites" -> Comparator.comparingLong(this::favoriteScore).reversed()
                    .thenComparing(newestTieBreak);
            case "rating" -> Comparator.comparingDouble(this::ratingScore).reversed()
                    .thenComparing(newestTieBreak);
            case "bestselling" -> Comparator.comparingLong(this::bestSellingScore).reversed()
                    .thenComparing(newestTieBreak);
            default -> throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Tiêu chí sắp xếp không hợp lệ");
        };
    }

    private long favoriteScore(Product product) {
        return em.createQuery(
                        "select count(i) from ProductInterest i where i.product.id=:id and i.favorite=true",
                        Long.class)
                .setParameter("id", product.getId())
                .getSingleResult();
    }

    private double ratingScore(Product product) {
        return em.createQuery(
                        "select coalesce(avg(r.stars),0) from ProductReview r where r.product.id=:id",
                        Double.class)
                .setParameter("id", product.getId())
                .getSingleResult();
    }

    private long bestSellingScore(Product product) {
        Long posSold = em.createQuery(
                        "select coalesce(sum(i.quantity),0) from OrderItem i where i.product.id=:id and i.order.status=vn.edu.ute.milktea.entity.order.OrderStatus.HOAN_THANH",
                        Long.class)
                .setParameter("id", product.getId())
                .getSingleResult();
        Long marketSold = em.createQuery(
                        "select coalesce(sum(l.quantity),0) from DeliveryLine l where l.product.id=:id and l.order.status=vn.edu.ute.milktea.entity.market.DeliveryOrder.Status.DELIVERED",
                        Long.class)
                .setParameter("id", product.getId())
                .getSingleResult();
        return (posSold == null ? 0 : posSold) + (marketSold == null ? 0 : marketSold);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
