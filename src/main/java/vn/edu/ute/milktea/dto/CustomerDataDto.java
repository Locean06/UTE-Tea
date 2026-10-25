package vn.edu.ute.milktea.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;

public class CustomerDataDto {
    public record AddressInput(@NotBlank @Size(max=120) String recipient,
        @NotBlank @Pattern(regexp="[+0-9 ()-]{8,20}") String phone,
        @NotBlank @Size(max=500) String addressLine, boolean defaultAddress,
        @Size(max=120) String area,@DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {
        public AddressInput(String recipient,String phone,String addressLine,boolean defaultAddress){this(recipient,phone,addressLine,defaultAddress,null,null,null);}
        @AssertTrue(message="Cần nhập đủ cặp tọa độ") public boolean isCoordinatePairValid(){return (latitude==null)==(longitude==null);}
    }
    public record AddressView(Long id,String recipient,String phone,String addressLine,boolean defaultAddress,String area,Double latitude,Double longitude) {}
    public record InterestView(CatalogDto.ProductItem product,boolean favorite,Instant lastViewedAt) {}
}
