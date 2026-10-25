package vn.edu.ute.milktea.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;

public class CustomerDataDto {
    public record AddressInput(@NotBlank @Size(max=120) String recipient,
        @NotBlank @Pattern(regexp="[+0-9 ()-]{8,20}") String phone,
        @NotBlank @Size(max=500) String addressLine, boolean defaultAddress) {}
    public record AddressView(Long id,String recipient,String phone,String addressLine,boolean defaultAddress) {}
    public record InterestView(CatalogDto.ProductItem product,boolean favorite,Instant lastViewedAt) {}
}
