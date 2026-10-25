package vn.edu.ute.milktea.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.ute.milktea.entity.market.DeliveryOrder;
import vn.edu.ute.milktea.repository.market.DeliveryOrderRepository;
import vn.edu.ute.milktea.service.market.VnpayService;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VnpayVerificationTest {
 @Mock DeliveryOrderRepository orders;
 @InjectMocks VnpayService gateway;
 Map<String,String> fields(){return new HashMap<>(Map.of("vnp_TmnCode","TEST1234","vnp_Amount","6500000","vnp_TxnRef","7","vnp_ResponseCode","00","vnp_TransactionStatus","00","vnp_TransactionNo","12345678"));}
 @BeforeEach void setup(){ReflectionTestUtils.setField(gateway,"tmnCode","TEST1234");ReflectionTestUtils.setField(gateway,"secret","unit-test-secret-123456789");}
 DeliveryOrder order(){var o=new DeliveryOrder();o.setId(7L);o.setTotalAmount(new BigDecimal("65000"));o.setPaymentMethod(DeliveryOrder.Method.VNPAY);return o;}
 @Test void validatesIndependentHmacVectorAndAcknowledgesReplay(){
  // Fixed vector independently generated with Python hmac/urllib, not the service signer.
  var f=fields();f.put("vnp_SecureHash","15724a38a21ceff4b35e0646d9020e74efe6c30b28da8b560aea627a3ace0ee53aff1977064f835a5eb126f59fda5919603c24b24ea7f0df585c15e236889e6b");
  var o=order();when(orders.lockId(7L)).thenReturn(Optional.of(o));
  assertEquals("00",gateway.ipn(f).get("RspCode"));assertTrue(o.isPaid());assertEquals("12345678",o.getGatewayReference());
  assertEquals("02",gateway.ipn(f).get("RspCode"));verify(orders,times(1)).flush();
 }
 @Test void rejectsForgedOrTamperedCallbackBeforeDatabaseAccess(){
  var f=fields();f.put("vnp_SecureHash","0".repeat(128));assertEquals("97",gateway.ipn(f).get("RspCode"));verifyNoInteractions(orders);
 }
 @Test void correctSignatureCannotBypassAmountCheck(){
  var f=fields();f.put("vnp_Amount","100");f.put("vnp_SecureHash",gateway.sign(f));var o=order();when(orders.lockId(7L)).thenReturn(Optional.of(o));
  assertEquals("04",gateway.ipn(f).get("RspCode"));assertFalse(o.isPaid());verify(orders,never()).flush();
 }
 @Test void unsuccessfulGatewayStatusDoesNotMarkPaid(){
  var f=fields();f.put("vnp_TransactionStatus","02");f.put("vnp_SecureHash",gateway.sign(f));var o=order();when(orders.lockId(7L)).thenReturn(Optional.of(o));
  assertEquals("00",gateway.ipn(f).get("RspCode"));assertFalse(o.isPaid());verify(orders,never()).flush();
 }
 @Test void signedWrongMerchantIsRejected(){
  var f=fields();f.put("vnp_TmnCode","ANOTHER");f.put("vnp_SecureHash",gateway.sign(f));assertEquals("97",gateway.ipn(f).get("RspCode"));verifyNoInteractions(orders);
 }
}
