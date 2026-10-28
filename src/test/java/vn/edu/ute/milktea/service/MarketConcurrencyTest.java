package vn.edu.ute.milktea.service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.dto.CustomerDataDto;
import vn.edu.ute.milktea.dto.CartDto;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.entity.market.DeliveryOrder;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;
import vn.edu.ute.milktea.repository.market.VoucherRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.MarketService;
import vn.edu.ute.milktea.service.account.CustomerDataService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class MarketConcurrencyTest {
 @Autowired MarketService market;@Autowired CustomerDataService data;@Autowired AccountRepository accounts;
 @Autowired ProductRepository products;@Autowired VoucherRepository vouchers;@Autowired PlatformTransactionManager transactions;
 record Fixture(Long shop,Long product,Long carrier,List<CurrentActor> buyers,List<Long> addresses,String voucher){}
 CurrentActor actor(Role role){var a=accounts.save(Account.builder().email(UUID.randomUUID()+"@race.test").passwordHash("test-only").role(role).build());return CurrentActor.authenticated(a.getId(),role);}
 Fixture fixture(boolean quota){return new TransactionTemplate(transactions).execute(status->{
  var admin=actor(Role.ADMIN);var vendor=actor(Role.VENDOR);var shop=market.createShop(vendor,new ShopInput("Race "+UUID.randomUUID(),"1 Võ Văn Ngân")).id();market.configureShop(admin,shop,new ShopConfig(true,BigDecimal.ZERO));
  var cat=market.saveCategory(vendor,shop,null,new CategoryInput("Race",true)).id();var product=market.saveProduct(vendor,shop,null,new ProductInput(cat,"Last cup","M",null,new BigDecimal("25000"),quota?2:1,true)).id();
  var carrier=market.saveCarrier(admin,null,null,new CarrierInput("Race",BigDecimal.ZERO,true)).id();var buyers=List.of(actor(Role.CUSTOMER),actor(Role.CUSTOMER));
  var addresses=buyers.stream().map(b->data.saveAddress(b,null,new CustomerDataDto.AddressInput("Buyer","0901000001","2 Võ Văn Ngân",true)).id()).toList();
  buyers.forEach(b->data.replaceCart(b,CartDto.ReplaceCartRequest.builder().items(List.of(CartDto.CartItemRequest.builder().productId(product).quantity(1).build())).build(),data.cart(b).getVersion()));
  String voucher=quota?"RACE"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase():null;
  if(quota)market.saveVoucher(vendor,shop,new VoucherInput(voucher,new BigDecimal("1000"),BigDecimal.ZERO,Instant.now().minusSeconds(30),Instant.now().plusSeconds(600),1,1,true));
  return new Fixture(shop,product,carrier,buyers,addresses,voucher);
 });}
 void race(Fixture f)throws Exception{
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{
   List<Future<Boolean>> results=new ArrayList<>();for(int n=0;n<2;n++){final int i=n;results.add(pool.submit(()->{start.await();try{market.checkout(f.buyers().get(i),new Checkout(f.shop(),f.addresses().get(i),f.carrier(),f.voucher(),DeliveryOrder.Method.COD,List.of(new BuyLine(f.product(),1)),null,data.cart(f.buyers().get(i)).getVersion()),UUID.randomUUID().toString());return true;}catch(BusinessException expected){return false;}}));}
   start.countDown();int success=0;for(var result:results)if(result.get(20,TimeUnit.SECONDS))success++;assertEquals(1,success);
  }finally{pool.shutdownNow();}
 }
 @Test void twoBuyersCannotOversellLastCup()throws Exception{var f=fixture(false);race(f);assertEquals(0,products.findById(f.product()).orElseThrow().getDeliveryStock());}
 @Test void lastVoucherUseIsAtomicAndFailedCheckoutRestoresStock()throws Exception{var f=fixture(true);race(f);assertEquals(1,products.findById(f.product()).orElseThrow().getDeliveryStock());assertEquals(1,vouchers.findByCode(f.voucher()).orElseThrow().getUsedCount());}
}
