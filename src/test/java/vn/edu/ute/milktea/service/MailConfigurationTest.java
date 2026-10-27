package vn.edu.ute.milktea.service;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.ute.milktea.service.mail.MailService;
import vn.edu.ute.milktea.common.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MailConfigurationTest {
    @Test void missingCredentialsFailClearlyBeforeSmtpWithoutLeakingSecrets() {
        var sender=mock(JavaMailSender.class);var service=new MailService(sender);
        ReflectionTestUtils.setField(service,"mailFrom","");
        var ex=assertThrows(BusinessException.class,()->service.sendRegistrationOtpEmail("customer@example.test","Khách","123456",10));
        assertEquals(ErrorCode.MAIL_SEND_FAILED,ex.getErrorCode());assertFalse(ex.getMessage().contains("123456"));verifyNoInteractions(sender);
    }
    @Test void smtpExceptionCannotExposeOtpOrPasswordInResponseOrLogs() throws Exception {
        var sender=mock(JavaMailSender.class);var service=new MailService(sender);
        ReflectionTestUtils.setField(service,"mailFrom","sender@example.test");
        ReflectionTestUtils.setField(service,"smtpUsername","sender@example.test");
        ReflectionTestUtils.setField(service,"smtpPassword","private-test-password");
        when(sender.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
        doThrow(new org.springframework.mail.MailSendException("OTP=123456 password=private-test-password")).when(sender).send(any(jakarta.mail.internet.MimeMessage.class));
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(MailService.class);
        var capture=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();capture.start();logger.addAppender(capture);
        try {
            var failure=assertThrows(BusinessException.class,()->service.sendRegistrationOtpEmail("customer@example.test","Khách","123456",10));
            String output=failure.getMessage()+capture.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
            assertFalse(output.contains("123456"));assertFalse(output.contains("private-test-password"));
        } finally {logger.detachAppender(capture);capture.stop();}
    }

    @Test void authenticationFailureIsReportedAsSmtpConfigurationProblem() {
        var sender=mock(JavaMailSender.class);var service=new MailService(sender);
        ReflectionTestUtils.setField(service,"mailFrom","sender@example.test");
        ReflectionTestUtils.setField(service,"smtpUsername","sender@example.test");
        ReflectionTestUtils.setField(service,"smtpPassword","test-secret-password");
        when(sender.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
        doThrow(new org.springframework.mail.MailAuthenticationException("test-secret-password")).when(sender).send(any(jakarta.mail.internet.MimeMessage.class));
        var failure=assertThrows(BusinessException.class,()->service.sendRegistrationOtpEmail("customer@example.test","Khách","123456",10));
        assertEquals(ErrorCode.MAIL_SEND_FAILED,failure.getErrorCode());
        assertTrue(failure.getMessage().contains("chưa xác thực được"));
        assertFalse(failure.getMessage().contains("test-secret-password"));
        assertFalse(failure.getMessage().contains("123456"));
    }

}
