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
 @Autowired DeliveryWorkspaceService delivery;
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
 @Test void customerHistoryOnlyShowsOwnOrdersAndBlocksForeignDetails(){
  var stranger=actor("stranger-customer",Role.CUSTOMER);
  var mine=market.checkout(buyer,input(null),"customer-history-test");
  assertEquals(1,market.orders(buyer,null).size());
  assertEquals(mine.id(),market.orders(buyer,null).getFirst().id());
  assertTrue(market.orders(stranger,null).isEmpty());
  assertThrows(BusinessException.class,()->market.order(stranger,mine.id()));
  assertThrows(BusinessException.class,()->market.orders(buyer,shop));
 }
 @Test void voucherCheckoutIsIdempotentAndPricesAreServerOwned(){
  market.saveVoucher(vendor,shop,new VoucherInput("TEST10",new BigDecimal("10000.00"),new BigDecimal("40000"),Instant.now().minusSeconds(60),Instant.now().plusSeconds(600),2,1,true));
  var first=market.checkout(buyer,input("TEST10"),"checkout-key-1");
  assertEquals(new BigDecimal("50000.00"),first.totalAmount());
  assertEquals(first.id(),market.checkout(buyer,input("TEST10"),"checkout-key-1").id());
  assertEquals(8,products.findById(product).orElseThrow().getDeliveryStock());
  assertThrows(BusinessException.class,()->market.checkout(buyer,input(null),"checkout-key-1"));
 }
 @Test void shipperWorkflowAndPurchasedOnlyReviews(){
  var o=market.checkout(buyer,input(null),"checkout-key-2");
  Long orderId=o.id();
  assertThrows(BusinessException.class,()->reviews.create(other,product,new ReviewInput(orderId,true,5,"Đây là đánh giá dài hơn năm mươi ký tự nhưng tài khoản này chưa mua hàng.")));
  o=market.transition(vendor,o.id(),new Transition(DeliveryOrder.Status.CONFIRMED,o.version(),null));
  o=market.assignShipper(vendor,o.id(),new AssignShipper(shipper.getAccountId(),o.version()));
  o=market.transition(shipper,o.id(),new Transition(DeliveryOrder.Status.SHIPPING,o.version(),null));
  o=market.transition(shipper,o.id(),new Transition(DeliveryOrder.Status.DELIVERED,o.version(),null));
  assertTrue(o.paid());
  var review=reviews.create(buyer,product,new ReviewInput(o.id(),true,5,"Trà rất thơm ngon, đóng gói cẩn thận và giao hàng nhanh chóng, mình rất hài lòng."));
  assertEquals(5,review.stars());assertEquals(1,reviews.list(product,0).size());
  assertEquals(new BigDecimal("5000.00"),market.summary(vendor,shop).platformCommission());
  assertTrue(search.search(null,null,shop,null,null,"rating",0,20).items().getFirst().rating()>0);
 }
 @Test void courierWorkspaceOnlyListsAssignedOrdersAndProtectsTransitions(){
  var alternate=actor("alternate-courier",Role.SHIPPER);
  var first=market.checkout(buyer,input(null),"courier-1");
  var second=market.checkout(buyer,input(null),"courier-2");
  first=market.transition(vendor,first.id(),new Transition(DeliveryOrder.Status.CONFIRMED,first.version(),null));
  second=market.transition(vendor,second.id(),new Transition(DeliveryOrder.Status.CONFIRMED,second.version(),null));
  first=market.assignShipper(vendor,first.id(),new AssignShipper(shipper.getAccountId(),first.version()));
  second=market.assignShipper(vendor,second.id(),new AssignShipper(alternate.getAccountId(),second.version()));
  assertThrows(BusinessException.class,()->delivery.workspace(buyer));
  assertEquals(1,delivery.workspace(shipper).statistics().assigned());
  assertEquals(first.id(),delivery.workspace(shipper).orders().getFirst().id());
  assertEquals(1,delivery.workspace(alternate).statistics().assigned());
  var firstAssigned=first;
  assertThrows(BusinessException.class,()->delivery.updateStatus(alternate,firstAssigned.id(),
      new Transition(DeliveryOrder.Status.SHIPPING,firstAssigned.version(),null)));
  assertThrows(BusinessException.class,()->delivery.updateStatus(shipper,firstAssigned.id(),
      new Transition(DeliveryOrder.Status.CANCELLED,firstAssigned.version(),null)));
  var shipping=delivery.updateStatus(shipper,first.id(),new Transition(DeliveryOrder.Status.SHIPPING,first.version(),null));
  assertEquals(1,delivery.workspace(shipper).statistics().shipping());
  assertThrows(BusinessException.class,()->delivery.updateStatus(shipper,shipping.id(),
      new Transition(DeliveryOrder.Status.DELIVERED,firstAssigned.version(),null)));
  var delivered=delivery.updateStatus(shipper,shipping.id(),new Transition(DeliveryOrder.Status.DELIVERED,shipping.version(),null));
  assertEquals(DeliveryOrder.Status.DELIVERED,delivered.status());
  var stats=delivery.workspace(shipper).statistics();
  assertEquals(1,stats.delivered());
  assertEquals(0,stats.failed());
  assertEquals(100.0,stats.successRate());
  assertEquals(1,stats.codDelivered());
  assertEquals(0,new BigDecimal("60000.00").compareTo(stats.codCollected()));
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
