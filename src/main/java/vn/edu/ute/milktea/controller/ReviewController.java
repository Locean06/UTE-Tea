package vn.edu.ute.milktea.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ute.milktea.common.ApiResponse;
import vn.edu.ute.milktea.dto.MarketDto.*;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.ReviewService;
import java.util.List;
@RestController @RequiredArgsConstructor
public class ReviewController {
 private final ReviewService reviews;
 @GetMapping("/api/v1/public/market/products/{id}/reviews") public ApiResponse<List<ReviewView>> list(@PathVariable Long id,@RequestParam(defaultValue="0") int page){return ApiResponse.ok(reviews.list(id,page));}
 @PostMapping("/api/v1/market/products/{id}/reviews") public ApiResponse<ReviewView> create(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@Valid @RequestBody ReviewInput input){return ApiResponse.ok(reviews.create(a,id,input));}
 @PostMapping("/api/v1/market/reviews/{id}/media") public ApiResponse<ReviewView> media(@AuthenticationPrincipal CurrentActor a,@PathVariable Long id,@RequestParam("file") MultipartFile file){return ApiResponse.ok(reviews.media(a,id,file));}
}
