package vn.edu.ute.milktea.config;

import java.math.BigDecimal;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.entity.inventory.Material;
import vn.edu.ute.milktea.entity.inventory.MaterialType;
import vn.edu.ute.milktea.entity.inventory.Stock;
import vn.edu.ute.milktea.entity.inventory.StockId;
import vn.edu.ute.milktea.entity.inventory.StockLocation;
import vn.edu.ute.milktea.repository.inventory.MaterialRepository;
import vn.edu.ute.milktea.repository.inventory.StockRepository;

/** Additional inventory fixtures from local SQL Server seed 02, persisted through JPA. */
@Slf4j
@Component
@Profile("prod")
@Order(6)
@RequiredArgsConstructor
public class NeonLocalInventorySeed implements CommandLineRunner {
    private final MaterialRepository materials;
    private final StockRepository stocks;
    @Value("${DEMO_SEED_ENABLED:false}")
    private boolean enabled;

    private static final String[][] MATERIALS = {
        {"Trà Đen Cổ Thụ", "THO", "kg"},
        {"Trà Ô Long Kim Tuyên", "THO", "kg"},
        {"Trà Lài Thượng Hạng", "THO", "kg"},
        {"Cà Phê Hạt Rang Mộc", "THO", "kg"},
        {"Sữa Đặc Có Đường", "THO", "kg"},
        {"Đường Cát Trắng", "THO", "kg"},
        {"Đường Đen Nữ Hoàng", "THO", "kg"},
        {"Sữa Tươi Thanh Trùng", "THO", "kg"},
        {"Bột Béo Thực Vật", "THO", "kg"},
        {"Bột Kem Cheese Anchor", "THO", "kg"},
        {"Trân Châu Thô Đen", "THO", "kg"},
        {"Hạt Trân Châu Trắng 3Q", "THO", "kg"},
        {"Đào Ngâm Đóng Hộp", "THO", "kg"},
        {"Cam Vàng Tươi", "THO", "kg"},
        {"Sả Cây Tươi", "THO", "kg"},
        {"Dâu Tây Đà Lạt", "THO", "kg"},
        {"Củ Năng Tươi", "THO", "kg"},
        {"Sương Sáo Khô", "THO", "kg"},
        {"Nha Đam Tươi", "THO", "kg"},
        {"Bột Pudding Trứng", "THO", "kg"},
        {"Ly Nhựa PP 500ml", "THO", "cai"},
        {"Ly Nhựa PP 700ml", "THO", "cai"},
        {"Ống Hút Trân Châu", "THO", "cai"},
        {"Màng Ép Miệng Ly", "THO", "cai"},
        {"Nước Cốt Trà Đen", "SOCHE", "ml"},
        {"Nước Cốt Trà Ô Long", "SOCHE", "ml"},
        {"Nước Cốt Trà Lài", "SOCHE", "ml"},
        {"Nước Cốt Cà Phê Pha Phin", "SOCHE", "ml"},
        {"Trân Châu Nấu Chín Dẻo", "SOCHE", "g"},
        {"Trân Châu Nấu Đường Đen", "SOCHE", "g"},
        {"Sốt Kem Cheese Đánh Bông", "SOCHE", "ml"},
        {"Pudding Trứng Nấu Chín", "SOCHE", "g"},
        {"Thạch Phô Mai Tươi Đúc Khuôn", "SOCHE", "g"},
        {"Thạch Củ Năng Rim Đường", "SOCHE", "g"},
        {"Thạch Sương Sáo Nấu Đông", "SOCHE", "g"},
        {"Khúc Bạch Cắt Viên", "SOCHE", "g"},
        {"Nước Đường Pha Sẵn", "SOCHE", "ml"},
    };

    // material name | stock location | quantity | warning threshold (NULL means unset)
    private static final String[][] INITIAL_STOCK = {
        {"Trà Đen Cổ Thụ", "KHO", "60.000", "10.000"},
        {"Trà Ô Long Kim Tuyên", "KHO", "50.000", "8.000"},
        {"Trà Lài Thượng Hạng", "KHO", "45.000", "8.000"},
        {"Cà Phê Hạt Rang Mộc", "KHO", "40.000", "5.000"},
        {"Sữa Đặc Có Đường", "KHO", "120.000", "15.000"},
        {"Đường Cát Trắng", "KHO", "150.000", "20.000"},
        {"Đường Đen Nữ Hoàng", "KHO", "80.000", "10.000"},
        {"Sữa Tươi Thanh Trùng", "KHO", "100.000", "15.000"},
        {"Trân Châu Thô Đen", "KHO", "90.000", "10.000"},
        {"Hạt Trân Châu Trắng 3Q", "KHO", "50.000", "5.000"},
        {"Đào Ngâm Đóng Hộp", "KHO", "40.000", "5.000"},
        {"Bột Kem Cheese Anchor", "KHO", "35.000", "5.000"},
        {"Bột Pudding Trứng", "KHO", "30.000", "5.000"},
        {"Ly Nhựa PP 500ml", "KHO", "2000.000", "300.000"},
        {"Ly Nhựa PP 700ml", "KHO", "2000.000", "300.000"},
        {"Ống Hút Trân Châu", "KHO", "3000.000", "500.000"},
        {"Cam Vàng Tươi", "KHO", "4.000", "10.000"},
        {"Sả Cây Tươi", "KHO", "2.500", "5.000"},
        {"Dâu Tây Đà Lạt", "KHO", "1.800", "4.000"},
        {"Củ Năng Tươi", "KHO", "0.000", "8.000"},
        {"Sương Sáo Khô", "KHO", "0.000", "5.000"},
        {"Bột Béo Thực Vật", "KHO", "25.000", "NULL"},
        {"Nha Đam Tươi", "KHO", "15.000", "NULL"},
        {"Màng Ép Miệng Ly", "KHO", "20.000", "NULL"},
        {"Nước Cốt Trà Đen", "BEP", "7000.000", "1000.000"},
        {"Nước Cốt Trà Ô Long", "BEP", "6000.000", "1000.000"},
        {"Nước Cốt Cà Phê Pha Phin", "BEP", "3000.000", "500.000"},
        {"Trân Châu Nấu Chín Dẻo", "BEP", "3500.000", "500.000"},
        {"Sốt Kem Cheese Đánh Bông", "BEP", "2500.000", "400.000"},
        {"Pudding Trứng Nấu Chín", "BEP", "2000.000", "300.000"},
        {"Sữa Đặc Có Đường", "BEP", "15.000", "2.000"},
        {"Đường Cát Trắng", "BEP", "20.000", "2.000"},
        {"Ly Nhựa PP 500ml", "BEP", "300.000", "50.000"},
        {"Ly Nhựa PP 700ml", "BEP", "300.000", "50.000"},
        {"Ống Hút Trân Châu", "BEP", "400.000", "60.000"},
        {"Nước Cốt Trà Lài", "BEP", "350.000", "1000.000"},
        {"Trân Châu Nấu Đường Đen", "BEP", "180.000", "500.000"},
        {"Cam Vàng Tươi", "BEP", "0.800", "2.000"},
        {"Đào Ngâm Đóng Hộp", "BEP", "0.500", "2.000"},
        {"Thạch Củ Năng Rim Đường", "BEP", "0.000", "500.000"},
        {"Thạch Sương Sáo Nấu Đông", "BEP", "0.000", "400.000"},
        {"Thạch Phô Mai Tươi Đúc Khuôn", "BEP", "1200.000", "NULL"},
        {"Khúc Bạch Cắt Viên", "BEP", "800.000", "NULL"},
        {"Nước Đường Pha Sẵn", "BEP", "2500.000", "NULL"},
    };

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;

        var all = new HashMap<String, Material>();
        for (Material material : materials.findAll()) all.put(material.getName(), material);

        int materialCount = 0, stockCount = 0;
        for (String[] row : MATERIALS) {
            if (all.containsKey(row[0])) continue;
            Material created = materials.save(Material.builder()
                    .name(row[0])
                    .type(MaterialType.valueOf(row[1]))
                    .unit(row[2])
                    .active(true).build());
            all.put(row[0], created);
            materialCount++;
        }

        for (String[] row : INITIAL_STOCK) {
            Material material = all.get(row[0]);
            if (material == null) throw new IllegalStateException("Demo material missing: " + row[0]);
            StockLocation location = StockLocation.valueOf(row[1]);
            StockId id = new StockId(material.getId(), location);
            if (stocks.existsById(id)) continue;
            stocks.save(Stock.builder()
                    .id(id)
                    .material(material)
                    .quantity(new BigDecimal(row[2]))
                    .threshold("NULL".equals(row[3]) ? null : new BigDecimal(row[3]))
                    .version(0L)
                    .build());
            stockCount++;
        }
        log.info("Neon local inventory seed: {} materials, {} stocks created", materialCount, stockCount);
    }
}
