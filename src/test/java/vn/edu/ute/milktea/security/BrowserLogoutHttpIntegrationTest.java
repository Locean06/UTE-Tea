package vn.edu.ute.milktea.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Browser form logout must invalidate the JWT and remove its HttpOnly cookie. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BrowserLogoutHttpIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper mapper;

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(url(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void logoutFormClearsCookieAndInvalidatesOldToken() throws Exception {
        var manager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(manager)
                .followRedirects(HttpClient.Redirect.NEVER).build();

        JsonNode csrf = mapper.readTree(get(client, "/api/v1/auth/csrf").body()).path("data");
        var login = client.send(HttpRequest.newBuilder(url("/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"cashier@utetea.vn\",\"password\":\"Password@123\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());

        String issuedToken = manager.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE))
                .findFirst().map(HttpCookie::getValue).orElseThrow();

        var cashier = get(client, "/cashier");
        assertEquals(200, cashier.statusCode(), cashier.body());
        assertTrue(cashier.body().contains("action=\"/signout\""),
                "Cashier navbar must show a native POST logout form");
        assertTrue(cashier.body().contains("name=\"_csrf\""),
                "Logout form must contain Spring Security CSRF token");

        csrf = mapper.readTree(get(client, "/api/v1/auth/csrf").body()).path("data");
        String form = "_csrf=" + URLEncoder.encode(csrf.path("token").asText(), StandardCharsets.UTF_8);
        var logout = client.send(HttpRequest.newBuilder(url("/signout"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, logout.statusCode(), logout.body());
        assertTrue(logout.headers().firstValue("location").orElse("").contains("/login"));
        assertTrue(manager.getCookieStore().getCookies().stream()
                        .noneMatch(c -> c.getName().equals(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE)
                                && !c.getValue().isBlank()),
                "Browser must remove JWT cookie after logout");
        assertEquals(401, get(client, "/api/v1/auth/me").statusCode());

        // Stolen/replayed pre-logout JWT must also be invalid after tokenVersion increment.
        var oldJwt = HttpClient.newHttpClient().send(HttpRequest.newBuilder(url("/api/v1/auth/me"))
                        .header("Cookie", JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE + "=" + issuedToken)
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, oldJwt.statusCode());
    }

    @Test
    void logoutMustNotAcceptPostWithoutCsrf() throws Exception {
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        var response = client.send(HttpRequest.newBuilder(url("/signout"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }
}
