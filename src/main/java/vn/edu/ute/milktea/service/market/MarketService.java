package vn.edu.ute.milktea.service.market;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.*;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.entity.catalog.*;
import vn.edu.ute.milktea.entity.market.*;
import vn.edu.ute.milktea.repository.account.*;
import vn.edu.ute.milktea.repository.catalog.*;
import vn.edu.ute.milktea.repository.market.*;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.account.CustomerDataService;
import vn.edu.ute.milktea.util.MenuCatalogSupport;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service @RequiredArgsConstructor @Transactional
public class MarketService {
 private final ShopRepository shops;
 private final AccountRepository accounts;
 private final ProductRepository products;
 private final CategoryRepository categories;
 private final CarrierRepository carriers;
 private final VoucherRepository vouchers;
 private final DeliveryOrderRepository orders;
 private final DeliveryAddressRepository addresses;
 private final EntityManager em;
 private final VnpayService gateway;
 private final ShopCustomerAccessRepository customers;
 private final vn.edu.ute.milktea.service.media.ProductImageService images;
 private final BranchSelectionService branches;
 private final CustomerDataService customerData;
 private final vn.edu.ute.milktea.service.realtime.RealtimeEventPublisher realtime;

 private Long id(CurrentActor a){return CustomerDataService.accountId(a);}
 private boolean admin(CurrentActor a){return a!=null&&a.hasRole(Role.ADMIN);}
 private boolean cashier(CurrentActor a){return a!=null&&a.hasRole(Role.CASHIER);}
 private void requireDeliveryStaff(CurrentActor a){if(!(admin(a)||cashier(a)))throw denied();}
 private BusinessException invalid(String m){return BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,m);}
 private BusinessException denied(){return BusinessException.forbidden(ErrorCode.ACCESS_DENIED,"Không có quyền với cửa hàng hoặc dữ liệu này");}
 private Account account(CurrentActor a){return accounts.findByIdWithLock(id(a)).orElseThrow(this::denied);}
 public Shop scope(CurrentActor a,Long shopId){
  Long accountId=id(a);Shop s=shops.findById(shopId).orElseThrow(this::denied);
  if(!admin(a)&&!(a.hasRole(Role.VENDOR)&&s.getOwner().getId().equals(accountId))&&!(a.hasRole(Role.MANAGER)&&s.getManagers().stream().anyMatch(m->m.getId().equals(accountId))))throw denied();
  return s;
 }
 public ShopView createShop(CurrentActor a,ShopInput input){
  var owner=account(a);
  if(!Set.of(Role.CUSTOMER,Role.VENDOR,Role.ADMIN).contains(owner.getRole()))throw denied();
  Shop s=new Shop();s.setOwner(owner);s.setName(input.name().trim());s.setAddress(input.address().trim());location(s,input);
  if(owner.getRole()==Role.CUSTOMER)owner.setRole(Role.VENDOR);
  return shopView(shops.save(s));
 }
 public ShopView editShop(CurrentActor a,Long shopId,ShopInput input){var s=scope(a,shopId);s.setName(input.name().trim());s.setAddress(input.address().trim());location(s,input);return shopView(s);}
 private void location(Shop s,ShopInput input){s.setArea(input.area()==null?null:input.area().trim());s.setLatitude(input.latitude());s.setLongitude(input.longitude());}
 public ShopView configureShop(CurrentActor a,Long shopId,ShopConfig input){
  var s=scope(a,shopId);
  if(admin(a)){s.setActive(input.active());s.setManagerCommissionEnabled(input.managerCommissionEnabled());}
  else if(!a.hasRole(Role.MANAGER)||!s.isManagerCommissionEnabled()||input.active()!=s.isActive())throw denied();
  s.setCommissionPercent(input.commissionPercent());return shopView(s);
 }
 public void assignManager(CurrentActor a,Long shopId,Long managerId,boolean enabled){
  if(!admin(a))throw denied();var s=scope(a,shopId);var m=accounts.findById(managerId).orElseThrow(this::denied);
  if(m.getRole()!=Role.MANAGER)throw invalid("Tài khoản phải có vai trò MANAGER");
  if(enabled)s.getManagers().add(m);else s.getManagers().removeIf(x->x.getId().equals(managerId));
 }
 @Transactional(readOnly=true) public List<ShopView> shops(CurrentActor a,boolean publicOnly){
  var all=publicOnly?shops.findAll():admin(a)?shops.findAll():shops.findAccessible(id(a));
  return all.stream().filter(s->!publicOnly||s.isActive()).map(this::shopView).toList();
 }
 private ShopView shopView(Shop s){return new ShopView(s.getId(),s.getName(),s.getAddress(),s.isActive(),s.getCommissionPercent(),s.getOwner().getId(),s.isManagerCommissionEnabled(),s.getArea(),s.getLatitude(),s.getLongitude());}
 public CategoryView saveCategory(CurrentActor a,Long shopId,Long categoryId,CategoryInput input){
  var shop=scope(a,shopId);Category c=categoryId==null?new Category():categories.findById(categoryId).orElseThrow(this::denied);
  if(categoryId!=null&&(c.getShop()==null||!shopId.equals(c.getShop().getId())))throw denied();
  c.setShop(shop);c.setName(input.name().trim());c.setActive(input.active());if(input.allowTopping()!=null)c.setAllowTopping(input.allowTopping());categories.save(c);
  return new CategoryView(c.getId(),c.getName(),shopId,c.getActive(),Boolean.TRUE.equals(c.getAllowTopping()));
 }
 @Transactional(readOnly=true) public List<CategoryView> shopCategories(CurrentActor a,Long shopId){
  scope(a,shopId);return categories.findAll().stream().filter(c->c.getShop()!=null&&shopId.equals(c.getShop().getId())).map(c->new CategoryView(c.getId(),c.getName(),shopId,c.getActive(),Boolean.TRUE.equals(c.getAllowTopping()))).toList();
 }
 public ProductView saveProduct(CurrentActor a,Long shopId,Long productId,ProductInput input){
  var shop=scope(a,shopId);Product p=productId==null?new Product():products.findByIdWithLock(productId).orElseThrow(this::denied);
  if(productId!=null&&(p.getShop()==null||!shopId.equals(p.getShop().getId())))throw denied();
  if(productId!=null&&!Objects.equals(p.getCatalogVersion(),input.catalogVersion()))throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,"Món hoặc tồn bán đã thay đổi; tải lại trước khi lưu");
  var cat=categories.findById(input.categoryId()).orElseThrow(()->invalid("Danh mục không tồn tại"));
  if(cat.getShop()==null||!shopId.equals(cat.getShop().getId()))throw denied();
  p.setShop(shop);p.setCategory(cat);p.setName(input.name().trim());p.setSize(input.size().trim());p.setDescription(input.description());p.setPrice(input.price());p.setDeliveryStock(input.stock());p.setActive(input.active());
  if(input.menuCode()!=null&&!input.menuCode().isBlank()) {
   if(!admin(a)&&!Product.normalizeSearch(input.menuCode()).equals(p.menuIdentity())&&!Product.normalizeSearch(input.menuCode()).equals(Product.normalizeSearch(p.getName())+"|"+Product.normalizeSearch(p.getSize())))throw denied();
   p.setMenuCode(Product.normalizeSearch(input.menuCode()));
  }
  return productView(products.saveAndFlush(p));
 }
 public vn.edu.ute.milktea.dto.AdminDto.ProductImageView productImage(CurrentActor a,Long shopId,Long productId,org.springframework.web.multipart.MultipartFile file){scope(a,shopId);var p=products.findByIdWithLock(productId).orElseThrow(this::denied);if(p.getShop()==null||!shopId.equals(p.getShop().getId()))throw denied();return images.uploadProductImage(productId,file);}
 @Transactional(readOnly=true) public List<ProductView> shopProducts(CurrentActor a,Long shopId){scope(a,shopId);return products.findByShopId(shopId).stream().map(this::productView).toList();}
 public CarrierView saveCarrier(CurrentActor a,Long shopId,Long carrierId,CarrierInput input){
  Shop shop=shopId==null?null:scope(a,shopId);if(shopId==null&&!admin(a))throw denied();
  Carrier c=carrierId==null?new Carrier():carriers.findById(carrierId).orElseThrow(this::denied);
  if(carrierId!=null&&!Objects.equals(shopId,c.getShop()==null?null:c.getShop().getId()))throw denied();
  c.setShop(shop);c.setName(input.name().trim());c.setFee(input.fee());c.setActive(input.active());carriers.save(c);return carrierView(c);
 }
 @Transactional(readOnly=true) public List<CarrierView> carriers(){return carriers.findAll().stream().filter(Carrier::isActive).map(this::carrierView).toList();}
 @Transactional(readOnly=true) public List<CarrierView> managedCarriers(CurrentActor a,Long shopId){if(shopId==null){if(!admin(a))throw denied();}else scope(a,shopId);return carriers.findAll().stream().filter(c->Objects.equals(shopId,c.getShop()==null?null:c.getShop().getId())).map(this::carrierView).toList();}
 private CarrierView carrierView(Carrier c){return new CarrierView(c.getId(),c.getName(),c.getFee(),c.getShop()==null?null:c.getShop().getId(),c.isActive());}
 public VoucherView saveVoucher(CurrentActor a,Long shopId,VoucherInput input){
  Shop shop=shopId==null?null:scope(a,shopId);if(shopId==null&&!admin(a))throw denied();
  if(!input.endsAt().isAfter(input.startsAt()))throw invalid("Ngày hết hạn phải sau ngày bắt đầu");
  if(vouchers.findByCode(input.code()).isPresent())throw invalid("Mã đã tồn tại");
  Voucher v=new Voucher();v.setShop(shop);v.setCode(input.code());v.setDiscountAmount(input.discountAmount());v.setMinimumAmount(input.minimumAmount());v.setStartsAt(input.startsAt());v.setEndsAt(input.endsAt());v.setUsageLimit(input.usageLimit());v.setPerAccountLimit(input.perAccountLimit());v.setActive(input.active());
  return voucherView(vouchers.save(v));
 }
 public void disableVoucher(CurrentActor a,Long voucherId){var selected=vouchers.findById(voucherId).orElseThrow(this::denied);if(selected.getShop()==null){if(!admin(a))throw denied();}else scope(a,selected.getShop().getId());var v=vouchers.lockCode(selected.getCode()).orElseThrow(this::denied);v.setActive(false);}
 @Transactional(readOnly=true) public List<VoucherView> vouchers(CurrentActor a,Long shopId){
  if(shopId==null&&!admin(a))throw denied();if(shopId!=null)scope(a,shopId);
  return vouchers.findAll().stream().filter(v->Objects.equals(shopId,v.getShop()==null?null:v.getShop().getId())).map(this::voucherView).toList();
 }
 private VoucherView voucherView(Voucher v){return new VoucherView(v.getId(),v.getCode(),v.getShop()==null?null:v.getShop().getId(),v.getDiscountAmount(),v.getMinimumAmount(),v.getStartsAt(),v.getEndsAt(),v.getUsageLimit(),v.getUsedCount(),v.getPerAccountLimit(),v.isActive());}
 public OrderView checkout(CurrentActor a,Checkout input,String key){
  if(input.paymentMethod()==DeliveryOrder.Method.VNPAY)gateway.requireConfigured();
  var buyer=account(a);
  if(key==null||!key.matches("[A-Za-z0-9_-]{8,64}"))throw invalid("Thiếu mã chống lặp (8–64 ký tự)");
  String hash=hash(input.toString());
  var duplicate=orders.findByAccountIdAndIdempotencyKey(buyer.getId(),key);
  if(duplicate.isPresent()){if(!duplicate.get().getRequestHash().equals(hash))throw BusinessException.conflict(ErrorCode.IDEMPOTENCY_CONFLICT,"Mã yêu cầu đã dùng cho nội dung khác");return orderView(duplicate.get());}
  if(input.cartVersion()!=null) {
   var cart=customerData.cart(a);
   if(!cart.getVersion().equals(input.cartVersion())||!selectionSignature(input.items()).equals(selectionSignature(branches.cartItems(a))))throw BusinessException.conflict(ErrorCode.CART_VERSION_CONFLICT,"Giỏ đã thay đổi hoặc chưa gửi đủ món. Vui lòng kiểm tra lại toàn bộ giỏ.");
  }
  var plan=branches.resolve(a,input.shopId(),input.addressId(),input.items(),true);
  if(!plan.issues().isEmpty())throw BusinessException.conflict(ErrorCode.INVENTORY_INSUFFICIENT,String.join("; ",plan.issues()));
  var shop=plan.shop();
  var access=customers.findByShopIdAndAccountId(shop.getId(),buyer.getId()).orElseGet(()->{var c=new ShopCustomerAccess();c.setShop(shop);c.setAccount(buyer);return c;});
  if(access.isBlocked())throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED,"Tài khoản bị hạn chế mua tại cửa hàng này");
  customers.save(access);
  var address=plan.address();
  var carrier=carriers.findById(input.carrierId()).filter(Carrier::isActive).orElseThrow(()->invalid("Đơn vị vận chuyển không khả dụng"));
  if(carrier.getShop()!=null&&!shop.getId().equals(carrier.getShop().getId()))throw denied();
  DeliveryOrder o=new DeliveryOrder();o.setAccount(buyer);o.setShop(shop);o.setCarrier(carrier);o.setRecipient(address.getRecipient());o.setPhone(address.getPhone());o.setAddressLine(address.getAddressLine());o.setPaymentMethod(input.paymentMethod());o.setIdempotencyKey(key);o.setRequestHash(hash);o.setCommissionPercent(shop.getCommissionPercent());o.setShippingFee(carrier.getFee());
  BigDecimal subtotal=plan.subtotal();
  for(var selection:plan.selections()) {
   var p=selection.product();var l=new DeliveryLine();l.setOrder(o);l.setProduct(p);l.setProductName(p.getName());l.setQuantity(selection.quantity());l.setUnitPrice(p.getPrice());l.setSnapshotSize(p.getSize());l.setNote(selection.note());l.setTopping(selection.topping());o.getLines().add(l);
  }
  o.setSubtotal(subtotal);
  if(input.voucherCode()!=null&&!input.voucherCode().isBlank()){
   var v=voucher(input.voucherCode(),buyer.getId(),shop.getId(),subtotal,true);
   v.setUsedCount(v.getUsedCount()+1);o.setVoucher(v);o.setDiscountAmount(v.getDiscountAmount().min(subtotal));
  }
  o.setTotalAmount(subtotal.subtract(o.getDiscountAmount()).add(o.getShippingFee()));
  if(input.expectedTotal()!=null&&input.expectedTotal().compareTo(o.getTotalAmount())!=0)throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,"Giá, phí hoặc ưu đãi đã thay đổi. Vui lòng xem lại tổng tiền và xác nhận lại.");
  plan.quantities().forEach((productId,quantity)->{var p=products.findById(productId).orElseThrow(this::denied);p.setDeliveryStock(p.getDeliveryStock()-quantity);});
  var created = orders.saveAndFlush(o);
  notifyCashierDeliveryChanged(created);
  return orderView(created);
 }
 private Map<String,Integer> selectionSignature(List<BuyLine> lines){
  Map<String,Integer> result=new TreeMap<>();for(var l:lines){var ids=l.toppingIds()==null?List.<Long>of():l.toppingIds().stream().sorted().toList();result.merge(l.productId()+"|"+ids+"|"+Objects.toString(l.note(),""),l.quantity(),Integer::sum);}return result;
 }
 private Voucher voucher(String code,Long accountId,Long shopId,BigDecimal subtotal,boolean lock){
  var v=(lock?vouchers.lockCode(code.trim().toUpperCase(Locale.ROOT)):vouchers.findByCode(code.trim().toUpperCase(Locale.ROOT))).orElseThrow(()->invalid("Mã giảm giá không tồn tại"));
  Instant now=Instant.now();
  if(!v.isActive()||now.isBefore(v.getStartsAt())||!now.isBefore(v.getEndsAt())||subtotal.compareTo(v.getMinimumAmount())<0||v.getUsedCount()>=v.getUsageLimit()||orders.countByAccountIdAndVoucherId(accountId,v.getId())>=v.getPerAccountLimit()||(v.getShop()!=null&&!shopId.equals(v.getShop().getId())))throw invalid("Mã hết hạn, hết lượt hoặc không đủ điều kiện áp dụng");
  return v;
 }
 @Transactional(readOnly=true) public CartQuote quote(CurrentActor a,QuoteInput input){
  var cart=customerData.cart(a);var plan=branches.resolve(a,input.shopId(),input.addressId(),branches.cartItems(a),false);
  if(!plan.issues().isEmpty())throw BusinessException.conflict(ErrorCode.INVENTORY_INSUFFICIENT,String.join("; ",plan.issues()));
  var carrier=carriers.findById(input.carrierId()).filter(Carrier::isActive).orElseThrow(()->invalid("Đơn vị vận chuyển không khả dụng"));
  if(carrier.getShop()!=null&&!input.shopId().equals(carrier.getShop().getId()))throw denied();
  var discount=input.voucherCode()==null||input.voucherCode().isBlank()?BigDecimal.ZERO:voucher(input.voucherCode(),id(a),input.shopId(),plan.subtotal(),false).getDiscountAmount().min(plan.subtotal());
  return new CartQuote(input.shopId(),cart.getVersion(),plan.subtotal(),carrier.getFee(),discount,plan.subtotal().subtract(discount).add(carrier.getFee()),plan.selections().stream().map(s->new LineView(s.product().getId(),s.product().getName(),s.quantity(),s.product().getPrice(),s.product().getSize(),s.note(),s.topping())).toList());
 }
 private String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private DeliveryOrder ownedOrder(CurrentActor a,Long orderId){
  id(a);var o=orders.lockId(orderId).orElseThrow(this::denied);
  if(admin(a)||cashier(a)||o.getAccount().getId().equals(a.getAccountId())||Objects.equals(o.getShipper()==null?null:o.getShipper().getId(),a.getAccountId()))return o;
  scope(a,o.getShop().getId());return o;
 }
 /** DeliveryOrder is independent of POS DonHang; never mix the tables or expose store-wide orders to customers. */
 @Transactional(readOnly=true) public List<OrderView> cashierDeliveryOrders(CurrentActor a){
  requireDeliveryStaff(a);
  return orders.findAll().stream().sorted(Comparator.comparing(DeliveryOrder::getId).reversed()).map(this::orderView).toList();
 }
 @Transactional(readOnly=true) public List<ShipperOption> cashierActiveShippers(CurrentActor a){
  requireDeliveryStaff(a);
  return accounts.findAll().stream().filter(x->x.getRole()==Role.SHIPPER&&Boolean.TRUE.equals(x.getActive()))
   .sorted(Comparator.comparing(Account::getFullName,Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
   .map(x->new ShipperOption(x.getId(),x.getFullName()==null||x.getFullName().isBlank()?"Nhân viên #"+x.getId():x.getFullName())).toList();
 }
 @Transactional(readOnly=true) public List<OrderView> orders(CurrentActor a,Long shopId){
  id(a);if(shopId!=null){scope(a,shopId);return orders.findByShopIdOrderByIdDesc(shopId).stream().map(this::orderView).toList();}
  var all=admin(a)?orders.findAll():a.hasRole(Role.SHIPPER)?orders.findByShipperIdOrderByIdDesc(id(a)):orders.findByAccountIdOrderByIdDesc(id(a));
  return all.stream().sorted(Comparator.comparing(DeliveryOrder::getId).reversed()).map(this::orderView).toList();
 }
 public OrderView order(CurrentActor a,Long orderId){return orderView(ownedOrder(a,orderId));}
 @Transactional(readOnly=true) public List<ShipperOption> activeShippers(CurrentActor a,Long shopId){
  scope(a,shopId);
  return accounts.findAll().stream()
   .filter(x->x.getRole()==Role.SHIPPER && Boolean.TRUE.equals(x.getActive()))
   .sorted(Comparator.comparing(Account::getFullName,Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
   .map(x->new ShipperOption(x.getId(),x.getFullName()==null||x.getFullName().isBlank()?"Nhân viên #"+x.getId():x.getFullName()))
   .toList();
 }
 public OrderView assignShipper(CurrentActor a,Long orderId,AssignShipper input){
  var o=orders.lockId(orderId).orElseThrow(this::denied);if(!cashier(a))scope(a,o.getShop().getId());version(o,input.version());
  if(o.getStatus()!=DeliveryOrder.Status.CONFIRMED)throw invalid("Chỉ phân công đơn đã xác nhận");
  var shipper=accounts.findById(input.accountId()).filter(x->x.getRole()==Role.SHIPPER&&Boolean.TRUE.equals(x.getActive())).orElseThrow(()->invalid("Shipper không hoạt động"));o.setShipper(shipper);orders.flush();notifyCashierDeliveryChanged(o);return orderView(o);
 }
 public OrderView transition(CurrentActor a,Long orderId,Transition input){
  var o=ownedOrder(a,orderId);version(o,input.version());
  boolean buyer=o.getAccount().getId().equals(id(a));
  boolean shipper=a.hasRole(Role.SHIPPER)&&o.getShipper()!=null&&o.getShipper().getId().equals(id(a));
  boolean seller=admin(a)||cashier(a)||shops.findAccessible(id(a)).stream().anyMatch(s->s.getId().equals(o.getShop().getId())&&((a.hasRole(Role.VENDOR)&&s.getOwner().getId().equals(id(a)))||a.hasRole(Role.MANAGER)));
  var before=o.getStatus();var next=input.status();boolean allowed=false;
  if(before==next)return orderView(o);
  switch(next){
   case CONFIRMED -> allowed=seller&&before==DeliveryOrder.Status.NEW;
   case SHIPPING -> allowed=(shipper||admin(a))&&before==DeliveryOrder.Status.CONFIRMED&&(o.getPaymentMethod()==DeliveryOrder.Method.COD||o.isPaid());
   case DELIVERED -> allowed=(shipper||admin(a))&&before==DeliveryOrder.Status.SHIPPING;
   case DELIVERY_FAILED -> allowed=(shipper||admin(a))&&before==DeliveryOrder.Status.SHIPPING;
   case CANCELLED -> allowed=(buyer&&before==DeliveryOrder.Status.NEW)||(seller&&(before==DeliveryOrder.Status.NEW||before==DeliveryOrder.Status.CONFIRMED||before==DeliveryOrder.Status.DELIVERY_FAILED));
   case RETURN_REQUESTED -> allowed=buyer&&before==DeliveryOrder.Status.DELIVERED;
   case RETURNED -> allowed=seller&&before==DeliveryOrder.Status.RETURN_REQUESTED;
   case REFUNDED -> {
    allowed=admin(a)&&o.isPaid()&&(before==DeliveryOrder.Status.RETURNED||before==DeliveryOrder.Status.CANCELLED)&&o.getPaymentMethod()==DeliveryOrder.Method.COD;
    if(allowed&&(input.reference()==null||input.reference().isBlank()))throw invalid("Cần tham chiếu phiếu hoàn tiền thực tế");
   }
   default -> {}
  }
  if(!allowed)throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,"Không đủ quyền hoặc chuyển trạng thái không hợp lệ");
  if(next==DeliveryOrder.Status.CANCELLED||next==DeliveryOrder.Status.RETURNED){
   for(var line:o.getLines().stream().sorted(Comparator.comparing(l->l.getProduct().getId())).toList()){
    var p=products.findByIdWithLock(line.getProduct().getId()).orElseThrow(this::denied);p.setDeliveryStock(Math.addExact(p.getDeliveryStock(),line.getQuantity()));
   }
  }
  if(next==DeliveryOrder.Status.DELIVERED){o.setDeliveredAt(Instant.now());if(o.getPaymentMethod()==DeliveryOrder.Method.COD)o.setPaid(true);}
  if(next==DeliveryOrder.Status.DELIVERY_FAILED)o.setDeliveryFailed(true);
  if(next==DeliveryOrder.Status.REFUNDED)o.setRefundReference(input.reference().trim());
  o.setStatus(next);orders.flush();notifyCashierDeliveryChanged(o);return orderView(o);
 }
 /** Notify staff only after the database transaction commits; no customer data in the event. */
 private void notifyCashierDeliveryChanged(DeliveryOrder order){
  realtime.publishAfterCommit("/topic/cashier", "DELIVERY_ORDER_CHANGED",
      String.valueOf(order.getId()), null, String.valueOf(order.getVersion()),
      java.util.Map.of("orderId", order.getId(), "shopId", order.getShop().getId()));
 }
 private void version(DeliveryOrder o,Long expected){if(!Objects.equals(o.getVersion(),expected))throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,"Đơn đã thay đổi; vui lòng tải lại");}
 public OrderView orderView(DeliveryOrder o){return new OrderView(o.getId(),o.getShop().getId(),o.getShop().getName(),o.getAccount().getId(),o.getShipper()==null?null:o.getShipper().getId(),o.getStatus(),o.getPaymentMethod(),o.isPaid(),o.getRecipient(),o.getPhone(),o.getAddressLine(),o.getSubtotal(),o.getShippingFee(),o.getDiscountAmount(),o.getTotalAmount(),o.getCommissionPercent(),o.getCreatedAt(),o.isDeliveryFailed(),o.getVersion(),o.getLines().stream().map(l->new LineView(l.getProduct().getId(),l.getProductName(),l.getQuantity(),l.getUnitPrice(),l.getSnapshotSize(),l.getNote(),l.isTopping())).toList());}
 @Transactional(readOnly=true) public Summary summary(CurrentActor a,Long shopId){
  var list=orders(a,shopId);var paid=list.stream().filter(o->o.paid()&&Set.of(DeliveryOrder.Status.DELIVERED,DeliveryOrder.Status.RETURN_REQUESTED).contains(o.status())).toList();
  return new Summary(list.size(),list.stream().filter(o->Set.of(DeliveryOrder.Status.DELIVERED,DeliveryOrder.Status.RETURN_REQUESTED,DeliveryOrder.Status.RETURNED,DeliveryOrder.Status.REFUNDED).contains(o.status())).count(),list.stream().filter(OrderView::deliveryFailed).count(),paid.stream().map(OrderView::totalAmount).reduce(BigDecimal.ZERO,BigDecimal::add),paid.stream().map(o->o.subtotal().subtract(o.discountAmount()).multiply(o.commissionPercent()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO,BigDecimal::add));
 }
 @Transactional(readOnly=true) public List<AccountView> accounts(CurrentActor a){
  if(!admin(a))throw denied();return accounts.findAll().stream().map(x->new AccountView(x.getId(),x.getEmail(),x.getFullName(),x.getRole(),Boolean.TRUE.equals(x.getActive()))).toList();
 }
 public void changeRole(CurrentActor a,Long targetId,RoleInput input){
  if(!admin(a)||id(a).equals(targetId))throw denied();var target=accounts.findByIdWithLock(targetId).orElseThrow(this::denied);
  if(target.getRole()==Role.ADMIN)throw invalid("Không thay đổi quản trị viên qua chức năng này");
  target.setRole(input.role());target.setActive(input.active());target.setTokenVersion(target.getTokenVersion()+1);
 }
 @Transactional(readOnly=true) public List<AccountView> customers(CurrentActor a,Long shopId){
  scope(a,shopId);return customers.findByShopId(shopId).stream().map(c->{var x=c.getAccount();return new AccountView(x.getId(),x.getEmail(),x.getFullName(),x.getRole(),!c.isBlocked());}).toList();
 }
 public void customerAccess(CurrentActor a,Long shopId,Long accountId,boolean enabled){
  scope(a,shopId);accounts.findByIdWithLock(accountId).orElseThrow(this::denied);
  var access=customers.findByShopIdAndAccountId(shopId,accountId).orElseThrow(()->invalid("Khách chưa có giao dịch tại shop này"));access.setBlocked(!enabled);
 }
 public ProductView productView(Product p){
  double rating=em.createQuery("select coalesce(avg(r.stars),0) from ProductReview r where r.product.id=:id",Double.class).setParameter("id",p.getId()).getSingleResult();
  long favorite=em.createQuery("select count(i) from ProductInterest i where i.product.id=:id and i.favorite=true",Long.class).setParameter("id",p.getId()).getSingleResult();
  return new ProductView(p.getId(),p.getName(),p.getSize(),p.getDescription(),p.getPrice(),MenuCatalogSupport.resolveIllustration(p),p.getCategory().getId(),p.getCategory().getName(),p.getShop()==null?null:p.getShop().getId(),p.getShop()==null?"UTE Tea tại quầy":p.getShop().getName(),p.getDeliveryStock(),Boolean.TRUE.equals(p.getActive()),p.getCatalogVersion(),rating,favorite,p.menuIdentity());
 }
}
