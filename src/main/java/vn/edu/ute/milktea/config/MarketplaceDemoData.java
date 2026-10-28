package vn.edu.ute.milktea.config;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.entity.catalog.*;
import vn.edu.ute.milktea.entity.market.*;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import vn.edu.ute.milktea.repository.catalog.*;
import vn.edu.ute.milktea.repository.market.*;
import java.math.BigDecimal;

/** Demo-only fixtures. Never runs against local/prod SQL Server profiles. */
@Component @Profile({"demo","prod"}) @Order(10) @RequiredArgsConstructor
public class MarketplaceDemoData implements CommandLineRunner {
 private final AccountRepository accounts;private final ShopRepository shops;private final CategoryRepository categories;private final ProductRepository products;private final CarrierRepository carriers;
 private final PasswordEncoder passwordEncoder;
 private final org.springframework.core.env.Environment environment;
 @Value("${DEMO_SEED_ENABLED:false}") private boolean demoSeedEnabled;
 @Value("${app.demo.shipper-password:Password@123}") private String demoShipperPassword;
 @Override @Transactional public void run(String...args){
  boolean productionDemo=environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"));
  if(productionDemo && !demoSeedEnabled)return;
  if(!productionDemo) {
  // Keep a separately hashed shipper demo login even when shops already exist.
  // This fixture ONLY runs under the demo profile; never on production.
  accounts.findByEmail("shipper@utetea.vn").ifPresentOrElse(
   existing -> { if(existing.getRole()!=Role.SHIPPER)throw new IllegalStateException("Demo shipper email is assigned to another role"); },
   () -> accounts.save(Account.builder()
    .email("shipper@utetea.vn")
    .passwordHash(passwordEncoder.encode(demoShipperPassword))
    .fullName("Nguyễn Minh Giao - Nhân viên giao hàng")
    .phone("0901000005")
    .role(Role.SHIPPER).active(true).emailVerified(true).tokenVersion(1L).build())
  );
  }
  if(shops.count()>0)return;
  String hash=productionDemo ? "" : accounts.findByEmail("admin@utetea.vn").orElseThrow().getPasswordHash();
  Account manager=productionDemo ? accounts.findByEmail("dohongnhung@utetea.vn").orElseThrow() : create("manager@utetea.vn",Role.MANAGER,hash);
  String[] locations={"Campus","Làng Đại Học","Thủ Đức"};
  var counterMenu=products.findAllByActiveTrue().stream().filter(p->p.getShop()==null).toList();
  for(int i=0;i<3;i++){
   Account owner=productionDemo ? accounts.findByEmail(new String[]{"dangthanhbinh@utetea.vn","phamgiahan@utetea.vn","truonghieunghia@utetea.vn"}[i]).orElseThrow() : create("vendor"+(i+1)+"@utetea.vn",Role.VENDOR,hash);
   Shop shop=new Shop();shop.setName("UTE Tea "+locations[i]);shop.setAddress((i+1)+" Võ Văn Ngân, TP. Thủ Đức");shop.setArea("Thủ Đức");shop.setOwner(owner);shop.setActive(true);shop.setCommissionPercent(new BigDecimal("5"));if(i==0)shop.getManagers().add(manager);shops.save(shop);
   Category cat=Category.builder().name("Trà nhà "+locations[i]).shop(shop).active(true).allowTopping(true).build();categories.save(cat);
   for(int j=0;j<5;j++)for(String size:new String[]{"M","L"})products.save(Product.builder().shop(shop).category(cat).name(new String[]{"Trà lài","Trà đào","Matcha sữa","Trà sen","Trà ô long"}[j]).size(size).price(BigDecimal.valueOf(25000+j*5000+(size.equals("L")?5000:0))).deliveryStock(50).imageUrl("/images/ute-tea-product-fallback.svg").active(true).build());
   var identities=new java.util.HashSet<>(products.findByShopId(shop.getId()).stream().map(Product::menuVariantIdentity).toList());
   var shopCategories=new java.util.HashMap<Long,Category>();
   for(var source:counterMenu) {
    if(!identities.add(source.menuVariantIdentity()))continue;
    var targetCategory=shopCategories.computeIfAbsent(source.getCategory().getId(),id->categories.save(Category.builder().name(source.getCategory().getName()).shop(shop).active(true).allowTopping(source.getCategory().getAllowTopping()).build()));
    products.save(Product.builder().shop(shop).category(targetCategory).name(source.getName()).size(source.getSize()).menuCode(source.menuIdentity()).price(source.getPrice()).deliveryStock(50).imageUrl(source.getImageUrl()).active(true).build());
   }
  }
  Carrier c=new Carrier();c.setName("UTE Tea giao tiêu chuẩn");c.setFee(new BigDecimal("15000"));carriers.save(c);
 }
 private Account create(String email,Role role,String hash){return accounts.findByEmail(email).orElseGet(()->accounts.save(Account.builder().email(email).passwordHash(hash).fullName(role+" Demo").role(role).build()));}
}
