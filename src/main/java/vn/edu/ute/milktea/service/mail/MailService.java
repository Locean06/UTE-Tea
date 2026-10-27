package vn.edu.ute.milktea.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.common.ErrorCode;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String mailFrom;

    @Value("${app.mail.sender-name:UTE TEA}")
    private String senderName;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    /**
     * Gửi email mã OTP xác thực tài khoản đăng ký mới.
     * Tách biệt khỏi database transaction để không giữ kết nối DB khi chờ SMTP.
     */
    public void sendRegistrationOtpEmail(String toEmail, String fullName, String otpCode, int expiryMinutes) {
        String recipientName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : "Quý khách";
        String subject = "[UTE TEA] Mã xác thực đăng ký tài khoản";

        String htmlContent = buildOtpEmailHtml(
                recipientName,
                "Xác thực địa chỉ email đăng ký tài khoản",
<<<<<<< HEAD
                "Cảm ơn bạn đã lựa chọn UTE TEA! Vui lòng sử dụng mã xác thực dưới đây để hoàn tất kích hoạt tài khoản của bạn:",
=======
                "Cảm ơn bạn đã lựa chọn UTE TEA! Vui lòng sử dụng mã xác thực gồm 6 chữ số dưới đây để hoàn tất kích hoạt tài khoản của bạn:",
>>>>>>> ai/ute-tea-fixes
                otpCode,
                expiryMinutes,
                "Nếu bạn không thực hiện yêu cầu đăng ký này, xin vui lòng bỏ qua email hoặc thông báo cho chúng tôi để bảo vệ tài khoản.");

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    /**
     * Gửi email mã xác thực khôi phục / đặt lại mật khẩu.
     */
    public void sendPasswordResetOtpEmail(String toEmail, String fullName, String resetCode, int expiryMinutes) {
        String recipientName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : "Quý khách";
        String subject = "[UTE TEA] Mã xác thực đặt lại mật khẩu";

        String htmlContent = buildOtpEmailHtml(
                recipientName,
                "Yêu cầu đặt lại mật khẩu",
<<<<<<< HEAD
                "UTE TEA nhận được yêu cầu đặt lại mật khẩu cho tài khoản liên kết với địa chỉ email này. Vui lòng sử dụng mã xác thực dưới đây:",
=======
                "Hệ thống nhận được yêu cầu đặt lại mật khẩu cho tài khoản UTE TEA liên kết với địa chỉ email này. Vui lòng sử dụng mã xác thực dưới đây:",
>>>>>>> ai/ute-tea-fixes
                resetCode,
                expiryMinutes,
                "Lưu ý quan trọng: Không cung cấp mã này cho bất kỳ ai, kể cả nhân viên quán. Nếu bạn không gửi yêu cầu này, hãy đổi mật khẩu ngay lập tức hoặc bỏ qua email.");

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    /**
     * Thực hiện gửi mail qua SMTP và ném lỗi rõ ràng nếu thất bại.
     */
    private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        // Commit pending account/OTP first. A failed external send must remain recoverable by resend.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { deliver(toEmail, subject, htmlContent); }
                    });
        } else {
            deliver(toEmail, subject, htmlContent);
        }
    }

    private void deliver(String toEmail, String subject, String htmlContent) {
        if (mailFrom == null || mailFrom.isBlank() || smtpUsername == null || smtpUsername.isBlank()
                || smtpPassword == null || smtpPassword.isBlank()) {
            log.error("Thiếu cấu hình SMTP: cần MAIL_USERNAME, MAIL_PASSWORD (Gmail App Password) và MAIL_FROM");
            throw BusinessException.badRequest(ErrorCode.MAIL_SEND_FAILED,
                    "Tài khoản chưa kích hoạt đã được lưu nhưng chưa thể gửi email. Vui lòng thử gửi lại mã sau khi cấu hình SMTP được khôi phục.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(mailFrom, senderName);
            helper.setTo(toEmail.trim().toLowerCase());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            // Ảnh CID đi cùng email; không phụ thuộc URL công khai hoặc ảnh tải từ internet.
            helper.addInline("uteTeaLogo",
                    new ClassPathResource("static/images/ute-tea-olive-mark.png"), "image/png");

            mailSender.send(message);
            log.info("Đã gửi email thành công tới: {}", maskEmail(toEmail));
        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("SMTP từ chối xác thực tới {}: kiểm tra MAIL_USERNAME và MAIL_PASSWORD; Gmail cần App Password hợp lệ", maskEmail(toEmail));
            throw BusinessException.badRequest(ErrorCode.MAIL_SEND_FAILED,
                    "Hệ thống chưa xác thực được với máy chủ email. Vui lòng liên hệ quản trị viên hoặc thử gửi lại mã sau.");
        } catch (MessagingException | MailException e) {
            log.error("Lỗi gửi email SMTP tới {} ({})", maskEmail(toEmail), e.getClass().getSimpleName());
            throw BusinessException.badRequest(
                    ErrorCode.MAIL_SEND_FAILED,
                    "Không thể gửi email xác thực qua hệ thống SMTP. Vui lòng thử lại sau giây lát hoặc kiểm tra kết nối mạng.");
        } catch (Exception e) {
            log.error("Lỗi tạo email tới {} ({})", maskEmail(toEmail), e.getClass().getSimpleName());
            throw BusinessException.badRequest(
                    ErrorCode.MAIL_SEND_FAILED,
                    "Không thể tạo email xác thực. Vui lòng thử gửi lại mã sau.");
        }
    }

    /**
<<<<<<< HEAD
     * Email template UTE TEA: bảng HTML + inline style cho Gmail/Outlook/mobile.
     * Tên người nhận, mã OTP và văn bản động được HTML-escape trước khi chèn.
     * Logo được nhúng bằng Content-ID (cid:uteTeaLogo), không cần URL công khai.
=======
     * Tạo template HTML responsive, thẩm mỹ thương hiệu UTE TEA
>>>>>>> ai/ute-tea-fixes
     */
    private String buildOtpEmailHtml(String recipientName, String title, String description,
            String code, int expiryMinutes, String warning) {
        String html = """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <meta name="color-scheme" content="light">
                    <title>{{TITLE}}</title>
                    <style>
<<<<<<< HEAD
                        @media screen and (max-width: 620px) {
                            .mail-shell { padding: 18px 12px !important; }
                            .mail-header { padding: 24px 24px !important; }
                            .mail-body { padding: 30px 24px 24px !important; }
                            .mail-footer { padding: 22px 24px !important; }
                            .otp-code { font-size: 34px !important; letter-spacing: 5px !important; }
                            .mail-title { font-size: 25px !important; }
                        }
                    </style>
                </head>
                <body style="margin:0;padding:0;background-color:#f2f5ef;color:#2e4032;font-family:Arial,Helvetica,sans-serif;">
                    <!-- Preheader: dùng trong phần xem trước của ứng dụng email. -->
                    <div style="display:none;font-size:1px;color:#f2f5ef;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">
                        UTE TEA gửi mã xác thực để bạn hoàn tất yêu cầu với tài khoản của mình.
=======
                        body { margin: 0; padding: 0; background-color: #f5f8f2; font-family: 'Segoe UI', Helvetica, Arial, sans-serif; color: #333333; }
                        .container { max-width: 580px; margin: 30px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
                        .header { background-color: #2e7d32; background: linear-gradient(135deg, #2e7d32 0%%, #1b5e20 100%%); padding: 32px 24px; text-align: center; color: #ffffff; }
                        .brand-title { font-size: 26px; font-weight: bold; margin: 0; letter-spacing: 1px; }
                        .brand-subtitle { font-size: 13px; opacity: 0.85; margin-top: 6px; }
                        .content { padding: 32px 28px; }
                        .greeting { font-size: 17px; font-weight: 600; margin-bottom: 12px; }
                        .intro { font-size: 14px; line-height: 1.6; color: #555555; margin-bottom: 24px; }
                        .otp-box { background-color: #f1f8ef; border: 2px dashed #a5cba2; border-radius: 10px; padding: 20px; text-align: center; margin: 24px 0; }
                        .otp-title { font-size: 12px; text-transform: uppercase; letter-spacing: 1.5px; color: #4c7545; margin-bottom: 8px; font-weight: 600; }
                        .otp-code { font-size: 34px; font-weight: 800; letter-spacing: 8px; color: #1b5e20; font-family: 'Courier New', monospace; margin: 4px 0; }
                        .otp-expiry { font-size: 13px; color: #2e7d32; margin-top: 8px; font-weight: 500; }
                        .warning-box { background-color: #eef7eb; border-left: 4px solid #66a65a; padding: 12px 16px; border-radius: 4px; font-size: 13px; color: #355e35; line-height: 1.5; margin-top: 24px; }
                        .footer { background-color: #f5f8f2; border-top: 1px solid #dce8d7; padding: 20px; text-align: center; font-size: 12px; color: #888888; }
                        .footer p { margin: 4px 0; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="brand-title">🍵 UTE TEA</div>
                            <div class="brand-subtitle">Hệ Thống Trà Sữa Tươi Cao Cấp</div>
                        </div>
                        <div class="content">
                            <div class="greeting">Xin chào, %s!</div>
                            <div class="intro">%s</div>
                            <div class="otp-box">
                                <div class="otp-title">MÃ XÁC THỰC CỦA BẠN</div>
                                <div class="otp-code">%s</div>
                                <div class="otp-expiry">⏳ Mã có hiệu lực trong vòng <strong>%d phút</strong></div>
                            </div>
                            <div class="warning-box">
                                <strong>Lưu ý an toàn:</strong> %s
                            </div>
                        </div>
                        <div class="footer">
                            <p><strong>Cửa hàng Trà Sữa UTE TEA</strong></p>
                            <p>Địa chỉ: Số 1 Võ Văn Ngân, TP. Thủ Đức, TP. Hồ Chí Minh</p>
                            <p>Thư này được tạo tự động từ hệ thống. Xin vui lòng không trả lời thư này.</p>
                        </div>
>>>>>>> ai/ute-tea-fixes
                    </div>
                    <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%"
                           style="width:100%;border-collapse:collapse;background-color:#f2f5ef;">
                        <tr>
                            <td class="mail-shell" align="center" style="padding:38px 16px 48px;">
                                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="600"
                                       style="width:100%;max-width:600px;border-collapse:separate;border-spacing:0;background-color:#ffffff;border:1px solid #e1e8dc;border-radius:18px;overflow:hidden;">
                                    <tr>
                                        <td height="6" style="height:6px;line-height:6px;font-size:0;background-color:#c9b28a;">&nbsp;</td>
                                    </tr>
                                    <tr>
                                        <td class="mail-header" bgcolor="#344b39" style="padding:30px 40px;background-color:#344b39;">
                                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;">
                                                <tr>
                                                    <td width="66" valign="middle" style="width:66px;vertical-align:middle;">
                                                        <img src="cid:uteTeaLogo" alt="UTE TEA" width="56" height="56"
                                                             style="display:block;width:56px;height:56px;object-fit:contain;border:0;background-color:#f1f2e9;border-radius:12px;">
                                                    </td>
                                                    <td valign="middle" style="vertical-align:middle;padding-left:12px;">
                                                        <div style="font-family:Georgia,'Times New Roman',serif;font-size:27px;line-height:33px;font-weight:bold;letter-spacing:3px;color:#ffffff;">UTE TEA</div>
                                                        <div style="font-family:Arial,Helvetica,sans-serif;font-size:10px;line-height:17px;letter-spacing:3px;font-weight:bold;color:#d5d8c6;">SINCE 2026</div>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="mail-body" style="padding:38px 40px 34px;">
                                            <p style="margin:0 0 11px;font-family:Arial,Helvetica,sans-serif;font-size:11px;line-height:17px;letter-spacing:2px;font-weight:bold;color:#7b875f;">BẢO MẬT TÀI KHOẢN</p>
                                            <h1 class="mail-title" style="margin:0 0 24px;font-family:Georgia,'Times New Roman',serif;font-size:28px;line-height:1.35;font-weight:bold;color:#2f4837;">{{TITLE}}</h1>
                                            <p style="margin:0 0 11px;font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:25px;font-weight:bold;color:#304b37;">Xin chào, {{NAME}}!</p>
                                            <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:25px;color:#59675b;">{{DESCRIPTION}}</p>

                                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                                                   style="width:100%;margin-top:30px;border-collapse:separate;border-spacing:0;background-color:#f5f8f1;border:1px solid #dce7d6;border-radius:14px;">
                                                <tr>
                                                    <td align="center" style="padding:26px 16px 27px;text-align:center;">
                                                        <div style="font-family:Arial,Helvetica,sans-serif;font-size:11px;line-height:17px;letter-spacing:2px;font-weight:bold;color:#667b5f;">MÃ XÁC THỰC CỦA BẠN</div>
                                                        <div class="otp-code" style="margin:13px 0 16px;font-family:'Courier New',Courier,monospace;font-size:39px;line-height:49px;letter-spacing:8px;font-weight:bold;white-space:nowrap;color:#304f39;">{{OTP}}</div>
                                                        <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:21px;color:#677866;">Mã có hiệu lực trong <strong style="color:#355a40;">{{EXPIRY}} phút</strong></div>
                                                    </td>
                                                </tr>
                                            </table>

                                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                                                   style="width:100%;margin-top:26px;border-collapse:collapse;">
                                                <tr>
                                                    <td width="4" style="width:4px;background-color:#c6ab80;font-size:0;line-height:0;">&nbsp;</td>
                                                    <td style="padding:13px 16px;background-color:#faf8f1;font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:22px;color:#655f4a;">
                                                        <strong style="color:#4c573b;">Lưu ý an toàn:</strong> {{WARNING}}
                                                    </td>
                                                </tr>
                                            </table>

                                            <p style="margin:30px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:22px;color:#667367;">Trân trọng,<br><strong style="color:#35513b;">Đội ngũ UTE TEA</strong></p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="mail-footer" style="padding:24px 40px 27px;border-top:1px solid #e9eee6;background-color:#f9fbf7;">
                                            <p style="margin:0 0 7px;font-family:Georgia,'Times New Roman',serif;font-size:15px;line-height:22px;letter-spacing:1px;font-weight:bold;color:#3b513f;">UTE TEA <span style="font-family:Arial,Helvetica,sans-serif;font-size:10px;letter-spacing:1px;font-weight:normal;color:#8a977f;">· SINCE 2026</span></p>
                                            <p style="margin:0 0 6px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:19px;color:#849082;">Số 1 Võ Văn Ngân, TP. Thủ Đức, TP. Hồ Chí Minh</p>
                                            <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:19px;color:#849082;">Đây là email tự động. Vui lòng không trả lời email này.</p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
<<<<<<< HEAD
                """;

        return html.replace("{{TITLE}}", HtmlUtils.htmlEscape(title))
                .replace("{{NAME}}", HtmlUtils.htmlEscape(recipientName))
                .replace("{{DESCRIPTION}}", HtmlUtils.htmlEscape(description))
                .replace("{{OTP}}", HtmlUtils.htmlEscape(code))
                .replace("{{EXPIRY}}", Integer.toString(expiryMinutes))
                .replace("{{WARNING}}", HtmlUtils.htmlEscape(warning));
=======
                """
                .formatted(title, org.springframework.web.util.HtmlUtils.htmlEscape(recipientName), description, code, expiryMinutes, warning);
>>>>>>> ai/ute-tea-fixes
    }

    /**
     * Che địa chỉ email để bảo mật thông tin khi log (ví dụ: kh***wk@gmail.com).
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String namePart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);

        if (namePart.length() <= 2) {
            return namePart.charAt(0) + "***" + domainPart;
        } else if (namePart.length() <= 4) {
            return namePart.charAt(0) + "***" + namePart.charAt(namePart.length() - 1) + domainPart;
        } else {
            return namePart.substring(0, 2) + "***" + namePart.substring(namePart.length() - 2) + domainPart;
        }
    }
}
