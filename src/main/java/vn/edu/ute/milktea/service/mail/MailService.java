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

    @Value("${app.mail.from:${spring.mail.username:khoalkopwk@gmail.com}}")
    private String mailFrom;

    @Value("${app.mail.sender-name:UTE Tea}")
    private String senderName;

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
                "Cảm ơn bạn đã lựa chọn UTE TEA! Vui lòng sử dụng mã xác thực dưới đây để hoàn tất kích hoạt tài khoản của bạn:",
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
                "UTE TEA nhận được yêu cầu đặt lại mật khẩu cho tài khoản liên kết với địa chỉ email này. Vui lòng sử dụng mã xác thực dưới đây:",
                resetCode,
                expiryMinutes,
                "Lưu ý quan trọng: Không cung cấp mã này cho bất kỳ ai, kể cả nhân viên quán. Nếu bạn không gửi yêu cầu này, hãy đổi mật khẩu ngay lập tức hoặc bỏ qua email.");

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    /**
     * Thực hiện gửi mail qua SMTP và ném lỗi rõ ràng nếu thất bại.
     */
    private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
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
        } catch (MessagingException | MailException e) {
            log.error("Lỗi gửi email SMTP tới {}: {}", maskEmail(toEmail), e.getMessage());
            throw BusinessException.badRequest(
                    ErrorCode.MAIL_SEND_FAILED,
                    "Không thể gửi email xác thực qua hệ thống SMTP. Vui lòng thử lại sau giây lát hoặc kiểm tra kết nối mạng.");
        } catch (Exception e) {
            log.error("Lỗi không mong muốn khi gửi email tới {}: {}", maskEmail(toEmail), e.getMessage());
            throw BusinessException.badRequest(
                    ErrorCode.MAIL_SEND_FAILED,
                    "Gặp lỗi khi tạo thư điện tử xác thực: " + e.getMessage());
        }
    }

    /**
     * Email template UTE TEA: bảng HTML + inline style cho Gmail/Outlook/mobile.
     * Tên người nhận, mã OTP và văn bản động được HTML-escape trước khi chèn.
     * Logo được nhúng bằng Content-ID (cid:uteTeaLogo), không cần URL công khai.
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
                """;

        return html.replace("{{TITLE}}", HtmlUtils.htmlEscape(title))
                .replace("{{NAME}}", HtmlUtils.htmlEscape(recipientName))
                .replace("{{DESCRIPTION}}", HtmlUtils.htmlEscape(description))
                .replace("{{OTP}}", HtmlUtils.htmlEscape(code))
                .replace("{{EXPIRY}}", Integer.toString(expiryMinutes))
                .replace("{{WARNING}}", HtmlUtils.htmlEscape(warning));
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
