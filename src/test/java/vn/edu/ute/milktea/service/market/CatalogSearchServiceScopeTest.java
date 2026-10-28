package vn.edu.ute.milktea.service.market;

import org.junit.jupiter.api.Test;
import vn.edu.ute.milktea.entity.catalog.Category;
import vn.edu.ute.milktea.entity.catalog.Product;
import vn.edu.ute.milktea.entity.market.Shop;
import vn.edu.ute.milktea.util.MenuCatalogSupport;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogSearchServiceScopeTest {
    private static Product product(long id, String size, Shop shop, Category category) {
        return Product.builder()
                .id(id).name("Trà Đào Cam Sả").size(size)
                .price(new BigDecimal("28000")).shop(shop)
                .category(category).active(true).build();
    }

    @Test
    void unscopedStorefrontDoesNotDisplayEachBranchMenuAsDuplicates() {
        Category globalCategory = Category.builder().id(1L).name("Trà Trái Cây Tươi").active(true).build();
        Shop campus = new Shop();
        campus.setId(10L);
        Shop thuDuc = new Shop();
        thuDuc.setId(20L);
        Category campusCategory = Category.builder().id(2L).name("Trà Trái Cây Tươi").shop(campus).active(true).build();
        Category thuDucCategory = Category.builder().id(3L).name("Trà Trái Cây Tươi").shop(thuDuc).active(true).build();

        var variants = List.of(
                product(1L, "M", null, globalCategory),
                product(2L, "L", null, globalCategory),
                product(3L, "M", campus, campusCategory),
                product(4L, "L", campus, campusCategory),
                product(5L, "M", thuDuc, thuDucCategory),
                product(6L, "L", thuDuc, thuDucCategory)
        );

        var unscoped = variants.stream().filter(p -> CatalogSearchService.belongsToCatalogScope(p, null)).toList();
        assertEquals(2, unscoped.size());
        var cards = MenuCatalogSupport.toDisplayProducts(unscoped);
        assertEquals(1, cards.size());
        assertEquals(1L, cards.getFirst().getId());
        assertEquals("", cards.getFirst().getSize());

        var branchOnly = variants.stream().filter(p -> CatalogSearchService.belongsToCatalogScope(p, campus.getId())).toList();
        assertEquals(2, branchOnly.size());
        assertEquals(1, MenuCatalogSupport.toDisplayProducts(branchOnly).size());
    }
}
