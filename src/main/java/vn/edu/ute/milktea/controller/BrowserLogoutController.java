package vn.edu.ute.milktea.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.security.JwtAuthenticationFilter;
import vn.edu.ute.milktea.service.auth.AuthService;

/**
 * Browser logout: standard HTML form POST with a per-request CSRF token.
 * No dependency on JS/third-party CDN, and no redirect if security rejects the POST.
 * Keep API /api/v1/auth/logout for programmatic clients.
 */
@Controller
@RequiredArgsConstructor
public class BrowserLogoutController {

    private final AuthService authService;
    private final CookieCsrfTokenRepository csrfTokens;

    @Value("${app.jwt.cookie-secure:false}")
    private boolean secureCookie;

    @PostMapping("/signout")
    public String signout(@AuthenticationPrincipal CurrentActor actor,
                          HttpServletRequest request,
                          HttpServletResponse response) {
        // Invalidate already issued JWTs via the server-side tokenVersion.
        if (actor != null && actor.getAccountId() != null) {
            authService.logout(actor.getAccountId());
        }

        // Revoke the browser cookies with matching path and flags.
        csrfTokens.saveToken(null, request, response);
        Cookie expired = new Cookie(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, "");
        expired.setPath("/");
        expired.setMaxAge(0);
        expired.setHttpOnly(true);
        expired.setSecure(secureCookie);
        expired.setAttribute("SameSite", "Lax");
        response.addCookie(expired);
        response.setHeader("Cache-Control", "no-store, max-age=0");
        return "redirect:/login?loggedOut";
    }
}
