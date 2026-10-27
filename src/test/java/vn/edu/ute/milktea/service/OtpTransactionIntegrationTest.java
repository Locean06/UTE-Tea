package vn.edu.ute.milktea.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.ute.milktea.common.*;
import vn.edu.ute.milktea.dto.AuthDto;
import vn.edu.ute.milktea.entity.account.EmailVerificationOtp;
import vn.edu.ute.milktea.repository.account.*;
import vn.edu.ute.milktea.service.auth.AuthService;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** No test transaction: assertions read committed state through real Spring proxies. SMTP only is mocked. */
@SpringBootTest(properties={"spring.mail.username=sender@example.test","spring.mail.password=test-only-app-password","app.mail.from=sender@example.test","management.health.mail.enabled=false","spring.datasource.url=jdbc:h2:mem:otpflow;DB_CLOSE_DELAY=-1;MODE=MSSQLServer"})
@ActiveProfiles("test")
class OtpTransactionIntegrationTest {
    @Autowired AuthService auth; @Autowired AccountRepository accounts;
    @Autowired EmailVerificationOtpRepository otps; @Autowired PlatformTransactionManager transactions;
    @Autowired jakarta.validation.Validator validator;
    @MockitoBean JavaMailSender sender;
    String email,rawOtp;
    @BeforeEach void setup() throws Exception {
        email=UUID.randomUUID()+"@otp.test";
        when(sender.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i->{
            MimeMessage message=i.getArgument(0);
            var match=Pattern.compile("class=\"otp-code\">([0-9]{6})<").matcher(message.getContent().toString());
            assertTrue(match.find());rawOtp=match.group(1);return null;
        }).when(sender).send(any(MimeMessage.class));
    }
    AuthDto.RegisterRequest input() {return AuthDto.RegisterRequest.builder().email(email).fullName("Khách thử").phone("0901234567").password("Password@123").build();}
    EmailVerificationOtp latest() {return otps.findTopByEmailAndPurposeOrderByCreatedAtDesc(email,EmailVerificationOtp.PURPOSE_REGISTER).orElseThrow();}
    void makeResendAvailable() {new TransactionTemplate(transactions).executeWithoutResult(s->{var otp=latest();otp.setLastSentAt(Instant.now().minusSeconds(120));otps.save(otp);});}
    @Test void pendingAccountHasHashedSixDigitOtpAndCannotLoginUntilVerified() {
        var response=auth.register(input());assertFalse(response.isEmailVerified());assertTrue(rawOtp.matches("[0-9]{6}"));
        assertFalse(accounts.findByEmail(email).orElseThrow().getEmailVerified());
        assertNotEquals(rawOtp,latest().getOtpHash());assertNull(latest().getUsedAt());
        var login=new AuthDto.LoginRequest(email,"Password@123");assertThrows(BusinessException.class,()->auth.login(login));
        auth.verifyEmail(new AuthDto.VerifyEmailRequest(email,rawOtp));
        assertTrue(accounts.findByEmail(email).orElseThrow().getEmailVerified());assertNotNull(latest().getUsedAt());
        assertNotNull(auth.login(login).getToken());
        assertThrows(BusinessException.class,()->auth.register(input()));
    }
    @Test void wrongAttemptsActuallyCommitAndRevokeAtLimit() {
        auth.register(input());String wrong=rawOtp.equals("000000")?"000001":"000000";
        for(int attempt=1;attempt<=5;attempt++) {
            assertThrows(BusinessException.class,()->auth.verifyEmail(new AuthDto.VerifyEmailRequest(email,wrong)));
            assertEquals(attempt,latest().getAttempts());
        }
        assertNotNull(latest().getRevokedAt());assertFalse(accounts.findByEmail(email).orElseThrow().getEmailVerified());
        assertThrows(BusinessException.class,()->auth.verifyEmail(new AuthDto.VerifyEmailRequest(email,rawOtp)));
        // Revoking a code must not bypass the resend cooldown.
        assertThrows(BusinessException.class,()->auth.resendRegisterOtp(email));
    }
    @Test void expiredOtpCannotActivateAccountAndResendReplacesIt() {
        auth.register(input());String old=rawOtp;
        new TransactionTemplate(transactions).executeWithoutResult(s->{var otp=latest();otp.setExpiresAt(Instant.now().minusSeconds(1));otps.save(otp);});
        assertThrows(BusinessException.class,()->auth.verifyEmail(new AuthDto.VerifyEmailRequest(email,old)));
        assertNotNull(latest().getRevokedAt());makeResendAvailable();
        auth.resendRegisterOtp(email);assertNull(latest().getRevokedAt());
        auth.verifyEmail(new AuthDto.VerifyEmailRequest(email,rawOtp));assertTrue(accounts.findByEmail(email).orElseThrow().getEmailVerified());
    }
    @Test void smtpAuthenticationFailureKeepsOneRecoverablePendingAccount() {
        doThrow(new MailAuthenticationException("test auth failure")).when(sender).send(any(MimeMessage.class));
        var failure=assertThrows(BusinessException.class,()->auth.register(input()));assertEquals(ErrorCode.MAIL_SEND_FAILED,failure.getErrorCode());
        var id=accounts.findByEmail(email).orElseThrow().getId();assertFalse(accounts.findById(id).orElseThrow().getEmailVerified());assertNotNull(latest().getOtpHash());
        makeResendAvailable();
        doNothing().when(sender).send(any(MimeMessage.class));auth.resendRegisterOtp(email);
        assertEquals(id,accounts.findByEmail(email).orElseThrow().getId());
    }
    @Test void smtpTimeoutDoesNotRollBackThePendingRegistration() {
        doThrow(new MailSendException("test timeout",new java.net.SocketTimeoutException())).when(sender).send(any(MimeMessage.class));
        assertThrows(BusinessException.class,()->auth.register(input()));
        assertTrue(accounts.findByEmail(email).isPresent());assertFalse(accounts.findByEmail(email).orElseThrow().getEmailVerified());
        assertThrows(BusinessException.class,()->auth.register(input())); // cooldown; no duplicate account
    }
    @Test void invalidRegistrationAndOtpBodiesAreRejectedByValidation() {
        var request=input();request.setEmail("bad");request.setPhone("123");request.setPassword("");
        assertTrue(validator.validate(request).size() >= 3);
        assertFalse(validator.validate(new AuthDto.VerifyEmailRequest(email,"12345x")).isEmpty());
    }
}
