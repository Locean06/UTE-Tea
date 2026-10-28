package vn.edu.ute.milktea.service.market;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.*;
import vn.edu.ute.milktea.entity.market.DeliveryOrder;
import vn.edu.ute.milktea.repository.market.DeliveryOrderRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.account.CustomerDataService;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class VnpayService {
 private final DeliveryOrderRepository orders;
 @Value("${app.vnpay.tmn-code:}") private String tmnCode;
 @Value("${app.vnpay.hash-secret:}") private String secret;
 @Value("${app.vnpay.return-url:http://localhost:8080/marketplace}") private String returnUrl;
 private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
 public boolean configured(){return tmnCode!=null&&!tmnCode.isBlank()&&secret!=null&&secret.length()>=16;}
 public void requireConfigured(){if(!configured())throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,"VNPAY sandbox chưa được cấu hình mã merchant và khóa ký. Bạn có thể chọn COD.");}
 @Transactional(readOnly=true) public String paymentUrl(CurrentActor actor,Long id,String ip){
  requireConfigured();Long accountId=CustomerDataService.accountId(actor);
  var o=orders.findById(id).orElseThrow(()->BusinessException.notFound(ErrorCode.ORDER_STATE_CONFLICT,"Không có đơn"));
  if(!o.getAccount().getId().equals(accountId))throw BusinessException.forbidden(ErrorCode.ACCESS_DENIED,"Không có quyền thanh toán đơn này");
  if(o.getPaymentMethod()!=DeliveryOrder.Method.VNPAY||o.isPaid()||!Set.of(DeliveryOrder.Status.NEW,DeliveryOrder.Status.CONFIRMED).contains(o.getStatus()))throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,"Đơn không còn chờ VNPAY");
  Instant expiry=o.getCreatedAt().plusSeconds(900);if(!Instant.now().isBefore(expiry))throw BusinessException.conflict(ErrorCode.ORDER_STATE_CONFLICT,"Đã hết 15 phút thanh toán. Hủy đơn và tạo lại để tiếp tục.");
  Map<String,String> fields=new TreeMap<>();fields.put("vnp_Version","2.1.0");fields.put("vnp_Command","pay");fields.put("vnp_TmnCode",tmnCode);fields.put("vnp_Amount",o.getTotalAmount().movePointRight(2).toBigIntegerExact().toString());fields.put("vnp_CurrCode","VND");fields.put("vnp_TxnRef",o.getId().toString());fields.put("vnp_OrderInfo","UTE Tea delivery order "+o.getId());fields.put("vnp_OrderType","other");fields.put("vnp_Locale","vn");fields.put("vnp_ReturnUrl",returnUrl);fields.put("vnp_IpAddr",ip);fields.put("vnp_CreateDate",DATE.format(o.getCreatedAt()));fields.put("vnp_ExpireDate",DATE.format(expiry));
  return "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"+encode(fields)+"&vnp_SecureHash="+sign(fields);
 }
 public String sign(Map<String,String> fields){
  try{Mac hmac=Mac.getInstance("HmacSHA512");hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA512"));return HexFormat.of().formatHex(hmac.doFinal(encode(fields).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Không thể ký VNPAY",e);}
 }
 private String encode(Map<String,String> fields){return new TreeMap<>(fields).entrySet().stream().filter(e->e.getValue()!=null&&!e.getValue().isEmpty()).map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
 @Transactional public Map<String,String> ipn(Map<String,String> input){
  if(!configured())return reply("99","Gateway not configured");
  Map<String,String> fields=new TreeMap<>();input.forEach((k,v)->{if(k.startsWith("vnp_")&&!k.equals("vnp_SecureHash")&&!k.equals("vnp_SecureHashType"))fields.put(k,v);});
  String actual=Objects.toString(input.get("vnp_SecureHash"),"");
  if(!actual.matches("[0-9a-fA-F]{128}")||!MessageDigest.isEqual(sign(fields).getBytes(StandardCharsets.US_ASCII),actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))return reply("97","Invalid signature");
  if(!tmnCode.equals(fields.get("vnp_TmnCode")))return reply("97","Invalid merchant");
  String ref=fields.get("vnp_TxnRef");if(ref==null||!ref.matches("[0-9]{1,18}"))return reply("01","Order not found");
  var optional=orders.lockId(Long.valueOf(ref));if(optional.isEmpty())return reply("01","Order not found");var o=optional.get();
  String amount=fields.get("vnp_Amount");
  if(amount==null||!amount.matches("[0-9]{1,20}")||!o.getTotalAmount().movePointRight(2).toBigIntegerExact().equals(new java.math.BigInteger(amount)))return reply("04","Invalid amount");
  if(o.getPaymentMethod()!=DeliveryOrder.Method.VNPAY)return reply("99","Invalid payment method");
  if(o.isPaid())return reply("02","Order already confirmed");
  if(!"00".equals(fields.get("vnp_ResponseCode"))||!"00".equals(fields.get("vnp_TransactionStatus")))return reply("00","Unsuccessful payment acknowledged");
  String transaction=fields.get("vnp_TransactionNo");if(transaction==null||!transaction.matches("[0-9]{1,15}")||orders.existsByGatewayReference(transaction))return reply("99","Invalid transaction reference");
  // A verified late payment is still money received, even if the user cancelled.
  // Keep order status unchanged; expose paid cancelled orders for manual gateway refund.
  o.setPaid(true);o.setGatewayReference(transaction);orders.flush();return reply("00","Confirm Success");
 }
 private Map<String,String> reply(String code,String message){return Map.of("RspCode",code,"Message",message);}
}
