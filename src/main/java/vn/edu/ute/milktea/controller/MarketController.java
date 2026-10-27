package vn.edu.ute.milktea.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.ute.milktea.common.ApiResponse;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.*;
import java.math.BigDecimal;
import java.util.*;

@RestController @RequiredArgsConstructor
public class MarketController {
 private final MarketService service;
 private final CatalogSearchService catalog;
 private final BranchSelectionService branches;
 @GetMapping("/marketplace") public ModelAndView page(){return new ModelAndView("customer/marketplace");}
 @GetMapping("/merchant") public ModelAndView merchant(@AuthenticationPrincipal CurrentActor actor){
  if(actor!=null && actor.hasRole(vn.edu.ute.milktea.entity.account.Role.SHIPPER))return new ModelAndView("redirect:/delivery");
  return new ModelAndView("market/portal");
 }
 @GetMapping("/api/v1/public/market/catalog") public ApiResponse<CatalogPage> catalog(
  @RequestParam(required=false) String q,@RequestParam(required=false) Long category,@RequestParam(required=false) Long shop,
  @RequestParam(required=false) BigDecimal min,@RequestParam(required=false) BigDecimal max,
  @RequestParam(defaultValue="bestselling") String sort,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(catalog.search(q,category,shop,min,max,sort,page,size));}
 @GetMapping("/api/v1/public/market/shops") public ApiResponse<List<ShopView>> publicShops(){return ApiResponse.ok(service.shops(null,true));}
 @GetMapping("/api/v1/public/market/products/{id}/options") public ApiResponse<MenuOptions> options(@PathVariable Long id){return ApiResponse.ok(catalog.options(id));}
 @GetMapping("/api/v1/account/cart/branches") public ApiResponse<List<BranchOption>> branches(@AuthenticationPrincipal CurrentActor a,@RequestParam Long addressId){return ApiResponse.ok(branches.recommendations(a,addressId));}
 @PostMapping("/api/v1/account/cart/quote") public ApiResponse<CartQuote> quote(@AuthenticationPrincipal CurrentActor a,@Valid @RequestBody QuoteInput input){return ApiResponse.ok(service.quote(a,input));}
 @GetMapping("/api/v1/public/market/carriers") public ApiResponse<List<CarrierView>> carriers(){return ApiResponse.ok(service.carriers());}
 @GetMapping("/api/v1/market/shops") public ApiResponse<List<ShopView>> shops(@AuthenticationPrincipal CurrentActor a){return ApiResponse.ok(service.shops(a,false));}
 @PostMapping("/api/v1/market/shops") public ApiResponse<ShopView> createShop(@AuthenticationPrincipal CurrentActor a,@Valid @RequestBody ShopInput input){return ApiResponse.ok(service.createShop(a,input));}
 @PutMapping("/api/v1/market/shops/{id}") public ApiResponse<ShopView> editShop(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@Valid @RequestBody ShopInput input){return ApiResponse.ok(service.editShop(a,id,input));}
 @PutMapping("/api/v1/market/shops/{id}/config") public ApiResponse<ShopView> config(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@Valid @RequestBody ShopConfig input){return ApiResponse.ok(service.configureShop(a,id,input));}
 @PutMapping("/api/v1/market/shops/{id}/managers/{accountId}") public ApiResponse<Void> manager(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@PathVariable Long accountId,@RequestParam boolean enabled){service.assignManager(a,id,accountId,enabled);return ApiResponse.ok(null);}
 @GetMapping("/api/v1/market/shops/{id}/categories") public ApiResponse<List<CategoryView>> categories(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id){return ApiResponse.ok(service.shopCategories(a,id));}
 @PostMapping("/api/v1/market/shops/{id}/categories") public ApiResponse<CategoryView> category(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@Valid @RequestBody CategoryInput input){return ApiResponse.ok(service.saveCategory(a,id,null,input));}
 @PutMapping("/api/v1/market/shops/{id}/categories/{categoryId}") public ApiResponse<CategoryView> category(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@PathVariable Long categoryId,@Valid @RequestBody CategoryInput input){return ApiResponse.ok(service.saveCategory(a,id,categoryId,input));}
 @GetMapping("/api/v1/market/shops/{id}/products") public ApiResponse<List<ProductView>> products(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id){return ApiResponse.ok(service.shopProducts(a,id));}
 @PostMapping("/api/v1/market/shops/{id}/products") public ApiResponse<ProductView> product(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@Valid @RequestBody ProductInput input){return ApiResponse.ok(service.saveProduct(a,id,null,input));}
 @PostMapping("/api/v1/market/shops/{id}/products/{productId}/image") public ApiResponse<vn.edu.ute.milktea.dto.AdminDto.ProductImageView> image(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@PathVariable Long productId,@RequestParam("file") org.springframework.web.multipart.MultipartFile file){return ApiResponse.ok(service.productImage(a,id,productId,file));}
 @PutMapping("/api/v1/market/shops/{id}/products/{productId}") public ApiResponse<ProductView> product(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@PathVariable Long productId,@Valid @RequestBody ProductInput input){return ApiResponse.ok(service.saveProduct(a,id,productId,input));}
 @PostMapping("/api/v1/market/carriers") public ApiResponse<CarrierView> carrier(@AuthenticationPrincipal CurrentActor a,@RequestParam(required=false) Long shopId,@Valid @RequestBody CarrierInput input){return ApiResponse.ok(service.saveCarrier(a,shopId,null,input));}
 @GetMapping("/api/v1/market/carriers") public ApiResponse<List<CarrierView>> managedCarriers(@AuthenticationPrincipal CurrentActor a,@RequestParam(required=false) Long shopId){return ApiResponse.ok(service.managedCarriers(a,shopId));}
 @PutMapping("/api/v1/market/carriers/{id}") public ApiResponse<CarrierView> carrier(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@RequestParam(required=false) Long shopId,@Valid @RequestBody CarrierInput input){return ApiResponse.ok(service.saveCarrier(a,shopId,id,input));}
 @GetMapping("/api/v1/market/vouchers") public ApiResponse<List<VoucherView>> vouchers(@AuthenticationPrincipal CurrentActor a,@RequestParam(required=false) Long shopId){return ApiResponse.ok(service.vouchers(a,shopId));}
 @PostMapping("/api/v1/market/vouchers") public ApiResponse<VoucherView> voucher(@AuthenticationPrincipal CurrentActor a,@RequestParam(required=false) Long shopId,@Valid @RequestBody VoucherInput input){return ApiResponse.ok(service.saveVoucher(a,shopId,input));}
 @DeleteMapping("/api/v1/market/vouchers/{id}") public ApiResponse<Void> disableVoucher(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id){service.disableVoucher(a,id);return ApiResponse.ok(null);}
 @PostMapping("/api/v1/market/orders") public ApiResponse<OrderView> checkout(@AuthenticationPrincipal CurrentActor a,@Valid @RequestBody Checkout input,@RequestHeader("Idempotency-Key") String key){return ApiResponse.ok(service.checkout(a,input,key));}
 @GetMapping("/api/v1/market/orders") public ApiResponse<List<OrderView>> orders(@AuthenticationPrincipal CurrentActor a,@RequestParam(required=false) Long shopId){return ApiResponse.ok(service.orders(a,shopId));}
 @GetMapping("/api/v1/market/orders/{id}") public ApiResponse<OrderView> order(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id){return ApiResponse.ok(service.order(a,id));}
 @PutMapping("/api/v1/market/orders/{id}/status") public ApiResponse<OrderView> status(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@Valid @RequestBody Transition input){return ApiResponse.ok(service.transition(a,id,input));}
 @GetMapping("/api/v1/market/shops/{id}/shippers") public ApiResponse<List<ShipperOption>> shippers(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id){return ApiResponse.ok(service.activeShippers(a,id));}
 @PutMapping("/api/v1/market/orders/{id}/shipper") public ApiResponse<OrderView> shipper(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@Valid @RequestBody AssignShipper input){return ApiResponse.ok(service.assignShipper(a,id,input));}
 @GetMapping("/api/v1/market/summary") public ApiResponse<Summary> summary(@AuthenticationPrincipal CurrentActor a,@RequestParam(required=false) Long shopId){return ApiResponse.ok(service.summary(a,shopId));}
 @GetMapping("/api/v1/market/accounts") public ApiResponse<List<AccountView>> accounts(@AuthenticationPrincipal CurrentActor a){return ApiResponse.ok(service.accounts(a));}
 @GetMapping("/api/v1/market/shops/{id}/customers") public ApiResponse<List<AccountView>> customers(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id){return ApiResponse.ok(service.customers(a,id));}
 @PutMapping("/api/v1/market/shops/{id}/customers/{accountId}") public ApiResponse<Void> customerAccess(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@PathVariable Long accountId,@RequestParam boolean enabled){service.customerAccess(a,id,accountId,enabled);return ApiResponse.ok(null);}
 @PutMapping("/api/v1/market/accounts/{id}") public ApiResponse<Void> role(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@Valid @RequestBody RoleInput input){service.changeRole(a,id,input);return ApiResponse.ok(null);}
}
