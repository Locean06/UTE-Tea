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
  String from=" from Product p left join p.shop s";
  var query=em.createQuery("select p"+from+where+" order by "+order,Product.class);
  var count=em.createQuery("select count(p)"+from+where,Long.class);
  params.forEach((key,value)->{query.setParameter(key,value);count.setParameter(key,value);});
  var items=query.setFirstResult(page*size).setMaxResults(size).getResultList().stream().map(market::productView).toList();
  return new CatalogPage(items,count.getSingleResult(),page,size);
 }
}
