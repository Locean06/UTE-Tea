package vn.edu.ute.milktea.service.market;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.*;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.entity.account.DeliveryAddress;
import vn.edu.ute.milktea.entity.catalog.Product;
import vn.edu.ute.milktea.entity.market.Shop;
import vn.edu.ute.milktea.repository.account.DeliveryAddressRepository;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;
import vn.edu.ute.milktea.repository.market.*;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.account.CustomerDataService;
import java.math.BigDecimal;
import java.util.*;

/** All branches serve the single canonical menu and share one delivery-stock pool. */
@Service @RequiredArgsConstructor @Transactional
public class BranchSelectionService {
    private final ShopRepository shops;
    private final ProductRepository products;
    private final DeliveryAddressRepository addresses;
    private final ShopCustomerAccessRepository customers;
    private final CustomerDataService customerData;
    private final EntityManager em;

    public record Selection(Product product,int quantity,String note,boolean topping) {}
    public record Plan(Shop shop,DeliveryAddress address,List<Selection> selections,
                       Map<Long,Integer> quantities,BigDecimal subtotal,List<String> issues) {}

    public static boolean validCoordinates(Double latitude,Double longitude) {
        return latitude!=null&&longitude!=null&&Double.isFinite(latitude)&&Double.isFinite(longitude)
                &&latitude>=-90&&latitude<=90&&longitude>=-180&&longitude<=180;
    }
    public static double distanceKm(double lat1,double lon1,double lat2,double lon2) {
        double lat=Math.toRadians(lat2-lat1),lon=Math.toRadians(lon2-lon1);
        double a=Math.pow(Math.sin(lat/2),2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(lon/2),2);
        return 6371.0088*2*Math.asin(Math.sqrt(Math.max(0,Math.min(1,a))));
    }
    private DeliveryAddress address(CurrentActor actor,Long addressId) {
        return addresses.findByIdAndAccountId(addressId,CustomerDataService.accountId(actor))
                .orElseThrow(()->BusinessException.forbidden(ErrorCode.ACCESS_DENIED,"Địa chỉ không thuộc tài khoản của bạn"));
    }
    public List<BuyLine> cartItems(CurrentActor actor) {
        return customerData.cart(actor).getItems().stream().map(l->new BuyLine(l.getProductId(),l.getQuantity(),l.getNote(),l.getToppingIds())).toList();
    }
    @Transactional(readOnly=true)
    public List<BranchOption> recommendations(CurrentActor actor,Long addressId) {
        var address=address(actor,addressId);
        var items=cartItems(actor);
        record Ranked(BranchOption option,int kind,double distance) {}
        List<Ranked> ranked=new ArrayList<>();
        for(var shop:shops.findAll().stream().filter(Shop::isActive).toList()) {
            var plan=resolve(actor,shop.getId(),addressId,items,false);
            boolean coordinates=validCoordinates(address.getLatitude(),address.getLongitude())&&validCoordinates(shop.getLatitude(),shop.getLongitude());
            boolean sameArea=address.getArea()!=null&&!address.getArea().isBlank()
                    &&Product.normalizeSearch(address.getArea()).equals(Product.normalizeSearch(shop.getArea()));
            Double distance=coordinates?distanceKm(address.getLatitude(),address.getLongitude(),shop.getLatitude(),shop.getLongitude()):null;
            int kind=coordinates?0:sameArea?1:2;
            var option=new BranchOption(shop.getId(),shop.getName(),shop.getAddress(),shop.getArea(),distance,
                    coordinates?"COORDINATES":sameArea?"AREA":"UNCONFIGURED",false,plan.issues().isEmpty(),plan.issues(),plan.issues().isEmpty()?plan.subtotal():null);
            ranked.add(new Ranked(option,kind,distance==null?Double.MAX_VALUE:distance));
        }
        ranked.sort(Comparator.comparingInt(Ranked::kind).thenComparingDouble(Ranked::distance)
                .thenComparing(r->r.option().name()).thenComparing(r->r.option().id()));
        List<BranchOption> result=new ArrayList<>();
        for(int i=0;i<ranked.size();i++) {
            var r=ranked.get(i);var o=r.option();
            result.add(new BranchOption(o.id(),o.name(),o.address(),o.area(),o.distanceKm(),o.rankingMethod(),i==0&&r.kind()<2,o.canServe(),o.issues(),o.subtotal()));
        }
        return result;
    }
    public Plan resolve(CurrentActor actor,Long shopId,Long addressId,List<BuyLine> items,boolean lockStock) {
        var address=address(actor,addressId);
        var shop=shops.findById(shopId).filter(Shop::isActive)
                .orElseThrow(()->BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,"Cơ sở đã ngừng phục vụ"));
        if(items==null||items.isEmpty()||items.size()>100)throw invalid("Giỏ cần từ 1 đến 100 dòng món");
        var available=products.findAllByActiveTrue().stream()
                .filter(p->p.getShop()==null).toList();
        Map<String,List<Product>> byCode=new HashMap<>();
        available.forEach(p->byCode.computeIfAbsent(p.menuVariantIdentity(),k->new ArrayList<>()).add(p));
        List<String> issues=new ArrayList<>();List<Selection> selections=new ArrayList<>();Map<Long,Integer> quantities=new TreeMap<>();
        customers.findByShopIdAndAccountId(shopId,CustomerDataService.accountId(actor)).filter(c->c.isBlocked())
                .ifPresent(c->issues.add("Tài khoản bị hạn chế mua tại cơ sở này"));
        for(var item:items) {
            if(item==null||item.quantity()<1||item.quantity()>99||item.note()!=null&&item.note().length()>500)throw invalid("Số lượng hoặc ghi chú không hợp lệ");
            var source=source(item.productId());
            var product=match(source,byCode,issues);
            var ids=item.toppingIds()==null?List.<Long>of():item.toppingIds();
            if(ids.size()>20||new HashSet<>(ids).size()!=ids.size())throw invalid("Topping bị trùng hoặc quá giới hạn");
            if(product!=null) {
                if(!ids.isEmpty()&&!Boolean.TRUE.equals(product.getCategory().getAllowTopping()))issues.add(product.getName()+": cơ sở không hỗ trợ topping cho món này");
                selections.add(new Selection(product,item.quantity(),item.note(),false));quantities.merge(product.getId(),item.quantity(),Integer::sum);
            }
            for(Long toppingId:ids) {
                var toppingSource=source(toppingId);
                if(!isTopping(toppingSource))throw invalid("Món bổ sung không phải topping");
                var topping=match(toppingSource,byCode,issues);
                if(topping!=null) {
                    if(!isTopping(topping))issues.add(topping.getName()+": cấu hình topping không hợp lệ");
                    selections.add(new Selection(topping,item.quantity(),item.note(),true));quantities.merge(topping.getId(),item.quantity(),Integer::sum);
                }
            }
        }
        Map<Long,Product> fresh=new HashMap<>();
        for(var entry:quantities.entrySet()) {
            var p=available.stream().filter(x->x.getId().equals(entry.getKey())).findFirst().orElseThrow();
            // Lock the canonical product row: every branch shares its menu price and available units.
            if(lockStock)em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
            fresh.put(p.getId(),p);
            if(!Boolean.TRUE.equals(p.getActive())||!Boolean.TRUE.equals(p.getCategory().getActive())||p.getDeliveryStock()==null||p.getDeliveryStock()<entry.getValue())
                issues.add(p.getName()+" · "+p.getSize()+": cần "+entry.getValue()+", tồn khả dụng "+(p.getDeliveryStock()==null?0:p.getDeliveryStock()));
        }
        var subtotal=selections.stream().map(s->fresh.get(s.product().getId()).getPrice().multiply(BigDecimal.valueOf(s.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Plan(shop,address,List.copyOf(selections),quantities,subtotal,List.copyOf(issues));
    }
    private Product source(Long id) {if(id==null)throw invalid("Thiếu mã món");return products.findById(id).orElseThrow(()->invalid("Món đã chọn không tồn tại"));}
    private Product match(Product source,Map<String,List<Product>> products,List<String> issues) {
        var matches=products.getOrDefault(source.menuVariantIdentity(),List.of());
        if(matches.size()!=1) {issues.add(source.getName()+" · "+source.getSize()+(matches.isEmpty()?": món không còn trong thực đơn chung":": mã món bị trùng trong thực đơn chung"));return null;}
        return matches.getFirst();
    }
    private boolean isTopping(Product p){return Product.normalizeSearch(p.getCategory().getName()).contains("topping");}
    private BusinessException invalid(String text){return BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,text);}
}
