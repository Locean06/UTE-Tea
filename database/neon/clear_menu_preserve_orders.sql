-- UTE Tea — Reset manually maintained demo menu without destroying order history.
-- Run ONCE in Neon SQL Editor, database utetea, after verifying a backup.
-- This script intentionally:
-- * Disables every current category and product across global + all branches.
-- * Deletes obsolete product recipe rows for products that have NO history references.
-- * Physically deletes unreferenced products and their empty categories.
-- * Keeps referenced products/categories inactive, so existing historical orders,
--   interest lists, reviews and carts continue to satisfy FK constraints.
-- * Does not delete any orders, accounts, branch records or user interest records.
-- IMPORTANT: DEMO_SEED_ENABLED must be false on Render or demo data can reappear.
-- IMPORTANT: This will remove YOUR CURRENT MENU; only run after backup.

BEGIN;

CREATE TEMP TABLE ute_keep_product_ids
ON COMMIT DROP AS
SELECT DISTINCT id
FROM (
    SELECT mamon AS id FROM chitietdonhang
    UNION ALL SELECT mamontopping FROM chitietdonhangtopping
    UNION ALL SELECT mamon FROM chitietgiophien
    UNION ALL SELECT productid FROM deliveryline
    UNION ALL SELECT productid FROM productinterest
    UNION ALL SELECT productid FROM productreview
) refs
WHERE id IS NOT NULL;

CREATE UNIQUE INDEX ON ute_keep_product_ids(id);

-- Disable everything first, including items which remain for historic references.
UPDATE monuong SET hoatdong = false, catalogversion = catalogversion + 1
WHERE hoatdong = true;
UPDATE danhmuc SET hoatdong = false WHERE hoatdong = true;

-- Remove recipe links ONLY for products which will be removed.
DELETE FROM congthucmon r
WHERE NOT EXISTS (
    SELECT 1 FROM ute_keep_product_ids k WHERE k.id = r.mamon
);

-- Delete all products with no historical/cart/favorite/review references.
DELETE FROM monuong m
WHERE NOT EXISTS (
    SELECT 1 FROM ute_keep_product_ids k WHERE k.id = m.mamon
);

-- Delete now-empty categories; remaining ones stay inactive.
DELETE FROM danhmuc c
WHERE NOT EXISTS (
    SELECT 1 FROM monuong m WHERE m.madm = c.madm
);

COMMIT;

-- Expected: 0 active menu products and 0 active categories.
-- Some inactive rows remain intentionally as historical FK anchors.
SELECT
  (SELECT count(*) FROM monuong WHERE hoatdong = true) AS active_products,
  (SELECT count(*) FROM danhmuc WHERE hoatdong = true) AS active_categories,
  (SELECT count(*) FROM monuong) AS historical_products_retained,
  (SELECT count(*) FROM danhmuc) AS historical_categories_retained;
