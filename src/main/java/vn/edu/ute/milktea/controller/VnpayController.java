package vn.edu.ute.milktea.controller;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import vn.edu.ute.milktea.common.ApiResponse;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.VnpayService;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class VnpayController {
 private final VnpayService gateway;
 @PostMapping("/api/v1/market/orders/{id}/payment-url") public ApiResponse<Map<String,String>> url(@AuthenticationPrincipal CurrentActor actor,@PathVariable Long id,HttpServletRequest request){return ApiResponse.ok(Map.of("url",gateway.paymentUrl(actor,id,request.getRemoteAddr())));}
 @GetMapping("/api/v1/public/vnpay/ipn") public Map<String,String> ipn(@RequestParam MultiValueMap<String,String> params){if(params.values().stream().anyMatch(v->v.size()!=1))return Map.of("RspCode","97","Message","Duplicate parameters");return gateway.ipn(params.toSingleValueMap());}
}
