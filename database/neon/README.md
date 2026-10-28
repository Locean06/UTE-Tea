# UTE Tea — Neon PostgreSQL demo seed

This seed is for a **new, dedicated Neon demo database**. It must not be enabled
against a database containing real orders or customer information.

## First Render deployment

Use Docker on Render from the `deploy` branch, with `SPRING_PROFILES_ACTIVE=prod`.
Configure the Neon PostgreSQL JDBC URL (`DB_URL`), `DB_USERNAME`, `DB_PASSWORD`,
and JWT/OTP secrets in Render's environment settings, never in Git.

Set these flags for the very first start:

```dotenv
SPRING_PROFILES_ACTIVE=prod
DEMO_SEED_ENABLED=true
JPA_DDL_AUTO=update
ADMIN_BOOTSTRAP_ENABLED=false
SHIPPER_BOOTSTRAP_ENABLED=false
```

On startup, the application creates the database schema (Hibernate update) and
runs the Spring seed runners in this order:

1. `ProductionDemoAccountSeed` (-10): 21 fictitious @utetea.vn users; 3 each of
   ADMIN, CASHIER, KITCHEN, CUSTOMER, SHIPPER, VENDOR and MANAGER.
2. `DataInitializer` (0): local menu basics, default shop settings, QR tables,
   stocks, prep and material recipes, sample batches.
3. `NeonLocalCatalogSeed` (5): local SQL file 02's 11 product categories,
   30 drinks in 2 sizes and 10 topping variants (70 rows, minus existing ones).
4. `NeonLocalInventorySeed` (6): 37 local materials, 44 KHO/BEP stock entries,
   preserving stock records already created by DataInitializer.
5. `MarketplaceDemoData` (10): 3 local-style shop branches, branch menus,
   owner/manager relationships and a delivery carrier.
6. `PromotionDemoData` (12): `GIAMGIA10` voucher.

Only **BCrypt password hashes** are committed. The password CSV delivered
privately to the owner is NOT in the Git repository. Each user has a unique
password. These email addresses are fictitious demo logins, not real mailboxes.

## After a successful first start

Change the Render environment variables to:

```dotenv
DEMO_SEED_ENABLED=false
JPA_DDL_AUTO=validate
ADMIN_BOOTSTRAP_ENABLED=false
SHIPPER_BOOTSTRAP_ENABLED=false
```

Save and redeploy. The seed classes will not run again with the flag off.
Do not leave a demo seed enabled on an internet-exposed production database.

## Verification in Neon SQL Editor

List tables first (Postgres commonly lowercases unquoted entity table names):

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema='public' ORDER BY table_name;

SELECT vaitro, COUNT(*) AS accounts
FROM taikhoan GROUP BY vaitro ORDER BY vaitro;

SELECT COUNT(*) AS categories FROM danhmuc;
SELECT COUNT(*) AS products FROM monuong;
SELECT COUNT(*) AS materials FROM nguyenlieu;
SELECT COUNT(*) AS stock_rows FROM tonnguyenlieu;
SELECT COUNT(*) AS shops FROM shop;
```

Expected user counts: 3 for each of the seven roles. Other record totals
include local base fixtures, so the counts can exceed the source additions.

## Important limitations

This setup **does not convert or run SQL Server .sql files** against Neon.
It imports selected catalog and stock fixtures through JPA and keeps the app's
local demo workflows. SQL Server's complete historical transactions (orders,
invoices, refunds, audit trails) are NOT migrated by this seed. An exact
copy of a local database requires its verified export and a separate migration.

If the database already contains demo accounts with earlier BCrypt hashes,
the CSV for the latest source code will NOT reset their passwords: this seed
is insert-only for existing accounts. Use a truly fresh Neon demo database,
or perform a separately reviewed credential rotation; never drop real data.

Application startup and schema compatibility must be validated through Render
build logs and `/actuator/health`, followed by actual login/order test flows.
