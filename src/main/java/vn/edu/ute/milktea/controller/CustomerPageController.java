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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.edu.ute.milktea.util.MenuCatalogSupport;

@Controller
@RequiredArgsConstructor
public class CustomerPageController {

    private static final Logger log = LoggerFactory.getLogger(CustomerPageController.class);

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

        // Each read is independent so a database migration issue cannot turn the
        // entire public home/menu page into a JSON 500 response.
        boolean dataUnavailable = false;
        GlobalSettings settings = null;
        List<Category> categories = List.of();
        List<Product> products = List.of();
        try {
            settings = settingsRepository.findById(1).orElse(null);
        } catch (org.springframework.dao.DataAccessException ex) {
            dataUnavailable = true;
            log.error("UTE Tea storefront settings query failed. Check database migrations.", ex);
        }
        try {
            categories = categoryRepository.findAllByActiveTrue().stream()
                    .filter(c -> c.getShop() == null && Boolean.TRUE.equals(c.getActive())).toList();
        } catch (org.springframework.dao.DataAccessException ex) {
            dataUnavailable = true;
            log.error("UTE Tea storefront category query failed. Check DanhMuc columns/migrations.", ex);
        }
        try {
            products = MenuCatalogSupport.toDisplayProducts(
                    productRepository.findAllByActiveTrue().stream()
                            .filter(p -> p.getShop() == null && p.getCategory() != null && Boolean.TRUE.equals(p.getCategory().getActive()))
                            .toList()
            );
        } catch (org.springframework.dao.DataAccessException ex) {
            dataUnavailable = true;
            log.error("UTE Tea storefront product query failed. Check MonUong columns/migrations.", ex);
        }
        model.addAttribute("catalogUnavailable", dataUnavailable);

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
        GlobalSettings settings = null;
        try {
            settings = settingsRepository.findById(1).orElse(null);
        } catch (org.springframework.dao.DataAccessException ex) {
            log.warn("Cart page: optional settings unavailable", ex);
        }
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
        // The orders dashboard includes every store order and is strictly staff-only.
        if (!actor.hasRole(vn.edu.ute.milktea.entity.account.Role.CASHIER)
                && !actor.hasRole(vn.edu.ute.milktea.entity.account.Role.ADMIN)) {
            return "redirect:/";
        }
        // This page renders a client-side order list; it must not fail with HTTP 500
        // just because optional CauHinhChung is missing or its schema is out of sync.
        // GlobalModelAttributes supplies settings when available (and safely falls back).
        DiningTable table = null; // Dine-in tables are assigned at the authenticated POS.

        model.addAttribute("actor", actor);
        model.addAttribute("currentTable", table);
        model.addAttribute("tableQr", null);

        return "customer/orders";
    }

    // Customer-only private order history; unlike /orders it never exposes store-wide data.
    @GetMapping("/my-orders")
    public String myOrdersPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || !actor.isAuthenticated()) {
            return "redirect:/login?redirect=/my-orders";
        }
        if (!actor.hasRole(vn.edu.ute.milktea.entity.account.Role.CUSTOMER)) {
            return "redirect:/";
        }
        // Do not query optional store settings while opening customer order history.
        model.addAttribute("pageTitle", "Đơn hàng của tôi");
        return "customer/my-orders";
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
