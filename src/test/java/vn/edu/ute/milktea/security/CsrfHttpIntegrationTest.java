package vn.edu.ute.milktea.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.net.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("test")
class CsrfHttpIntegrationTest {
 @Value("${local.server.port}") int port;
 @Autowired ObjectMapper mapper;
 URI uri(String path){return URI.create("http://localhost:"+port+path);}
 HttpResponse<String> get(HttpClient c,String path)throws Exception{return c.send(HttpRequest.newBuilder(uri(path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
 HttpResponse<String> send(HttpClient c,String path,String method,String body,Map<String,String> headers)throws Exception{
  var request=HttpRequest.newBuilder(uri(path)).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));headers.forEach(request::header);return c.send(request.build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void authenticatedReadsMustNotInvalidateCsrfForNextCartMutation()throws Exception{
  var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var client=HttpClient.newBuilder().cookieHandler(cookies).build();
  var token=mapper.readTree(get(client,"/api/v1/auth/csrf").body()).path("data");
  var login=send(client,"/api/v1/auth/login","POST","{\"email\":\"customer@milktea.vn\",\"password\":\"Password@123\"}",Map.of(token.path("headerName").asText(),token.path("token").asText()));
  assertEquals(200,login.statusCode());
  token=mapper.readTree(get(client,"/api/v1/auth/csrf").body()).path("data");
  assertEquals(200,get(client,"/api/v1/auth/me").statusCode());
  var cart=mapper.readTree(get(client,"/api/v1/account/cart").body()).path("data");
  var updated=send(client,"/api/v1/account/cart","PUT","{\"items\":[]}",Map.of(token.path("headerName").asText(),token.path("token").asText(),"If-Match",cart.path("version").asText()));
  assertEquals(200,updated.statusCode(),updated.body());
 }
 @Test void loginStillRejectsMissingCsrf()throws Exception{
  assertEquals(403,send(HttpClient.newHttpClient(),"/api/v1/auth/login","POST","{\"email\":\"customer@milktea.vn\",\"password\":\"Password@123\"}",Map.of()).statusCode());
 }
}
