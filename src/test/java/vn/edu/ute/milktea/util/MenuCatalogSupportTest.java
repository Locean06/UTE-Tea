package vn.edu.ute.milktea.util;

import org.junit.jupiter.api.Test;
import vn.edu.ute.milktea.entity.catalog.Category;
import vn.edu.ute.milktea.entity.catalog.Product;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MenuCatalogSupportTest {

    private static Product product(long id, String name, String size, int price, Category category) {
        return Product.builder()
                .id(id)
                .name(name)
                .size(size)
                .price(BigDecimal.valueOf(price))
                .category(category)
                .active(true)
                .build();
    }

    @Test
    void sameDrinkAcrossSizesProducesExactlyOneCatalogCard() {
        Category category = Category.builder().id(4L).name("Trà trái cây").build();
        Product medium = product(101L, "Trà Xoài Chanh Dây", "M", 30000, category);
        Product large = product(102L, "Trà Xoài Chanh Dây", "L", 35000, category);

        var cards = MenuCatalogSupport.toDisplayProducts(List.of(large, medium));

        assertEquals(1, cards.size());
        assertEquals(101L, cards.get(0).getId());
        assertEquals("", cards.get(0).getSize());
        assertEquals(new BigDecimal("30000"), cards.get(0).getPrice());
        assertEquals("/images/products/ute-fruit-tea.webp", cards.get(0).getImageUrl());
    }

    @Test
    void catalogDoesNotMergeDifferentDrinksOrCategories() {
        Category fruit = Category.builder().id(4L).name("Trà trái cây").build();
        Category milk = Category.builder().id(5L).name("Trà sữa").build();
        var products = List.of(
                product(1L, "Trà Xoài Chanh Dây", "M", 30000, fruit),
                product(2L, "Trà Xoài Chanh Dây", "L", 35000, fruit),
                product(3L, "Trà Xoài Chanh Dây", "M", 32000, milk),
                product(4L, "Trà Đào", "M", 29000, fruit)
        );
        assertEquals(3, MenuCatalogSupport.toDisplayProducts(products).size());
    }

    @Test
    void cloudinaryImageTakesPriorityOverLocalIllustration() {
        Category fruit = Category.builder().id(4L).name("Trà trái cây").build();
        Product product = product(1L, "Trà Xoài Chanh Dây", "M", 30000, fruit);
        product.setImageUrl("https://res.cloudinary.com/example/image/upload/mango.jpg");
        assertEquals(product.getImageUrl(), MenuCatalogSupport.resolveIllustration(product));
    }
}
