package vn.edu.ute.milktea.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.dto.*;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.entity.catalog.*;
import vn.edu.ute.milktea.entity.market.DeliveryOrder;
import vn.edu.ute.milktea.repository.account.*;
import vn.edu.ute.milktea.repository.catalog.*;
import vn.edu.ute.milktea.repository.market.*;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.account.CustomerDataService;
import vn.edu.ute.milktea.service.market.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class BranchCartIntegrationTest {
    @Autowired MarketService market; @Autowired BranchSelectionService branches; @Autowired CustomerDataService data;
    @Autowired AccountRepository accounts; @Autowired ProductRepository products; @Autowired CategoryRepository categories;
    @Autowired AccountCartRepository carts; @Autowired ShopRepository shops; @Autowired DeliveryAddressRepository addresses;
    @Autowired DeliveryOrderRepository orders; @Autowired CatalogSearchService catalog;
    @Autowired jakarta.validation.Validator validator;
    CurrentActor admin,vendor,buyer,other;
    Long shopA,shopB,reference,toppingReference,baseA,baseB,topA,topB,address,carrier;
    CurrentActor actor(Role role){var a=accounts.save(Account.builder().email(UUID.randomUUID()+"@branch.test").passwordHash("test-only").role(role).build());return CurrentActor.authenticated(a.getId(),role);}
    Product product(Long shop,Long category,String name,String size,String code,String price,int stock){
        return products.saveAndFlush(Product.builder().shop(shop==null?null:shops.findById(shop).orElseThrow()).category(categories.findById(category).orElseThrow()).name(name).size(size).menuCode(code).price(new BigDecimal(price)).deliveryStock(shop==null?null:stock).active(true).build());
    }
    @BeforeEach void setup(){
        admin=actor(Role.ADMIN);vendor=actor(Role.VENDOR);buyer=actor(Role.CUSTOMER);other=actor(Role.CUSTOMER);
        shopA=market.createShop(vendor,new ShopInput("A · Cơ sở gần","A", "Thủ Đức",10.77,106.69)).id();
        shopB=market.createShop(vendor,new ShopInput("B · Cơ sở khác","B", "Quận 1",10.80,106.72)).id();
        market.configureShop(admin,shopA,new ShopConfig(true,BigDecimal.ZERO));market.configureShop(admin,shopB,new ShopConfig(true,BigDecimal.ZERO));
        Long global=categories.save(Category.builder().name("Neutral drinks "+UUID.randomUUID()).active(true).allowTopping(true).build()).getId();
        Long globalTop=categories.save(Category.builder().name("Topping "+UUID.randomUUID()).active(true).allowTopping(false).build()).getId();
        reference=product(null,global,"Trà chung","M","shared-milk","23000",0).getId();
        toppingReference=product(null,globalTop,"Trân châu chung","TOPPING","shared-pearl","4000",0).getId();
        Long ca=market.saveCategory(vendor,shopA,null,new CategoryInput("Trà",true,true)).id();
        Long cb=market.saveCategory(vendor,shopB,null,new CategoryInput("Trà",true,true)).id();
        Long ta=market.saveCategory(vendor,shopA,null,new CategoryInput("Topping",true,false)).id();
        Long tb=market.saveCategory(vendor,shopB,null,new CategoryInput("Topping",true,false)).id();
        baseA=product(shopA,ca,"Trà chung","M","shared-milk","25000",5).getId();
        baseB=product(shopB,cb,"Trà chung","M","shared-milk","32000",5).getId();
        topA=product(shopA,ta,"Trân châu chung","TOPPING","shared-pearl","5000",5).getId();
        topB=product(shopB,tb,"Trân châu chung","TOPPING","shared-pearl","6000",5).getId();
        address=data.saveAddress(buyer,null,new CustomerDataDto.AddressInput("Khách","0901000001","Địa chỉ thử",true,"Thủ Đức",10.77,106.69)).id();
        carrier=market.saveCarrier(admin,null,null,new CarrierInput("Giao chuẩn",new BigDecimal("10000"),true)).id();
    }
    CartDto.CartResponse cart(int quantity){return data.replaceCart(buyer,CartDto.ReplaceCartRequest.builder().items(List.of(CartDto.CartItemRequest.builder().productId(baseA).quantity(quantity).note("Ít đá").toppingIds(List.of(topA)).build())).build(),data.cart(buyer).getVersion());}
    Checkout checkout(Long shop,CartQuote quote){return new Checkout(shop,address,carrier,null,DeliveryOrder.Method.COD,branches.cartItems(buyer),quote.totalAmount(),quote.cartVersion());}
    @Test void storedCartIsBranchNeutralAndIdenticalChoicesMerge(){
        var lines=List.of(CartDto.CartItemRequest.builder().productId(baseA).quantity(1).toppingIds(List.of(topA)).build(),CartDto.CartItemRequest.builder().productId(baseB).quantity(2).toppingIds(List.of(topB)).build());
        var cart=data.replaceCart(buyer,CartDto.ReplaceCartRequest.builder().items(lines).build(),"0");
        assertEquals(1,cart.getItems().size());assertEquals(3,cart.getItems().getFirst().getQuantity());
        assertEquals(reference,cart.getItems().getFirst().getProductId());assertNull(cart.getItems().getFirst().getShopId());
        assertEquals(List.of(toppingReference),cart.getItems().getFirst().getToppingIds());
        String json=carts.findById(buyer.getAccountId()).orElseThrow().getItemsJson();assertFalse(json.contains("shopId"));assertFalse(json.contains("branchId"));
        assertEquals(1,data.cart(buyer).getItems().size());assertTrue(data.cart(other).getItems().isEmpty());
    }
    @Test void coordinatesRankNearestAndChangingAddressChangesRanking(){
        cart(1);var list=branches.recommendations(buyer,address);
        assertEquals(shopA,list.getFirst().id());assertTrue(list.getFirst().nearest());assertEquals(0,list.getFirst().distanceKm(),.000001);
        Long moved=data.saveAddress(buyer,null,new CustomerDataDto.AddressInput("Khách","0901000001","Địa chỉ B",false,"Quận 1",10.80,106.72)).id();
        assertEquals(shopB,branches.recommendations(buyer,moved).getFirst().id());
        assertEquals(111.195,BranchSelectionService.distanceKm(0,0,0,1),.01);
    }
    @Test void missingOrInvalidCoordinatesUseConfiguredAreaWithoutInventedKilometres(){
        cart(1);var a=addresses.findById(address).orElseThrow();a.setLatitude(null);a.setLongitude(null);a.setArea("quan 1");
        var list=branches.recommendations(buyer,address);assertEquals(shopB,list.getFirst().id());assertEquals("AREA",list.getFirst().rankingMethod());assertTrue(list.getFirst().nearest());assertNull(list.getFirst().distanceKm());
        a.setArea(null);a.setLatitude(200.0);a.setLongitude(300.0);
        assertTrue(branches.recommendations(buyer,address).stream().noneMatch(BranchOption::nearest));
        assertTrue(branches.recommendations(buyer,address).stream().allMatch(b->b.distanceKm()==null));
    }
    @Test void chosenBranchRepricesAndReservesAllBaseAndToppingStockThenRestoresOnCancel(){
        cart(2);var quote=market.quote(buyer,new QuoteInput(shopB,address,carrier,null));
        assertEquals(0,new BigDecimal("76000").compareTo(quote.subtotal()));assertEquals(0,new BigDecimal("86000").compareTo(quote.totalAmount()));
        var order=market.checkout(buyer,checkout(shopB,quote),"branch-order-1");assertEquals(shopB,order.shopId());assertEquals(2,order.items().size());
        assertTrue(order.items().stream().anyMatch(LineView::topping));assertTrue(order.items().stream().allMatch(l->"Ít đá".equals(l.note())));
        assertEquals(3,products.findById(baseB).orElseThrow().getDeliveryStock());assertEquals(3,products.findById(topB).orElseThrow().getDeliveryStock());assertEquals(5,products.findById(baseA).orElseThrow().getDeliveryStock());
        assertEquals(order.id(),market.checkout(buyer,checkout(shopB,quote),"branch-order-1").id());
        market.transition(buyer,order.id(),new Transition(DeliveryOrder.Status.CANCELLED,order.version(),null));
        assertEquals(5,products.findById(baseB).orElseThrow().getDeliveryStock());assertEquals(5,products.findById(topB).orElseThrow().getDeliveryStock());
    }
    @Test void noPartialOrderOrAutomaticSplitWhenBranchLacksSizeOrTopping(){
        cart(2);products.findById(topA).orElseThrow().setDeliveryStock(1);
        var nearest=branches.recommendations(buyer,address).getFirst();assertTrue(nearest.nearest());assertFalse(nearest.canServe());assertTrue(nearest.issues().stream().anyMatch(m->m.contains("Trân châu")));
        assertThrows(BusinessException.class,()->market.quote(buyer,new QuoteInput(shopA,address,carrier,null)));
        assertThrows(BusinessException.class,()->market.checkout(buyer,new Checkout(shopA,address,carrier,null,DeliveryOrder.Method.COD,branches.cartItems(buyer)),"no-split-key"));
        assertTrue(orders.findByAccountIdOrderByIdDesc(buyer.getAccountId()).isEmpty());assertEquals(5,products.findById(baseA).orElseThrow().getDeliveryStock());
    }
    @Test void stalePriceIsRejectedBeforeStockIsChanged(){
        cart(1);var quote=market.quote(buyer,new QuoteInput(shopB,address,carrier,null));products.findById(baseB).orElseThrow().setPrice(new BigDecimal("33000"));products.flush();
        assertThrows(BusinessException.class,()->market.checkout(buyer,checkout(shopB,quote),"stale-price-key"));assertEquals(5,products.findById(baseB).orElseThrow().getDeliveryStock());
    }
    @Test void changingQuantityInvalidatesQuoteAndOmittingAnyCartItemIsRejected(){
        cart(1);var quote=market.quote(buyer,new QuoteInput(shopB,address,carrier,null));cart(2);
        assertThrows(BusinessException.class,()->market.checkout(buyer,checkout(shopB,quote),"old-cart-key"));
        var current=market.quote(buyer,new QuoteInput(shopB,address,carrier,null));
        assertThrows(BusinessException.class,()->market.checkout(buyer,new Checkout(shopB,address,carrier,null,DeliveryOrder.Method.COD,List.of(new BuyLine(reference,1)),current.totalAmount(),current.cartVersion()),"partial-cart-key"));
    }
    @Test void addressOwnershipAndCoordinatePairAreValidated(){
        cart(1);assertThrows(BusinessException.class,()->branches.recommendations(other,address));
        assertFalse(validator.validate(new CustomerDataDto.AddressInput("A","0901000001","A",false,"Q1",10.0,null)).isEmpty());
        assertFalse(validator.validate(new ShopInput("A","A","Q1",91.0,100.0)).isEmpty());
    }
    @Test void catalogueDeduplicatesBranchesButKeepsDistinctSizes(){
        var all=catalog.search("Trà chung",null,null,null,null,"newest",0,100);assertEquals(1,all.total());
        var p=products.findById(baseA).orElseThrow();product(shopA,p.getCategory().getId(),"Trà chung","L","shared-milk","30000",5);
        assertEquals(2,catalog.search("Trà chung",null,null,null,null,"newest",0,100).total());
        assertEquals(2,catalog.options(reference).variants().size());
    }
    @Test void closingReferenceBranchDoesNotBindOrLoseTheMenuSelection(){
        cart(1);products.findById(reference).orElseThrow().setActive(false);products.findById(baseA).orElseThrow().setActive(false);products.flush();
        var existing=data.cart(buyer);var line=existing.getItems().getFirst();
        var changed=data.replaceCart(buyer,CartDto.ReplaceCartRequest.builder().items(List.of(CartDto.CartItemRequest.builder().productId(line.getProductId()).quantity(2).note(line.getNote()).toppingIds(line.getToppingIds()).build())).build(),existing.getVersion());
        assertEquals(2,changed.getItems().getFirst().getQuantity());assertNull(changed.getItems().getFirst().getShopId());
        assertTrue(branches.recommendations(buyer,address).stream().filter(b->b.id().equals(shopB)).findFirst().orElseThrow().canServe());
    }
}
