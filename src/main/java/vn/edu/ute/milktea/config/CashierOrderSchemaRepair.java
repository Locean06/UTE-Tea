package vn.edu.ute.milktea.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-only, additive compatibility for SQL Server databases created before
 * the branch-aware order list. Full database setup still requires migrations 01-06.
 * Production is deliberately excluded; use the SQL patch with DBA approval.
 */
@Slf4j
@Component
@Profile("local")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CashierOrderSchemaRepair implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            try (var connection = jdbc.getDataSource().getConnection()) {
                if (!connection.getMetaData().getDatabaseProductName().contains("Microsoft SQL Server")) return;
            }
            if (!tableExists("DonHang") || !tableExists("Shop")) {
                log.warn("Order schema not ready: DonHang/Shop missing. Apply database/sqlserver/03_Nang_Cap_Tinh_Nang.sql.");
                return;
            }
            addMissingColumn("DonHang", "MaShop", "BIGINT NULL");
            addMissingColumn("Shop", "Area", "NVARCHAR(120) NULL");
            addMissingColumn("Shop", "Latitude", "FLOAT NULL");
            addMissingColumn("Shop", "Longitude", "FLOAT NULL");
            if (tableExists("HoaDon")) {
                addMissingColumn("HoaDon", "LoaiGiamChot",
                        "VARCHAR(16) NOT NULL CONSTRAINT DF_HoaDon_LoaiGiamChot_Patch DEFAULT 'PERCENT' WITH VALUES");
                addMissingColumn("HoaDon", "GiaTriGiamChot",
                        "DECIMAL(18,2) NOT NULL CONSTRAINT DF_HoaDon_GiaTriGiamChot_Patch DEFAULT 0 WITH VALUES");
            }
            if (tableExists("TaiKhoan")) {
                addMissingColumn("TaiKhoan", "EmailXacThuc",
                        "BIT NOT NULL CONSTRAINT DF_TaiKhoan_EmailXacThuc_Patch DEFAULT 1 WITH VALUES");
                addMissingColumn("TaiKhoan", "TokenVersion",
                        "BIGINT NOT NULL CONSTRAINT DF_TaiKhoan_TokenVersion_Patch DEFAULT 1 WITH VALUES");
                addMissingColumn("TaiKhoan", "AvatarUrl", "NVARCHAR(1000) NULL");
                addMissingColumn("TaiKhoan", "AvatarPublicId", "NVARCHAR(255) NULL");
            }
            addMissingColumn("DonHang", "Version",
                    "BIGINT NOT NULL CONSTRAINT DF_DonHang_Version_Patch DEFAULT 0 WITH VALUES");
        } catch (Exception ex) {
            // Do not prevent the app from starting: report the precise SQL exception.
            // The operator can run 07_SUA_LOI_DANH_SACH_DON_HANG_500.sql.
            log.error("Could not verify/repair cashier order schema. Run database/sqlserver/07_SUA_LOI_DANH_SACH_DON_HANG_500.sql on DB_URL", ex);
        }
    }

    private boolean tableExists(String table) {
        return jdbc.queryForObject("SELECT OBJECT_ID(N'dbo." + table + "', N'U')", Long.class) != null;
    }

    private void addMissingColumn(String table, String column, String type) {
        Long length = jdbc.queryForObject(
                "SELECT COL_LENGTH(N'dbo." + table + "', N'" + column + "')", Long.class);
        if (length == null) {
            jdbc.execute("ALTER TABLE dbo." + table + " ADD " + column + " " + type);
            log.info("Restored missing POS order schema column: {}.{}", table, column);
        }
    }
}
