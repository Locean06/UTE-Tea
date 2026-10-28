"""Offline source-level contract. Runtime Spring and SQL must be tested separately."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
def read(path): return (root/path).read_text(encoding='utf-8')
backend=read('src/main/java/vn/edu/ute/milktea/service/market/MarketService.java')
controller=read('src/main/java/vn/edu/ute/milktea/controller/KitchenDeliveryController.java')
model=read('src/main/java/vn/edu/ute/milktea/entity/market/DeliveryOrder.java')
html=read('src/main/resources/templates/kitchen/orders.html')
js=read('src/main/resources/static/js/kitchen/delivery-orders.js')
shipper=read('src/main/resources/static/js/delivery/dashboard.js')
security=read('src/main/java/vn/edu/ute/milktea/security/SecurityConfig.java')
sql=read('database/sqlserver/15_kitchen_delivery_flow.sql')
assert 'Preparation { PENDING, PREPARING, READY }' in model
assert '"/api/v1/kitchen/**").hasAnyRole("ADMIN", "KITCHEN")' in security
assert '/api/v1/kitchen/delivery-orders' in js
assert '@PutMapping("/{id}/preparation")' in controller
assert 'version(order,expectedVersion)' in backend and 'orders.lockId(orderId)' in backend
assert 'o.getPreparationStatus()==DeliveryOrder.Preparation.READY' in backend
assert "order.preparationStatus === 'READY'" in shipper
assert 'DELIVERY_ORDER_CHANGED' in js and 'notifyKitchenDeliveryChanged' in backend
assert 'id="kitchenDeliveryOrders"' in html
assert 'COL_LENGTH' in sql and 'DROP TABLE' not in sql.upper()
print('PASS kitchen-to-shipper visibility / authorization / preparation / SQL migration source contract')
