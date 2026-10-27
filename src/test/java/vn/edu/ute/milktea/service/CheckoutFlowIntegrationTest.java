package vn.edu.ute.milktea.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.dto.*;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.entity.catalog.*;
import vn.edu.ute.milktea.entity.market.*;
import vn.edu.ute.milktea.entity.order.*;
import vn.edu.ute.milktea.entity.payment.*;
import vn.edu.ute.milktea.entity.table.*;
import vn.edu.ute.milktea.repository.account.*;
import vn.edu.ute.milktea.repository.catalog.*;
import vn.edu.ute.milktea.repository.market.*;
import vn.edu.ute.milktea.repository.order.*;
import vn.edu.ute.milktea.repository.payment.*;
import vn.edu.ute.milktea.repository.table.*;
import vn.edu.ute.milktea.repository.settings.GlobalSettingsRepository;
import vn.edu.ute.milktea.security.*;
import vn.edu.ute.milktea.service.order.OrderService;
import vn.edu.ute.milktea.service.payment.PaymentService;
import vn.edu.ute.milktea.service.table.TableContextService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CheckoutFlowIntegrationTest {
    @Autowired OrderService service; @Autowired PaymentService payments;
    @Autowired vn.edu.ute.milktea.service.order.OrderBranchService branchService;
    @Autowired TableContextService contexts;
    @Autowired AccountRepository accounts; @Autowired ShopRepository shops;
    @Autowired ProductRepository products; @Autowired CategoryRepository categories;
    @Autowired OrderRepository orders; @Autowired InvoiceRepository invoices;
    @Autowired PaymentRepository receipts; @Autowired DiningTableRepository tables;
    @Autowired GlobalSettingsRepository settings; @Autowired VoucherRepository vouchers;
    Account buyer,cashier,otherCashier; Shop a,b; Product reference,drink,top,topReference; DiningTable table;
    CurrentActor actor;
    Account account(Role role) {return accounts.save(Account.builder().email(UUID.randomUUID()+"@checkout.test").role(role).passwordHash("test").build());}
    Shop shop(String name) {var s=new Shop();s.setName(name);s.setAddress("Tại quán");s.setOwner(cashier);s.setActive(true);return shops.save(s);}
    Product product(Shop shop,Category category,String name,String code,String price) {
        return products.saveAndFlush(Product.builder().shop(shop).category(category).name(name).menuCode(code).size("M").price(new BigDecimal(price)).active(true).deliveryStock(99).build());
    }
    @BeforeEach void setup() {
        buyer=account(Role.CUSTOMER);cashier=account(Role.CASHIER);otherCashier=account(Role.CASHIER);
        actor=CurrentActor.authenticated(buyer.getId(),Role.CUSTOMER);
        a=shop("A");b=shop("B");
        var cat=categories.save(Category.builder().name("Checkout drinks "+UUID.randomUUID()).active(true).allowTopping(true).build());
        var topping=categories.save(Category.builder().name("Topping "+UUID.randomUUID()).active(true).allowTopping(false).build());
        String code=UUID.randomUUID().toString();reference=product(null,cat,"Reference "+UUID.randomUUID(),code,"10000");drink=product(a,cat,"Trà Sữa Truyền Thống",code,"30000");product(b,cat,"Trà Sữa Truyền Thống",code,"40000");
        String topCode=UUID.randomUUID().toString();topReference=product(null,topping,"Reference "+UUID.randomUUID(),topCode,"1000");top=product(a,topping,"Trân Châu Đen",topCode,"5000");product(b,topping,"Trân Châu Đen",topCode,"7000");
        table=tables.save(DiningTable.builder().shop(a).name("Test "+UUID.randomUUID().toString().substring(0,12)).qrCode("TABLE_QR_"+UUID.randomUUID()).build());
        settings.findById(1).ifPresent(s->{s.setDiscountValue(BigDecimal.ZERO);s.setDiscountPercent(BigDecimal.ZERO);});
    }
    List<CartDto.CartItemRequest> items() {return List.of(CartDto.CartItemRequest.builder().productId(reference.getId()).quantity(2).toppingIds(List.of(topReference.getId())).build());}
    OrderDto.CreateCounterOrderRequest request(PaymentMethod method) {return OrderDto.CreateCounterOrderRequest.builder().shopId(a.getId()).items(items()).paymentMethod(method).build();}
    OrderDto.CreateTableOrderRequest tableRequest(PaymentMethod method) {return OrderDto.CreateTableOrderRequest.builder().tableId(table.getId()).shopId(a.getId()).qrCode(table.getQrCode()).items(items()).paymentMethod(method).build();}
    @ParameterizedTest @EnumSource(PaymentMethod.class)
    void takeawayUsesBranchPricesWithoutAddressOrCarrierAndRemainsUnpaid(PaymentMethod method) {
        var response=service.createCounterOrder(request(method),actor,UUID.randomUUID().toString());
        assertEquals(a.getId(),response.getShopId());assertEquals("TAKEAWAY",response.getFulfillmentMode());
        assertEquals(0,new BigDecimal("70000").compareTo(response.getTotalAmount()));
        assertEquals(method.name(),response.getPaymentRequestMethod());assertFalse(response.isPaid());
        assertEquals("PENDING_PAYMENT",response.getPaymentStatus());assertEquals(BigDecimal.ZERO,response.getSurchargeAmount());
        assertFalse(receipts.existsByInvoiceId(response.getInvoiceId()));
        assertEquals(drink.getId(),response.getItems().getFirst().getProductId());
    }
    @ParameterizedTest @EnumSource(PaymentMethod.class)
    void qrOpensCorrectSessionAndBranchWithoutMarkingPaid(PaymentMethod method) {
        var context=contexts.readByQr(table.getQrCode());assertEquals(a.getId(),context.getShopId());
        assertNull(context.getSessionId());assertNull(table.getActiveSessionId());
        var response=service.createTableOrder(tableRequest(method),actor,UUID.randomUUID().toString());
        assertEquals(table.getId(),response.getTableId());assertEquals(a.getId(),response.getShopId());
        assertEquals("DINE_IN",response.getFulfillmentMode());assertFalse(response.isPaid());
        assertNotNull(response.getSessionId());assertNotNull(response.getGuestCounterToken());
    }
    @Test void forgedBranchAndQrCannotPlaceTableOrders() {
        var input=tableRequest(PaymentMethod.CASH);input.setShopId(b.getId());
        assertThrows(BusinessException.class,()->service.createTableOrder(input,actor,"wrong-branch"));
    }
    @Test void tableNumberOrForgedSessionIsNotAnAccessProof() {
        var input=tableRequest(PaymentMethod.CASH);input.setQrCode(null);input.setExpectedSessionId(123456L);input.setTableToken("made-up");
        assertThrows(BusinessException.class,()->service.createTableOrder(input,actor,"forged-session"));
    }
    @Test void staleSessionIsRejectedEvenWithCorrectQr() {
        var input=tableRequest(PaymentMethod.CASH);input.setExpectedSessionId(123456L);
        assertThrows(BusinessException.class,()->service.createTableOrder(input,actor,"stale-session"));
    }
    @Test void orderReplayDoesNotCreateAnotherInvoiceAndChangedInputIsRejected() {
        var input=request(PaymentMethod.BANK_TRANSFER);String key=UUID.randomUUID().toString();
        var first=service.createCounterOrder(input,actor,key);var again=service.createCounterOrder(input,actor,key);
        assertEquals(first.getOrderId(),again.getOrderId());assertEquals(first.getInvoiceId(),again.getInvoiceId());
        assertEquals(first.getTransferContent(),again.getTransferContent());
        input.setPaymentMethod(PaymentMethod.CASH);
        assertThrows(BusinessException.class,()->service.createCounterOrder(input,actor,key));
    }
    @Test void transferNoticeCannotMarkPaidAndOnlyBranchCashierCanConfirm() {
        var response=service.createCounterOrder(request(PaymentMethod.BANK_TRANSFER),actor,UUID.randomUUID().toString());
        payments.createNoticeForOrder(response.getOrderId(),PaymentDto.PaymentNoticeRequest.builder().method(PaymentMethod.BANK_TRANSFER).reference("CUSTOMER_TRANSFERRED_"+response.getOrderId()).build(),"notice-key");
        assertFalse(service.getOrderForCustomer(response.getOrderId(),actor).isPaid());
        assertThrows(BusinessException.class,()->payments.confirmTransferReceiptForOrder(response.getOrderId(),otherCashier.getId(),"foreign-cashier"));
    }
    @Test void cashierReceiptIsIdempotentAndKitchenReceivesConfirmedUnpaidOrder() {
        var response=service.createCounterOrder(request(PaymentMethod.CASH),actor,UUID.randomUUID().toString());
        service.confirm(response.getOrderId(),cashier.getId());
        assertTrue(service.getOrdersForKitchen().stream().anyMatch(o->o.getOrderId().equals(response.getOrderId())));
        assertFalse(service.getOrderForCustomer(response.getOrderId(),actor).isPaid());
        var request=PaymentDto.RecordPaymentRequest.builder().method(PaymentMethod.CASH).build();
        var first=payments.recordReceiptForOrder(response.getOrderId(),request,cashier.getId(),"cash-key");
        var again=payments.recordReceiptForOrder(response.getOrderId(),request,cashier.getId(),"cash-key");
        assertEquals(first.getPaymentId(),again.getPaymentId());assertTrue(service.getOrderForCustomer(response.getOrderId(),actor).isPaid());
    }
    @Test void quoteRepricesEntireCartForSelectedBranch() {
        var input=request(PaymentMethod.CASH);input.setShopId(b.getId());
        var quote=service.quote(input,actor);
        assertEquals(0,new BigDecimal("94000").compareTo((BigDecimal)quote.get("totalAmount")));
        assertEquals(BigDecimal.ZERO,quote.get("shippingFee"));
    }
    @Test void validVoucherReducesBackendTotalAndIsConsumedOnce() {
        var v=new Voucher();v.setCode(("CHECKOUT"+UUID.randomUUID().toString().substring(0,8)).toUpperCase());v.setShop(a);v.setDiscountAmount(new BigDecimal("10000"));v.setStartsAt(Instant.now().minusSeconds(60));v.setEndsAt(Instant.now().plusSeconds(3600));v.setUsageLimit(10);v.setPerAccountLimit(1);vouchers.save(v);
        var input=request(PaymentMethod.CASH);input.setVoucherCode(v.getCode());
        var quote=service.quote(input,actor);assertEquals(0,v.getUsedCount());
        assertEquals(0,new BigDecimal("60000").compareTo((BigDecimal)quote.get("totalAmount")));
        String key=UUID.randomUUID().toString();var response=service.createCounterOrder(input,actor,key);service.createCounterOrder(input,actor,key);
        assertEquals(0,new BigDecimal("60000").compareTo(response.getTotalAmount()));assertEquals(1,v.getUsedCount());
    }
    @Test void forgedExpectedTotalIsRejected() {
        var input=request(PaymentMethod.CASH);input.setExpectedTotal(BigDecimal.ONE);
        assertThrows(BusinessException.class,()->service.createCounterOrder(input,actor,"forged-total"));
    }
    @Test void existingSessionTokenCanBeReusedOnlyForItsOwnTable() {
        service.createTableOrder(tableRequest(PaymentMethod.CASH),actor,UUID.randomUUID().toString());
        var context=contexts.readByQr(table.getQrCode());
        var input=tableRequest(PaymentMethod.BANK_TRANSFER);input.setQrCode(null);input.setTableToken(context.getTableToken());input.setExpectedSessionId(context.getSessionId());
        var response=service.createTableOrder(input,null,UUID.randomUUID().toString());
        assertEquals(context.getSessionId(),response.getSessionId());assertFalse(response.isPaid());
    }
    @Test void staffBoardHidesAnotherBranchsOrders() {
        var response=service.createCounterOrder(request(PaymentMethod.CASH),actor,UUID.randomUUID().toString());
        var principal=CurrentActor.authenticated(otherCashier.getId(),Role.CASHIER);
        var authentication=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        try {assertTrue(service.getOrdersForCashier(null).stream().noneMatch(o->o.getOrderId().equals(response.getOrderId())));}
        finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void configuredVietQrContainsBackendAmountAndUniqueOrderReference() {
        var bank=settings.findById(1).orElseThrow();bank.setBankName("Test Bank");bank.setBankAccountNumber("1234567890");bank.setBankAccountHolder("TEST ONLY");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"bankBin","970436");
        try {
            var response=service.createCounterOrder(request(PaymentMethod.BANK_TRANSFER),actor,UUID.randomUUID().toString());
            assertTrue(response.getVietQrUrl().contains("amount=70000"));
            assertTrue(response.getVietQrUrl().contains("addInfo=UTETEA+ORD"+response.getOrderId()));assertFalse(response.isPaid());
        } finally {org.springframework.test.util.ReflectionTestUtils.setField(service,"bankBin","");}
    }

    @Test void customerCanQuoteAndOrderWithoutChoosingModeOrBranch() {
        org.springframework.test.util.ReflectionTestUtils.setField(branchService,"posShopId",a.getId());
        try {
            var input=request(PaymentMethod.CASH);input.setShopId(null);
            var quote=service.quote(input,actor);assertEquals(a.getId(),quote.get("shopId"));
            var response=service.createCounterOrder(input,actor,UUID.randomUUID().toString());
            assertEquals(a.getId(),response.getShopId());assertEquals("TAKEAWAY",response.getFulfillmentMode());assertFalse(response.isPaid());
        } finally {org.springframework.test.util.ReflectionTestUtils.setField(branchService,"posShopId",0L);}
    }

}
