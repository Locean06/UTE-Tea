package vn.edu.ute.milktea.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.entity.settings.GlobalSettings;
import vn.edu.ute.milktea.repository.catalog.CategoryRepository;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;
import vn.edu.ute.milktea.repository.inventory.MaterialRepository;
import vn.edu.ute.milktea.repository.settings.GlobalSettingsRepository;
import vn.edu.ute.milktea.repository.table.DiningTableRepository;
import vn.edu.ute.milktea.security.CurrentActor;

@Controller
@RequiredArgsConstructor
public class StaffPageController {

    private final GlobalSettingsRepository settingsRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final DiningTableRepository tableRepository;
    private final MaterialRepository materialRepository;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "redirect", required = false) String redirect,
            @RequestParam(value = "error", required = false) String error,
            Model model) {
        model.addAttribute("redirect", redirect != null ? redirect : "/");
        model.addAttribute("error", error);
        return "auth/login";
    }

    // Shared staff entry point: immediately open the real workspace; no extra landing page.
    @GetMapping("/staff")
    public String staffHubPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || !actor.isAuthenticated()) return "redirect:/login?redirect=/staff";
        return switch (actor.getRole()) {
            case CASHIER -> "redirect:/cashier";
            case KITCHEN -> "redirect:/kitchen";
            case SHIPPER -> "redirect:/delivery";
            case ADMIN -> "redirect:/cashier";
            default -> "redirect:/login?redirect=/staff";
        };
    }

    @GetMapping("/delivery")
    public String deliveryPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || !actor.hasRole(Role.SHIPPER)) {
            return "redirect:/login?redirect=/delivery";
        }
        model.addAttribute("actor", actor);
        model.addAttribute("pageTitle", "Giao hàng được phân công");
        return "delivery/dashboard";
    }

    @GetMapping("/cashier")
    public String cashierPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || (actor.getRole() != Role.CASHIER && actor.getRole() != Role.ADMIN)) {
            return "redirect:/login?redirect=/cashier";
        }
        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        model.addAttribute("settings", settings);
        model.addAttribute("actor", actor);
        var posProducts = productRepository.findAllByActiveTrue().stream()
                .filter(product -> product.getShop() == null).toList();
        var posCategoryIds = posProducts.stream().map(product -> product.getCategory().getId())
                .collect(java.util.stream.Collectors.toSet());
        model.addAttribute("categories", categoryRepository.findAllByActiveTrue().stream()
                .filter(category -> posCategoryIds.contains(category.getId())).toList());
        model.addAttribute("products", posProducts);
        return "cashier/index";
    }

    @GetMapping("/cashier/delivery-orders")
    public String cashierDeliveryPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || (actor.getRole() != Role.CASHIER && actor.getRole() != Role.ADMIN)) {
            return "redirect:/login?redirect=/cashier/delivery-orders";
        }
        // Opening the JS-driven delivery inbox should not depend on CauHinhChung.
        model.addAttribute("actor", actor);
        model.addAttribute("pageTitle", "Quản lý đơn giao hàng");
        return "cashier/delivery-orders";
    }

    @GetMapping("/kitchen")
    public String kitchenPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || (actor.getRole() != Role.KITCHEN && actor.getRole() != Role.ADMIN)) {
            return "redirect:/login?redirect=/kitchen";
        }
        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        model.addAttribute("settings", settings);
        model.addAttribute("actor", actor);
        return "kitchen/orders";
    }

    @GetMapping("/admin")
    public String adminMainPage(
            @RequestParam(value = "section", required = false) String section,
            @AuthenticationPrincipal CurrentActor actor,
            Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return "redirect:/login?redirect=/admin";
        }
        if ("catalog".equalsIgnoreCase(section)) {
            return adminCatalogPage(actor, model);
        } else if ("tables".equalsIgnoreCase(section) || "branches".equalsIgnoreCase(section)) {
            return "redirect:/admin/branches";
        } else if ("promotions".equalsIgnoreCase(section)) {
            return "redirect:/admin/promotions";
        } else if ("orders".equalsIgnoreCase(section)) {
            return adminOrdersPage(actor, model);
        } else if ("staff".equalsIgnoreCase(section)) {
            return adminStaffPage(actor, model);
        } else if ("inventory".equalsIgnoreCase(section)) {
            return adminInventoryPage(actor, model);
        } else if ("documents".equalsIgnoreCase(section)) {
            return documentsPage(actor, model);
        } else if ("reports".equalsIgnoreCase(section)) {
            return adminReportsPage(actor, model);
        } else if ("settings".equalsIgnoreCase(section)) {
            return adminSettingsPage(actor, model);
        }
        return adminReportsPage(actor, model);
    }

    @GetMapping("/admin/catalog")
    public String adminCatalogPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return "redirect:/login?redirect=/admin/catalog";
        }
        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        model.addAttribute("settings", settings);
        model.addAttribute("actor", actor);
        model.addAttribute("categories", categoryRepository.findAllByActiveTrue().stream()
                .filter(cat -> cat.getShop() == null)
                .sorted(java.util.Comparator.comparing(vn.edu.ute.milktea.entity.catalog.Category::getId))
                .toList());
        model.addAttribute(
            "products",
            productRepository.findAllByActiveTrue().stream().filter(product -> product.getShop() == null).toList()
        );
        model.addAttribute("materials", materialRepository.findAllByActiveTrue());
        return "admin/catalog";
    }

    // Deprecated dine-in area: do not expose a table map on the Take Away dashboard.
    @GetMapping("/admin/tables")
    public String retiredTablesPage() { return "redirect:/admin/branches"; }

    @GetMapping("/admin/branches")
    public String adminBranchesPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) return "redirect:/login?redirect=/admin/branches";
        model.addAttribute("actor", actor);
        model.addAttribute("pageTitle", "Cập nhật chi nhánh");
        return "admin/branches";
    }

    @GetMapping("/admin/promotions")
    public String adminPromotionsPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) return "redirect:/login?redirect=/admin/promotions";
        model.addAttribute("actor", actor);
        model.addAttribute("pageTitle", "Quản lý mã khuyến mãi");
        return "admin/promotions";
    }

    @GetMapping("/admin/orders")
    public String adminOrdersPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return "redirect:/login?redirect=/admin/orders";
        }
        // Order management loads its records using authenticated APIs.
        model.addAttribute("actor", actor);
        model.addAttribute("pageTitle", "Quản lý đơn hàng");
        return "admin/orders";
    }

    @GetMapping("/admin/staff")
    public String adminStaffPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return "redirect:/login?redirect=/admin/staff";
        }
        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        model.addAttribute("settings", settings);
        model.addAttribute("actor", actor);
        return "admin/staff";
    }

    @GetMapping("/admin/inventory")
    public String adminInventoryPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return "redirect:/login?redirect=/admin/inventory";
        }
        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        model.addAttribute("settings", settings);
        model.addAttribute("actor", actor);
        model.addAttribute("materials", materialRepository.findAll());
        return "admin/inventory";
    }

    @GetMapping("/admin/reports")
    public String adminReportsPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return "redirect:/login?redirect=/admin/reports";
        }
        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        model.addAttribute("settings", settings);
        model.addAttribute("actor", actor);
        return "admin/reports";
    }

    @GetMapping("/admin/documents")
    public String documentsPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return "redirect:/login?redirect=/admin/documents";
        }
        model.addAttribute("settings", settingsRepository.findById(1).orElse(null));
        model.addAttribute("actor", actor);
        return "admin/documents";
    }

    @GetMapping("/admin/settings")
    public String adminSettingsPage(@AuthenticationPrincipal CurrentActor actor, Model model) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return "redirect:/login?redirect=/admin/settings";
        }
        GlobalSettings settings = settingsRepository.findById(1).orElse(null);
        model.addAttribute("settings", settings);
        model.addAttribute("actor", actor);
        return "admin/settings";
    }
}
