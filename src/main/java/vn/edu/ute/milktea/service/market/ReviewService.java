package vn.edu.ute.milktea.service.market;
import com.cloudinary.Cloudinary;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ute.milktea.common.*;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.entity.market.*;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;
import vn.edu.ute.milktea.repository.market.*;
import vn.edu.ute.milktea.repository.order.OrderRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.account.CustomerDataService;
import vn.edu.ute.milktea.service.media.ProductImageService;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class ReviewService {
 private final AccountRepository accounts;
 private final ProductRepository products;
 private final ProductReviewRepository reviews;
 private final DeliveryOrderRepository deliveries;
 private final OrderRepository counterOrders;
 private final EntityManager em;
 private final Cloudinary cloudinary;
 private final ProductImageService images;
 private BusinessException invalid(String m){return BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,m);}
 public ReviewView create(CurrentActor actor,Long productId,ReviewInput input){
  Long accountId=CustomerDataService.accountId(actor);
  var account=accounts.findByIdWithLock(accountId).orElseThrow(()->invalid("Không có tài khoản"));
  if(input.comment().trim().length()<50)throw invalid("Bình luận tối thiểu 50 ký tự");
  if(reviews.existsByAccountIdAndProductId(accountId,productId))throw BusinessException.conflict(ErrorCode.VALIDATION_FAILED,"Bạn đã đánh giá món này");
  var product=products.findById(productId).orElseThrow(()->invalid("Món không tồn tại"));
  ProductReview r=new ProductReview();r.setAccount(account);r.setProduct(product);r.setStars(input.stars());r.setComment(input.comment().trim());
  if(input.delivery()){
   var order=deliveries.findById(input.orderId()).orElseThrow(()->invalid("Không có đơn hàng"));
   if(!order.getAccount().getId().equals(accountId)||order.getStatus()!=DeliveryOrder.Status.DELIVERED||!order.isPaid()||order.getLines().stream().noneMatch(l->l.getProduct().getId().equals(productId)))throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED,"Chỉ đánh giá món đã mua và nhận thành công");
   r.setDeliveryOrder(order);
  }else{
   long eligible=em.createQuery("select count(i) from OrderItem i, Payment pay where i.order.id=:orderId and i.product.id=:productId and i.order.account.id=:accountId and i.order.status=vn.edu.ute.milktea.entity.order.OrderStatus.HOAN_THANH and pay.invoice.order=i.order and pay.invoice.status=vn.edu.ute.milktea.entity.order.InvoiceStatus.HIEU_LUC and pay.amount>=pay.invoice.totalAmount",Long.class)
    .setParameter("orderId",input.orderId()).setParameter("productId",productId).setParameter("accountId",accountId).getSingleResult();
   if(eligible==0)throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED,"Chỉ đánh giá món đã mua, hoàn thành và thanh toán");
   r.setCounterOrder(counterOrders.findById(input.orderId()).orElseThrow(()->invalid("Không có đơn hàng")));
  }
  return view(reviews.save(r));
 }
 @Transactional(readOnly=true) public List<ReviewView> list(Long productId,int page){
  if(page<0||page>100000)throw invalid("Trang không hợp lệ");
  return em.createQuery("select r from ProductReview r where r.product.id=:id order by r.id desc",ProductReview.class).setParameter("id",productId).setFirstResult(page*20).setMaxResults(20).getResultList().stream().map(this::view).toList();
 }
 public ReviewView media(CurrentActor actor,Long reviewId,MultipartFile file){
  Long accountId=CustomerDataService.accountId(actor);accounts.findByIdWithLock(accountId).orElseThrow(()->invalid("Không có tài khoản"));
  var review=reviews.findById(reviewId).orElseThrow(()->invalid("Không có đánh giá"));
  if(!review.getAccount().getId().equals(accountId))throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED,"Không có quyền sửa đánh giá");
  if(review.getMedia().size()>=5)throw invalid("Mỗi đánh giá tối đa 5 ảnh/video");
  if(file==null||file.isEmpty()||file.getSize()>20*1024*1024)throw invalid("Ảnh/video không hợp lệ hoặc vượt 20MB");
  if(!images.isCloudinaryConfigured())throw invalid("Chưa cấu hình Cloudinary để tải ảnh/video");
  try{
   byte[] bytes=file.getBytes();String mime=Objects.toString(file.getContentType(),"");String type;
   boolean jpeg=bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
   boolean png=bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
   boolean webp=bytes.length>=12&&new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
   boolean mp4=bytes.length>=12&&new String(bytes,4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp");
   if((jpeg&&mime.equals("image/jpeg"))||(png&&mime.equals("image/png"))||(webp&&mime.equals("image/webp"))){type="image";if(bytes.length>5*1024*1024)throw invalid("Ảnh tối đa 5MB");}
   else if(mp4&&mime.equals("video/mp4"))type="video";else throw invalid("Chỉ nhận JPEG, PNG, WebP hoặc MP4 có nội dung hợp lệ");
   String publicId="ute-tea/reviews/"+UUID.randomUUID();
   var uploaded=cloudinary.uploader().upload(bytes,Map.of("resource_type",type,"public_id",publicId,"overwrite",false));
   TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{cloudinary.uploader().destroy(publicId,Map.of("resource_type",type));}catch(Exception ignored){}}});
   var media=new ReviewMedia();media.setReview(review);media.setMediaType(type);media.setPublicId(publicId);media.setUrl((String)uploaded.get("secure_url"));review.getMedia().add(media);reviews.saveAndFlush(review);return view(review);
  }catch(BusinessException e){throw e;}catch(Exception e){throw BusinessException.badRequest(ErrorCode.IMAGE_UPLOAD_FAILED,"Không thể tải ảnh/video. Vui lòng thử lại.");}
 }
 private ReviewView view(ProductReview r){return new ReviewView(r.getId(),r.getAccount().getId(),r.getStars(),r.getComment(),r.getCreatedAt(),r.getMedia().stream().map(m->new MediaView(m.getId(),m.getMediaType(),m.getUrl())).toList());}
}
