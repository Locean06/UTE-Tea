package vn.edu.ute.milktea.service;
import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.entity.market.*;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import vn.edu.ute.milktea.repository.market.ProductReviewRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.market.ReviewService;
import vn.edu.ute.milktea.service.media.ProductImageService;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ReviewMediaValidationTest {
 @Mock AccountRepository accounts;@Mock ProductReviewRepository reviews;@Mock ProductImageService images;@Mock Cloudinary cloud;
 @InjectMocks ReviewService service;
 CurrentActor author=CurrentActor.authenticated(1L,Role.CUSTOMER);
 void fixture(long owner){var account=Account.builder().id(owner).build();var review=new ProductReview();review.setId(7L);review.setAccount(account);when(accounts.findByIdWithLock(1L)).thenReturn(Optional.of(Account.builder().id(1L).build()));when(reviews.findById(7L)).thenReturn(Optional.of(review));}
 @Test void anotherCustomerCannotAttachMedia(){fixture(2L);assertThrows(BusinessException.class,()->service.media(author,7L,new MockMultipartFile("file","photo.png","image/png",new byte[]{1,2,3})));verifyNoInteractions(cloud,images);}
 @Test void rejectsSpoofedImageAndVideoBeforeUpload(){fixture(1L);when(images.isCloudinaryConfigured()).thenReturn(true);for(String type:List.of("image/png","video/mp4"))assertThrows(BusinessException.class,()->service.media(author,7L,new MockMultipartFile("file","bad",type,"<script>alert(1)</script>".getBytes())));verifyNoInteractions(cloud);}
 @Test void rejectsOverLimitFileBeforeUpload(){fixture(1L);assertThrows(BusinessException.class,()->service.media(author,7L,new MockMultipartFile("file","big.mp4","video/mp4",new byte[20*1024*1024+1])));verifyNoInteractions(cloud,images);}
}
