package vn.edu.ute.milktea.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.ute.milktea.entity.catalog.Category;
import vn.edu.ute.milktea.entity.catalog.Product;
import vn.edu.ute.milktea.entity.settings.GlobalSettings;
import vn.edu.ute.milktea.entity.table.DiningTable;
import vn.edu.ute.milktea.repository.catalog.CategoryRepository;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;
import vn.edu.ute.milktea.repository.settings.GlobalSettingsRepository;
import vn.edu.ute.milktea.security.CurrentActor;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class CustomerPageController {

    private final GlobalSettingsRepository settingsRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // Table service is POS-only. Public ordering intentionally does not load a table from URL/cookie.

    @GetMapping({"/", "/menu"})
    public String menuPage(
            @RequestParam(value = "qr", required = false) String qrCode,
            @RequestParam(value = "table", required = false) String tableParam,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            Model model) {

        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        List<Category> categories = categoryRepository.findAllByActiveTrue();
        List<Product> products = productRepository.findAllByActiveTrue();

        DiningTable table = null; // Dine-in tables are assigned at the authenticated POS.

        model.addAttribute("settings", settings);
        model.addAttribute("categories", categories);
        model.addAttribute("products", products);
        model.addAttribute("currentTable", table);
        model.addAttribute("tableQr", null);

        return "customer/menu";
    }

    @GetMapping("/cart")
    public String cartPage(
            @RequestParam(value = "qr", required = false) String qrCode,
            @RequestParam(value = "table", required = false) String tableParam,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            @AuthenticationPrincipal CurrentActor actor, Model model) {

        if (actor != null && actor.hasRole(vn.edu.ute.milktea.entity.account.Role.CASHIER)) return "redirect:/cashier";
        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        DiningTable table = null; // Dine-in tables are assigned at the authenticated POS.

        model.addAttribute("settings", settings);
        model.addAttribute("currentTable", table);
        model.addAttribute("tableQr", null);

        return "customer/cart";
    }

    @GetMapping("/orders")
    public String ordersPage(
            @RequestParam(value = "qr", required = false) String qrCode,
            @RequestParam(value = "table", required = false) String tableParam,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            @AuthenticationPrincipal CurrentActor actor,
            Model model) {

        // A table QR / guest-session token must never unlock a list of past orders.
        // Only a real signed-in account may open the order-history page.
        if (actor == null || !actor.isAuthenticated()) {
            return "redirect:/login?redirect=/orders";
        }
        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        DiningTable table = null; // Dine-in tables are assigned at the authenticated POS.

        model.addAttribute("settings", settings);
        model.addAttribute("actor", actor);
        model.addAttribute("currentTable", table);
        model.addAttribute("tableQr", null);

        return "customer/orders";
    }

    @GetMapping("/order-confirmation")
    public String orderConfirmationPage(@RequestParam(value = "order", required = false) Long orderId,
                                        Model model) {
        model.addAttribute("confirmationOrderId", orderId);
        return "customer/order-confirmation";
    }

    @GetMapping("/profile")
    public String profilePage(
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            jakarta.servlet.http.HttpServletRequest request,
            @AuthenticationPrincipal CurrentActor actor,
            Model model) {
        if (actor == null || actor.getAccountId() == null) {
            return "redirect:/login?redirect=/profile";
        }

        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        model.addAttribute("settings", settings);
        model.addAttribute("actor", actor);

        String referer = request.getHeader("Referer");
        String safeBackUrl = resolveSafeBackUrl(returnUrl, referer, actor);
        model.addAttribute("backUrl", safeBackUrl);

        return "profile";
    }

    private String resolveSafeBackUrl(String returnUrl, String referer, CurrentActor actor) {
        String candidate = null;
        if (returnUrl != null && !returnUrl.isBlank()) {
            candidate = returnUrl.trim();
        } else if (referer != null && !referer.isBlank()) {
            try {
                java.net.URI uri = java.net.URI.create(referer.trim());
                String path = uri.getPath();
                String query = uri.getQuery();
                if (path != null) {
                    candidate = path + (query != null && !query.isBlank() ? "?" + query : "");
                }
            } catch (Exception ignored) {}
        }

        if (candidate != null && isValidInternalUrl(candidate, actor)) {
            return candidate;
        }

        if (actor != null && actor.getRole() != null) {
            switch (actor.getRole()) {
                case ADMIN:
                    return "/admin";
                case CASHIER:
                    return "/cashier";
                case KITCHEN:
                    return "/kitchen";
                case CUSTOMER:
                default:
                    return "/";
            }
        }
        return "/";
    }

    private boolean isValidInternalUrl(String url, CurrentActor actor) {
        if (url == null || !url.startsWith("/") || url.startsWith("//") || url.startsWith("/\\")) {
            return false;
        }
        String lower = url.toLowerCase();
        if (lower.startsWith("/login") || lower.startsWith("/logout") || lower.startsWith("/profile") || lower.startsWith("/api/")) {
            return false;
        }
        if (lower.startsWith("/admin") && (actor == null || actor.getRole() != vn.edu.ute.milktea.entity.account.Role.ADMIN)) {
            return false;
        }
        if (lower.startsWith("/cashier") && (actor == null || (actor.getRole() != vn.edu.ute.milktea.entity.account.Role.CASHIER && actor.getRole() != vn.edu.ute.milktea.entity.account.Role.ADMIN))) {
            return false;
        }
        if (lower.startsWith("/kitchen") && (actor == null || (actor.getRole() != vn.edu.ute.milktea.entity.account.Role.KITCHEN && actor.getRole() != vn.edu.ute.milktea.entity.account.Role.ADMIN))) {
            return false;
        }
        return true;
    }
}
