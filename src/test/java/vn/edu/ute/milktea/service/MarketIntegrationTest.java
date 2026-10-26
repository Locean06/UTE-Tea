package vn.edu.ute.milktea.service;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.dto.CustomerDataDto;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.entity.market.DeliveryOrder;
import vn.edu.ute.milktea.entity.market.Voucher;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;
import vn.edu.ute.milktea.service.market.*;
import vn.edu.ute.milktea.service.account.CustomerDataService;
import vn.edu.ute.milktea.security.CurrentActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class MarketIntegrationTest {
 @Autowired MarketService market;
 @Autowired CatalogSearchService search;
 @Autowired CustomerDataService data;
 @Autowired ReviewService reviews;
 @Autowired AccountRepository accounts;
 @Autowired ProductRepository products;
 CurrentActor admin,vendor,other,manager,buyer,shipper;Long shop,otherShop,product,category,address,carrier;
 CurrentActor actor(String name,Role role){var a=accounts.save(Account.builder().email(name+"-market@example.test").passwordHash("test-only").role(role).build());return CurrentActor.authenticated(a.getId(),role);}
 @BeforeEach void setup(){
  admin=actor("admin",Role.ADMIN);vendor=actor("vendor",Role.VENDOR);other=actor("other",Role.VENDOR);manager=actor("manager",Role.MANAGER);buyer=actor("buyer",Role.CUSTOMER);shipper=actor("shipper",Role.SHIPPER);
  shop=market.createShop(vendor,new ShopInput("Shop A","1 Võ Văn Ngân")).id();otherShop=market.createShop(other,new ShopInput("Shop B","2 Võ Văn Ngân")).id();
  market.configureShop(admin,shop,new ShopConfig(true,new BigDecimal("10")));market.configureShop(admin,otherShop,new ShopConfig(true,BigDecimal.ZERO));
  category=market.saveCategory(vendor,shop,null,new CategoryInput("Danh mục A",true)).id();
  product=market.saveProduct(vendor,shop,null,new ProductInput(category,"Trà thử nghiệm","M","Món kiểm thử",new BigDecimal("25000.00"),10,true)).id();
  address=data.saveAddress(buyer,null,new CustomerDataDto.AddressInput("Người mua","0901000001","3 Võ Văn Ngân",true)).id();
  carrier=market.saveCarrier(admin,null,null,new CarrierInput("Giao thử nghiệm",new BigDecimal("10000.00"),true)).id();
 }
 Checkout input(String voucher){return new Checkout(shop,address,carrier,voucher,DeliveryOrder.Method.COD,List.of(new BuyLine(product,2)));}
 @Test void vendorAndManagerCannotCrossShopBoundary(){
  assertThrows(BusinessException.class,()->market.shopProducts(other,shop));
  assertThrows(BusinessException.class,()->market.shopProducts(manager,shop));
  market.assignManager(admin,shop,manager.getAccountId(),true);
  assertEquals(1,market.shopProducts(manager,shop).size());
  assertThrows(BusinessException.class,()->market.shopProducts(manager,otherShop));
  assertThrows(BusinessException.class,()->market.saveProduct(other,otherShop,product,new ProductInput(category,"Hijack","M",null,BigDecimal.ONE,1,true)));
 }
 @Test void voucherCheckoutIsIdempotentAndPricesAreServerOwned(){
  market.saveVoucher(vendor,shop,new VoucherInput("TEST10",new BigDecimal("10000.00"),new BigDecimal("40000"),Instant.now().minusSeconds(60),Instant.now().plusSeconds(600),2,1,true));
  var first=market.checkout(buyer,input("TEST10"),"checkout-key-1");
  assertEquals(new BigDecimal("50000.00"),first.totalAmount());
  assertEquals(first.id(),market.checkout(buyer,input("TEST10"),"checkout-key-1").id());
  assertEquals(8,products.findById(product).orElseThrow().getDeliveryStock());
  assertThrows(BusinessException.class,()->market.checkout(buyer,input(null),"checkout-key-1"));
 }
 VoucherInput promo(String code,String cap,Voucher.Kind kind,String percent){return new VoucherInput(code,new BigDecimal(cap),BigDecimal.ZERO,Instant.now().minusSeconds(60),Instant.now().plusSeconds(600),5,1,true,kind,percent==null?null:new BigDecimal(percent));}
 @Test void percentAndShippingPromotionsArePricedOnServer(){
  // đơn mẫu: 2 x 25.000 = 50.000, phí ship 10.000
  market.saveVoucher(vendor,shop,promo("PCT10","3000",Voucher.Kind.PERCENT,"10"));
  var pct=market.checkout(buyer,input("PCT10"),"promo-key-1");
  assertEquals(0,new BigDecimal("3000").compareTo(pct.discountAmount()));   // 10% = 5.000 nhưng tối đa 3.000
  assertEquals(0,new BigDecimal("57000").compareTo(pct.totalAmount()));
  market.saveVoucher(vendor,shop,promo("SHIP4K","4000",Voucher.Kind.SHIPPING,null));
  var fixed=market.checkout(buyer,input("SHIP4K"),"promo-key-2");
  assertEquals(0,fixed.discountAmount().signum());
  assertEquals(0,new BigDecimal("6000").compareTo(fixed.shippingFee()));
  assertEquals(0,new BigDecimal("56000").compareTo(fixed.totalAmount()));
  market.saveVoucher(vendor,shop,promo("FREESHIP","10000",Voucher.Kind.SHIPPING,"100"));
  var free=market.checkout(buyer,input("FREESHIP"),"promo-key-3");
  assertEquals(0,free.shippingFee().signum());
  assertEquals(0,new BigDecimal("50000").compareTo(free.totalAmount()));
  assertThrows(BusinessException.class,()->market.saveVoucher(vendor,shop,promo("BADPCT","1000",Voucher.Kind.PERCENT,"0")));
 }
 @Test void shipperWorkflowAndPurchasedOnlyReviews(){
  var o=market.checkout(buyer,input(null),"checkout-key-2");
  Long orderId=o.id();
  assertThrows(BusinessException.class,()->reviews.create(other,product,new ReviewInput(orderId,true,5,"Đây là đánh giá dài hơn năm mươi ký tự nhưng tài khoản này chưa mua hàng.")));
  o=market.transition(vendor,o.id(),new Transition(DeliveryOrder.Status.CONFIRMED,o.version(),null));
  o=market.assignShipper(vendor,o.id(),new AssignShipper(shipper.getAccountId(),o.version()));
  o=market.transition(shipper,o.id(),new Transition(DeliveryOrder.Status.PICKED_UP,o.version(),null));
  assertEquals(DeliveryOrder.Status.PICKED_UP,o.status());
  o=market.transition(shipper,o.id(),new Transition(DeliveryOrder.Status.SHIPPING,o.version(),null));
  o=market.transition(shipper,o.id(),new Transition(DeliveryOrder.Status.DELIVERED,o.version(),null));
  assertTrue(o.paid());
  var best=search.bestSellers(1,50).stream().filter(b->shop.equals(b.product().shopId())).toList();
  assertEquals(1,best.size());assertEquals(2,best.getFirst().sold());   // 2 ly đã giao
  assertTrue(search.bestSellers(2,50).stream().noneMatch(b->shop.equals(b.product().shopId())));   // "trên" ngưỡng: 2 không lớn hơn 2
  var review=reviews.create(buyer,product,new ReviewInput(o.id(),true,5,"Trà rất thơm ngon, đóng gói cẩn thận và giao hàng nhanh chóng, mình rất hài lòng."));
  assertEquals(5,review.stars());assertEquals(1,reviews.list(product,0).size());
  assertEquals(new BigDecimal("5000.00"),market.summary(vendor,shop).platformCommission());
  assertTrue(search.search(null,null,shop,null,null,"rating",0,20).items().getFirst().rating()>0);
 }
 @Test void cancelledOrderRestocksExactlyOnceAndStaleTransitionsFail(){
  var o=market.checkout(buyer,input(null),"checkout-key-3");Long initialVersion=o.version();
  var cancelled=market.transition(buyer,o.id(),new Transition(DeliveryOrder.Status.CANCELLED,initialVersion,null));
  assertEquals(10,products.findById(product).orElseThrow().getDeliveryStock());
  market.transition(buyer,o.id(),new Transition(DeliveryOrder.Status.CANCELLED,cancelled.version(),null));
  assertEquals(10,products.findById(product).orElseThrow().getDeliveryStock());
  Long orderId=o.id();assertThrows(BusinessException.class,()->market.transition(vendor,orderId,new Transition(DeliveryOrder.Status.CONFIRMED,initialVersion,null)));
 }
 @Test void catalogFiltersPaginatesAndSupportsAllDocumentedSorts(){
  assertEquals(1,search.search("tra thu",category,shop,null,null,"newest",0,20).total());
  for(String sort:List.of("newest","bestselling","rating","favorites","price_asc","price_desc")){
   var result=search.search("Trà thử",category,shop,new BigDecimal("20000"),new BigDecimal("30000"),sort,0,1);
   assertEquals(1,result.total());assertEquals(product,result.items().getFirst().id());
   assertTrue(search.search(null,null,shop,null,null,sort,1,1).items().isEmpty());
  }
  assertThrows(BusinessException.class,()->search.search(null,null,null,BigDecimal.TEN,BigDecimal.ONE,"newest",0,20));
 }
 @Test void foreignAddressAndUnconfiguredVnpayAreRejected(){
  assertThrows(BusinessException.class,()->market.checkout(other,input(null),"bad-address-key"));
  assertThrows(BusinessException.class,()->market.checkout(buyer,new Checkout(shop,address,carrier,null,DeliveryOrder.Method.VNPAY,List.of(new BuyLine(product,1))),"unconfigured-key"));
 }
 @Test void managerCustomerRestrictionIsShopScoped(){
  market.checkout(buyer,input(null),"customer-scope-key");market.assignManager(admin,shop,manager.getAccountId(),true);
  assertEquals(1,market.customers(manager,shop).size());
  assertThrows(BusinessException.class,()->market.customers(manager,otherShop));
  market.customerAccess(manager,shop,buyer.getAccountId(),false);
  assertFalse(market.customers(manager,shop).getFirst().active());
  assertThrows(BusinessException.class,()->market.checkout(buyer,input(null),"blocked-buyer-key"));
 }
 @Test void productFormCannotOverwriteStockAfterCheckout(){
  var old=market.shopProducts(vendor,shop).getFirst();market.checkout(buyer,input(null),"stock-edit-key");
  assertThrows(BusinessException.class,()->market.saveProduct(vendor,shop,product,new ProductInput(category,old.name(),old.size(),old.description(),old.price(),old.stock(),true,old.catalogVersion())));
 }
 @Test void managerCommissionRequiresExplicitAdminGrant(){
  market.assignManager(admin,shop,manager.getAccountId(),true);
  assertThrows(BusinessException.class,()->market.configureShop(manager,shop,new ShopConfig(true,new BigDecimal("7"))));
  market.configureShop(admin,shop,new ShopConfig(true,new BigDecimal("10"),true));
  assertEquals(new BigDecimal("7"),market.configureShop(manager,shop,new ShopConfig(true,new BigDecimal("7"))).commissionPercent());
  assertThrows(BusinessException.class,()->market.configureShop(manager,otherShop,new ShopConfig(true,BigDecimal.ZERO)));
 }
}