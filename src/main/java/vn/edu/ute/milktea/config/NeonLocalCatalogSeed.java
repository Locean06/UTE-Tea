package vn.edu.ute.milktea.config;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.entity.catalog.Category;
import vn.edu.ute.milktea.entity.catalog.Product;
import vn.edu.ute.milktea.repository.catalog.CategoryRepository;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;

/**
 * Reuses the catalog from database/sqlserver/02_Du_Lieu_Goc_Menu.sql,
 * but persists through JPA so it works on Neon/PostgreSQL.
 * Executes after local base data and before shop menu propagation.
 */
@Slf4j
@Component
@Profile("prod")
@Order(5)
@RequiredArgsConstructor
public class NeonLocalCatalogSeed implements CommandLineRunner {
    private final CategoryRepository categories;
    private final ProductRepository products;

    @Value("${DEMO_SEED_ENABLED:false}")
    private boolean enabled;

    // category name | item name | size | price VND | description
    private static final String[][] MENU = {
        {"Trà Sữa Truyền Thống", "Trà Sữa Truyền Thống", "M", "25000", "Hương vị trà đen đậm đà hòa quyện cùng sữa béo thơm ngon"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Truyền Thống", "L", "30000", "Trà sữa truyền thống phiên bản ly lớn 700ml sảng khoái"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Thái Xanh", "M", "28000", "Trà xanh Thái Lan thanh mát, thơm ngát thảo mộc"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Thái Xanh", "L", "33000", "Trà sữa Thái xanh giải nhiệt ngày hè ly lớn"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Thái Đỏ", "M", "28000", "Trà đỏ Thái Lan truyền thống thơm nồng vị thảo mộc"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Thái Đỏ", "L", "33000", "Trà Thái đỏ đậm vị béo ngậy ly lớn"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Socola Đậm Đà", "M", "32000", "Vị socola hảo hạng kết hợp trà đen thơm lừng"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Socola Đậm Đà", "L", "37000", "Trà sữa socola đậm đà ly lớn"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Khoai Môn Hoàng Kim", "M", "32000", "Khoai môn bùi béo tự nhiên hòa quyện sữa tươi"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Khoai Môn Hoàng Kim", "L", "37000", "Trà sữa khoai môn thơm bùi ly lớn"},
        {"Trà Sữa Ô Long", "Trà Sữa Ô Long Nướng", "M", "30000", "Hương vị ô long rang cháy đặc trưng thơm lừng khó cưỡng"},
        {"Trà Sữa Ô Long", "Trà Sữa Ô Long Nướng", "L", "35000", "Ô long nướng đậm vị khói thơm ngậy ly lớn"},
        {"Trà Sữa Ô Long", "Trà Sữa Ô Long Thiết Quan Âm", "M", "32000", "Trà thiết quan âm thượng hạng hậu ngọt thanh tao"},
        {"Trà Sữa Ô Long", "Trà Sữa Ô Long Thiết Quan Âm", "L", "38000", "Thiết quan âm sữa thơm quý phái ly lớn"},
        {"Trà Sữa Lài", "Trà Sữa Lài Thơm", "M", "28000", "Hoa lài tự nhiên thơm ngát hòa cùng sữa dịu ngọt"},
        {"Trà Sữa Lài", "Trà Sữa Lài Thơm", "L", "33000", "Trà sữa lài ngọt thanh thư giãn ly lớn"},
        {"Trà Sữa Lài", "Trà Sữa Lài Kem Cheese", "M", "35000", "Trà sữa lài thanh tao kèm váng phô mai béo ngậy"},
        {"Trà Sữa Lài", "Trà Sữa Lài Kem Cheese", "L", "42000", "Trà sữa lài phủ kem cheese ngậy béo ly lớn"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Matcha Nhật Bản", "M", "35000", "Bột matcha Uji thượng phẩm thơm lừng vị trà xanh mộc"},
        {"Trà Sữa Truyền Thống", "Trà Sữa Matcha Nhật Bản", "L", "42000", "Matcha sữa đậm đà chuẩn vị Nhật ly lớn"},
        {"Trà Trái Cây Tươi", "Trà Đào Cam Sả", "M", "28000", "Trà đen ủ lạnh cùng cam vàng mọng nước và sả tươi đập dập"},
        {"Trà Trái Cây Tươi", "Trà Đào Cam Sả", "L", "33000", "Trà đào cam sả thanh nhiệt tươi mới ly lớn"},
        {"Trà Trái Cây Tươi", "Trà Vải Hoa Hồng", "M", "30000", "Vải thiều ngọt lịm phảng phất hương hoa hồng lãng mạn"},
        {"Trà Trái Cây Tươi", "Trà Vải Hoa Hồng", "L", "35000", "Trà vải hoa hồng quý phái ngọt ngào ly lớn"},
        {"Trà Trái Cây Tươi", "Trà Dâu Tây Nhiệt Đới", "M", "32000", "Dâu tây Đà Lạt tươi giã tay thơm lừng vị quả mọng"},
        {"Trà Trái Cây Tươi", "Trà Dâu Tây Nhiệt Đới", "L", "38000", "Trà dâu tây tươi mát rượi ly lớn"},
        {"Trà Trái Cây Tươi", "Trà Xoài Chanh Dây", "M", "30000", "Vị chua ngọt bùng nổ giữa xoài cát chín và chanh leo"},
        {"Trà Trái Cây Tươi", "Trà Xoài Chanh Dây", "L", "35000", "Trà xoài chanh leo bừng tỉnh năng lượng ly lớn"},
        {"Trà Trái Cây Tươi", "Trà Ổi Hồng Hạt Chia", "M", "32000", "Nước cốt ổi hồng thơm ngát giàu vitamin kèm hạt chia"},
        {"Trà Trái Cây Tươi", "Trà Ổi Hồng Hạt Chia", "L", "37000", "Trà ổi hồng bổ dưỡng da sáng dáng thon ly lớn"},
        {"Trà Trái Cây Tươi", "Trà Táo Xanh Bạc Hà", "M", "30000", "Táo xanh giòn rụm kết hợp bạc hà mát lạnh the mát"},
        {"Trà Trái Cây Tươi", "Trà Táo Xanh Bạc Hà", "L", "35000", "Trà táo xanh the mát giải nhiệt ly lớn"},
        {"Trà Trái Cây Tươi", "Trà Bưởi Mật Ong", "M", "32000", "Tép bưởi hồng mọng nước cùng mật ong hoa nhãn dịu ngọt"},
        {"Trà Trái Cây Tươi", "Trà Bưởi Mật Ong", "L", "38000", "Trà bưởi hồng mật ong thanh lọc cơ thể ly lớn"},
        {"Trà Trái Cây Tươi", "Trà Mãng Cầu Xiêm", "M", "35000", "Mãng cầu xiêm chua ngọt tự nhiên giã tay đậm đà"},
        {"Trà Trái Cây Tươi", "Trà Mãng Cầu Xiêm", "L", "42000", "Trà mãng cầu xiêm hot trend tươi mát ly lớn"},
        {"Cà Phê Truyền Thống", "Cà Phê Đen Đá", "M", "20000", "Cà phê robusta rang mộc pha phin truyền thống đậm đà"},
        {"Cà Phê Truyền Thống", "Cà Phê Đen Đá", "L", "25000", "Cà phê đen đá đậm vị tỉnh táo ngày dài"},
        {"Cà Phê Truyền Thống", "Cà Phê Sữa Đá", "M", "25000", "Cà phê pha phin truyền thống hòa cùng sữa đặc sánh ngọt"},
        {"Cà Phê Truyền Thống", "Cà Phê Sữa Đá", "L", "30000", "Cà phê sữa đá chuẩn gu Sài Gòn ly lớn"},
        {"Cà Phê Sáng Tạo", "Cà Phê Muối Xứ Huế", "M", "28000", "Lớp kem sữa muối mằn mặn hòa cùng cà phê thơm nồng"},
        {"Cà Phê Sáng Tạo", "Cà Phê Muối Xứ Huế", "L", "34000", "Cà phê muối béo mặn thơm ngậy ly lớn"},
        {"Cà Phê Sáng Tạo", "Bạc Xỉu Ba Tầng", "M", "28000", "Tầng sữa ngọt dịu, sữa tươi thơm mát và cà phê đậm vị"},
        {"Cà Phê Sáng Tạo", "Bạc Xỉu Ba Tầng", "L", "33000", "Bạc xỉu ba tầng ngọt ngào êm ái ly lớn"},
        {"Sữa Tươi Trân Châu", "Sữa Tươi Trân Châu Đường Đen", "M", "35000", "Sữa tươi thanh trùng Đà Lạt cùng trân châu nấu đường đen dẻo quánh"},
        {"Sữa Tươi Trân Châu", "Sữa Tươi Trân Châu Đường Đen", "L", "42000", "Sữa tươi đường đen thơm ngon nức tiếng ly lớn"},
        {"Sữa Tươi Trân Châu", "Sữa Tươi Sương Sáo Thảo Mộc", "M", "32000", "Sữa tươi béo nhẹ kết hợp sương sáo Đài Loan mát lành"},
        {"Sữa Tươi Trân Châu", "Sữa Tươi Sương Sáo Thảo Mộc", "L", "38000", "Sữa tươi sương sáo thanh lọc cơ thể ly lớn"},
        {"Macchiato & Váng Sữa", "Trà Ô Long Váng Sữa Macchiato", "M", "35000", "Cốt trà ô long thượng hạng phủ lớp macchiato kem sữa béo ngậy"},
        {"Macchiato & Váng Sữa", "Trà Ô Long Váng Sữa Macchiato", "L", "42000", "Ô long macchiato béo ngọt cân bằng ly lớn"},
        {"Macchiato & Váng Sữa", "Trà Đen Macchiato Muối Biển", "M", "32000", "Trà đen đậm đà tương phản với kem muối biển béo ngậy"},
        {"Macchiato & Váng Sữa", "Trà Đen Macchiato Muối Biển", "L", "38000", "Trà đen macchiato muối biển thơm ngon ly lớn"},
        {"Đá Xay & Sinh Tố", "Matcha Đá Xay Kem Tuyết", "M", "40000", "Matcha xay nhuyễn cùng đá và sữa, phủ bông kem whipping tươi"},
        {"Đá Xay & Sinh Tố", "Matcha Đá Xay Kem Tuyết", "L", "48000", "Matcha đá xay kem tuyết mát lạnh sảng khoái ly lớn"},
        {"Đá Xay & Sinh Tố", "Cà Phê Đá Xay Caramel", "M", "38000", "Espresso xay đá cùng xốt caramel ngọt bùi phủ kem whipping"},
        {"Đá Xay & Sinh Tố", "Cà Phê Đá Xay Caramel", "L", "45000", "Cà phê caramel đá xay béo ngọt nồng nàn ly lớn"},
        {"Đá Xay & Sinh Tố", "Sinh Tố Bơ Dừa Thơm Béo", "M", "42000", "Bơ sáp Đắk Lắk dẻo quánh xay cùng nước cốt dừa béo bùi"},
        {"Đá Xay & Sinh Tố", "Sinh Tố Bơ Dừa Thơm Béo", "L", "50000", "Sinh tố bơ dừa thơm ngậy bổ dưỡng ly lớn"},
        {"Trà Thảo Mộc & Thanh Nhiệt", "Trà Hoa Cúc Mật Ong Hạt Sen", "M", "32000", "Hoa cúc sấy khô ủ ấm mật ong rừng và hạt sen bùi ngậy"},
        {"Trà Thảo Mộc & Thanh Nhiệt", "Trà Hoa Cúc Mật Ong Hạt Sen", "L", "38000", "Trà hoa cúc hạt sen an thần thanh nhiệt ly lớn"},
        {"Topping Cao Cấp", "Trân Châu Đen Hoàng Kim", "M", "6000", "Trân châu dẻo dai hoàng kim nấu đường đen ngọt ngào"},
        {"Topping Cao Cấp", "Trân Châu Trắng 3Q", "M", "7000", "Trân châu trắng giòn sần sật nhai vui miệng"},
        {"Topping Cao Cấp", "Thạch Đào Giòn Ngọt", "M", "8000", "Thạch đào dai giòn thơm nức mùi quả chín"},
        {"Topping Cao Cấp", "Kem Cheese Phô Mai Macchiato", "M", "10000", "Lớp kem cheese mằn mặn béo ngậy tan chảy trên đầu lưỡi"},
        {"Topping Cao Cấp", "Pudding Trứng Vàng", "M", "8000", "Pudding mềm mịn núng nính thơm ngậy vị trứng gà"},
        {"Topping Cao Cấp", "Thạch Phô Mai Tươi", "M", "10000", "Viên phô mai bọc thạch trong suốt béo ngậy"},
        {"Topping Cao Cấp", "Sương Sáo Thảo Mộc Đài Loan", "M", "6000", "Sương sáo thanh mát thảo mộc giải nhiệt tức thì"},
        {"Topping Cao Cấp", "Thạch Củ Năng Giòn", "M", "8000", "Củ năng bọc bột lọc giòn ngọt sảng khoái"},
        {"Topping Cao Cấp", "Nha Đam Tươi Đường Phèn", "M", "7000", "Nha đam giòn mát nấu đường phèn thanh khiết"},
        {"Topping Cao Cấp", "Khúc Bạch Hạnh Nhân", "M", "10000", "Khúc bạch phô mai thơm lừng rắc hạnh nhân giòn"},
    };

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;

        var categoriesByName = new HashMap<String, Category>();
        for (Category category : categories.findAll()) {
            if (category.getShop() == null) categoriesByName.put(category.getName(), category);
        }

        var uniqueMenu = new HashSet<String>();
        // Include discontinued items too: a later restart must never re-create deleted demo products.
        for (Product p : products.findAll()) {
            if (p.getShop() == null) uniqueMenu.add(p.getName().toLowerCase(java.util.Locale.ROOT) + "|" + p.getSize().toLowerCase(java.util.Locale.ROOT));
        }

        int created = 0;
        for (String[] row : MENU) {
            String categoryName = row[0];
            Category category = categoriesByName.get(categoryName);
            if (category == null) {
                category = categories.save(Category.builder()
                        .name(categoryName)
                        .active(true)
                        .allowTopping(!categoryName.equals("Topping Cao Cấp"))
                        .build());
                categoriesByName.put(categoryName, category);
            }

            String key = row[1].toLowerCase(java.util.Locale.ROOT) + "|" + row[2].toLowerCase(java.util.Locale.ROOT);
            if (!uniqueMenu.add(key)) continue;
            products.save(Product.builder()
                    .category(category)
                    .name(row[1])
                    .size(row[2])
                    .description(row[4])
                    .price(new BigDecimal(row[3]))
                    .imageUrl("/images/ute-tea-product-fallback.svg")
                    .active(true)
                    .build());
            created++;
        }
        log.info("Neon local catalog seed: {} added from {} local SQL menu variants", created, MENU.length);
    }
}
