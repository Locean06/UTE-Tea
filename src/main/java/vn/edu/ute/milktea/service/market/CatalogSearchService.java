package vn.edu.ute.milktea.service.market;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.*;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.entity.catalog.Product;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class CatalogSearchService {
 private final EntityManager em;
 private final MarketService market;
 private final vn.edu.ute.milktea.repository.catalog.ProductRepository products;
 @Transactional(readOnly=true) public MenuOptions options(Long id){
  var source=products.findById(id).orElseThrow(()->BusinessException.notFound(ErrorCode.PRODUCT_UNAVAILABLE,"Món không tồn tại"));
  Map<String,Product> unique=new LinkedHashMap<>();products.findAllByActiveTrue().stream().sorted(Comparator.comparing(Product::getId)).forEach(p->unique.putIfAbsent(p.menuVariantIdentity(),p));
  var selected=unique.get(source.menuVariantIdentity());if(selected==null)throw BusinessException.badRequest(ErrorCode.PRODUCT_UNAVAILABLE,"Món đã ngừng bán");
  var variants=unique.values().stream().filter(p->Product.normalizeSearch(p.getName()).equals(Product.normalizeSearch(selected.getName())))
    .sorted(Comparator.comparing(Product::getPrice)).map(market::productView).toList();
  var toppings=unique.values().stream().filter(p->Product.normalizeSearch(p.getCategory().getName()).contains("topping")).map(market::productView).toList();
  boolean allowTopping=products.findAllByActiveTrue().stream().anyMatch(p->p.menuVariantIdentity().equals(selected.menuVariantIdentity())&&Boolean.TRUE.equals(p.getCategory().getAllowTopping()));
  return new MenuOptions(market.productView(selected),variants,toppings,allowTopping&&!Product.normalizeSearch(selected.getCategory().getName()).contains("topping"));
 }
 @Transactional(readOnly=true)
 public CatalogPage search(String term,Long category,Long shop,BigDecimal min,BigDecimal max,String sort,int page,int size){
  if(page<0||page>100000||size<1||size>100||(min!=null&&min.signum()<0)||(max!=null&&max.signum()<0)||(min!=null&&max!=null&&min.compareTo(max)>0)||term!=null&&term.length()>120)throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,"Bộ lọc hoặc phân trang không hợp lệ");
  String where=" where p.active=true and p.category.active=true and (p.shop is null or p.shop.active=true)";
  Map<String,Object> params=new HashMap<>();
  if(term!=null&&!term.isBlank()){where+=" and coalesce(p.searchName,lower(p.name)) like :term escape '!'";params.put("term","%"+Product.normalizeSearch(term).replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
  if(category!=null){where+=" and p.category.id=:category";params.put("category",category);}
  if(shop!=null){where+=" and p.shop.id=:shop";params.put("shop",shop);}
  if(min!=null){where+=" and p.price>=:min";params.put("min",min);}
  if(max!=null){where+=" and p.price<=:max";params.put("max",max);}
  String order=switch(sort==null?"newest":sort){
   case "newest"->"p.id desc";
   case "price_asc"->"p.price asc, p.id desc";
   case "price_desc"->"p.price desc, p.id desc";
   case "favorites"->"(select count(i) from ProductInterest i where i.product=p and i.favorite=true) desc, p.id desc";
   case "rating"->"(select coalesce(avg(r.stars),0) from ProductReview r where r.product=p) desc, p.id desc";
   case "bestselling"->"((select coalesce(sum(l.quantity),0) from DeliveryLine l where l.product=p and l.order.status=vn.edu.ute.milktea.entity.market.DeliveryOrder.Status.DELIVERED)+(select coalesce(sum(i.quantity),0) from OrderItem i where i.product=p and i.order.status=vn.edu.ute.milktea.entity.order.OrderStatus.HOAN_THANH)) desc, p.id desc";
   default->throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,"Tiêu chí sắp xếp không hợp lệ");
  };
  // Use a left join: implicit paths through optional shop must not drop legacy POS products.
  where=where.replace("p.shop.active", "s.active");
  String otherWhere=where.replaceAll("\\bp\\.","p2.").replaceAll("\\bs\\.","s2.");
  where+=" and not exists (select p2.id from Product p2 left join p2.shop s2"+otherWhere
    +" and coalesce(p2.menuCode,concat(coalesce(p2.searchName,lower(p2.name)),'|',lower(p2.size)))=coalesce(p.menuCode,concat(coalesce(p.searchName,lower(p.name)),'|',lower(p.size))) and lower(p2.size)=lower(p.size) and p2.id<p.id)";
  String from=" from Product p left join p.shop s";
  var query=em.createQuery("select p"+from+where+" order by "+order,Product.class);
  var count=em.createQuery("select count(p)"+from+where,Long.class);
  params.forEach((key,value)->{query.setParameter(key,value);count.setParameter(key,value);});
  var items=query.setFirstResult(page*size).setMaxResults(size).getResultList().stream().map(market::productView).toList();
  return new CatalogPage(items,count.getSingleResult(),page,size);
 }
}
