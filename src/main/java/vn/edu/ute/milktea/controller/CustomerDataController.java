package vn.edu.ute.milktea.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ute.milktea.common.ApiResponse;
import vn.edu.ute.milktea.dto.*;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.account.CustomerDataService;
import java.util.List;

@RestController @RequestMapping("/api/v1/account") @RequiredArgsConstructor
public class CustomerDataController {
    private final CustomerDataService service;
    @GetMapping("/preferences") public org.springframework.web.servlet.ModelAndView preferences(){return new org.springframework.web.servlet.ModelAndView("customer/account-data");}
    @GetMapping("/cart") public ApiResponse<CartDto.CartResponse> cart(@AuthenticationPrincipal CurrentActor a){return ApiResponse.ok(service.cart(a));}
    @PutMapping("/cart") public ApiResponse<CartDto.CartResponse> cart(@AuthenticationPrincipal CurrentActor a,@RequestBody CartDto.ReplaceCartRequest input,@RequestHeader("If-Match") String version){return ApiResponse.ok(service.replaceCart(a,input,version.replace("\"","")));}
    @GetMapping("/addresses") public ApiResponse<List<CustomerDataDto.AddressView>> addresses(@AuthenticationPrincipal CurrentActor a){return ApiResponse.ok(service.addresses(a));}
    @PostMapping("/addresses") public ApiResponse<CustomerDataDto.AddressView> address(@AuthenticationPrincipal CurrentActor a,@Valid @RequestBody CustomerDataDto.AddressInput input){return ApiResponse.ok(service.saveAddress(a,null,input));}
    @PutMapping("/addresses/{id}") public ApiResponse<CustomerDataDto.AddressView> address(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@Valid @RequestBody CustomerDataDto.AddressInput input){return ApiResponse.ok(service.saveAddress(a,id,input));}
    @DeleteMapping("/addresses/{id}") public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id){service.deleteAddress(a,id);return ApiResponse.ok(null);}
    @GetMapping("/favorites") public ApiResponse<List<CustomerDataDto.InterestView>> favorites(@AuthenticationPrincipal CurrentActor a){return ApiResponse.ok(service.interests(a,true));}
    @GetMapping("/viewed") public ApiResponse<List<CustomerDataDto.InterestView>> viewed(@AuthenticationPrincipal CurrentActor a){return ApiResponse.ok(service.interests(a,false));}
    @PutMapping("/favorites/{id}") public ApiResponse<Void> favorite(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@RequestParam boolean enabled){service.interest(a,id,enabled,false);return ApiResponse.ok(null);}
    @PostMapping("/viewed/{id}") public ApiResponse<Void> viewed(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id){service.interest(a,id,null,true);return ApiResponse.ok(null);}
}
