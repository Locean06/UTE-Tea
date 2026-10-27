package vn.edu.ute.milktea.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.ute.milktea.entity.catalog.*;
import vn.edu.ute.milktea.entity.market.Shop;
import vn.edu.ute.milktea.entity.table.DiningTable;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import vn.edu.ute.milktea.repository.catalog.*;
import vn.edu.ute.milktea.repository.market.ShopRepository;
import vn.edu.ute.milktea.repository.table.DiningTableRepository;
import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("test")
class CheckoutHttpIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper mapper; @Autowired AccountRepository accounts; @Autowired ShopRepository shops;
    @Autowired ProductRepository products; @Autowired CategoryRepository categories; @Autowired DiningTableRepository tables;
    HttpClient client;Map<String,String> csrf;
    HttpResponse<String> get(String path)throws Exception {return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    HttpResponse<String> post(String path,Object data,boolean protectedRequest,String key)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(protectedRequest)csrf.forEach(request::header);if(key!=null)request.header("Idempotency-Key",key);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
    }
    void setup(boolean login)throws Exception {
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();updateCsrf();
        if(login){assertEquals(200,post("/api/v1/auth/login",Map.of("email","customer@milktea.vn","password","Password@123"),true,null).statusCode());updateCsrf();}
    }
    void updateCsrf()throws Exception {var data=mapper.readTree(get("/api/v1/auth/csrf").body()).path("data");csrf=Map.of(data.path("headerName").asText(),data.path("token").asText());}
    @ParameterizedTest @CsvSource({"TAKEAWAY,CASH","TAKEAWAY,BANK_TRANSFER","DINE_IN,CASH","DINE_IN,BANK_TRANSFER"})
    void checkoutThroughSecurityAndControllersPreservesBranchAndPaymentState(String mode,String method)throws Exception {
        setup(true);
        var shop=new Shop();shop.setName("HTTP branch");shop.setAddress("Tại quán");shop.setActive(true);shop.setOwner(accounts.findByEmail("admin@milktea.vn").orElseThrow());shop=shops.save(shop);
        var cat=categories.save(Category.builder().name("HTTP "+UUID.randomUUID()).active(true).allowTopping(false).build());
        var product=products.saveAndFlush(Product.builder().shop(shop).category(cat).name("Trà Sữa Truyền Thống").size("M").price(new BigDecimal("30000")).active(true).build());
        Map<String,Object> body=new HashMap<>();body.put("shopId",shop.getId());body.put("paymentMethod",method);body.put("items",List.of(Map.of("productId",product.getId(),"quantity",1)));
        String path="/api/v1/orders/counter";
        if(mode.equals("DINE_IN")){
            var table=tables.save(DiningTable.builder().shop(shop).name("HTTP "+UUID.randomUUID().toString().substring(0,8)).qrCode("TABLE_QR_"+UUID.randomUUID()).build());
            assertEquals(200,get("/api/v1/public/table-context?qrCode="+table.getQrCode()).statusCode());
            body.put("tableId",table.getId());body.put("qrCode",table.getQrCode());path="/api/v1/orders/table";
        }
        String key=UUID.randomUUID().toString();var created=post(path,body,true,key);assertEquals(201,created.statusCode(),created.body());
        var order=mapper.readTree(created.body()).path("data");long id=order.path("orderId").asLong();
        assertEquals(shop.getId(),order.path("shopId").asLong());assertEquals(mode,order.path("fulfillmentMode").asText());assertEquals(method,order.path("paymentRequestMethod").asText());assertFalse(order.path("paid").asBoolean());
        var replay=post(path,body,true,key);assertEquals(201,replay.statusCode(),replay.body());assertEquals(id,mapper.readTree(replay.body()).path("data").path("orderId").asLong());
        assertEquals(403,post("/api/v1/cashier/orders/"+id+"/payments",Map.of("method",method),true,"unauthorized-payment").statusCode());
        if(method.equals("BANK_TRANSFER"))assertEquals(200,post("/api/v1/orders/"+id+"/payment-notices",Map.of("method",method,"reference","CUSTOMER_TRANSFERRED_"+id),true,"notice-"+id).statusCode());
        assertFalse(mapper.readTree(get("/api/v1/orders/"+id).body()).path("data").path("paid").asBoolean());
    }
    @Test void publicOtpEndpointsRequireCsrfAndValidateBodies()throws Exception {
        setup(false);
        assertEquals(403,post("/api/v1/auth/register",Map.of(),false,null).statusCode());
        for(String path:List.of("register","verify-email","resend-otp"))assertEquals(400,post("/api/v1/auth/"+path,Map.of(),true,null).statusCode());
        assertEquals(200,get("/api/v1/public/checkout/branches").statusCode());
        assertEquals(403,get("/api/v1/public/table-context?table=1").statusCode());
    }
    @Test void missingCheckoutApiReturns404InsteadOfMisleadingSystemFailure()throws Exception {
        setup(false);
        var response=post("/api/v1/public/checkout/unknown",Map.of(),true,null);
        assertEquals(404,response.statusCode(),response.body());
        assertEquals("RESOURCE_NOT_FOUND",mapper.readTree(response.body()).path("error").path("code").asText());
    }

}
