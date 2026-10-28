from pathlib import Path
root = Path(__file__).resolve().parents[1]
js = (root / 'src/main/resources/static/js/order/orders.js').read_text()
sql = (root / 'database/sqlserver/07_SUA_LOI_DANH_SACH_DON_HANG_500.sql').read_text()
repair = (root / 'src/main/java/vn/edu/ute/milktea/config/CashierOrderSchemaRepair.java').read_text()
assert "await MilkTeaApi.get('/api/v1/cashier/orders')" in js
for must in ('ordersLoading', 'nextAutomaticRetryAt', 'err.traceId', 'load(true)', 'fetchedCancels = cancellations'):
    assert must in js, must
for name in ('DonHang', 'MaShop', 'Area', 'Latitude', 'Longitude', 'COL_LENGTH', 'BEGIN TRANSACTION'):
    assert name in sql, name
for forbidden in ('DROP TABLE', 'DELETE FROM', 'TRUNCATE TABLE'):
    assert forbidden not in sql.upper(), forbidden
assert '@Profile("local")' in repair
assert 'Microsoft SQL Server' in repair
print('PASS cashier orders 500 regression contract')
