package vn.edu.ute.milktea.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@lombok.extern.slf4j.Slf4j
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> standardError(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.builder().success(false).traceId(UUID.randomUUID().toString())
                .error(ErrorResponse.ErrorDetail.builder().code(code.name()).message(message).build()).build());
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception ex) {
        return standardError(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Dữ liệu, tham số hoặc tệp tải lên không hợp lệ");
    }

    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleDataConflict(Exception ex) {
        return standardError(HttpStatus.CONFLICT, ErrorCode.ORDER_STATE_CONFLICT, "Dữ liệu đã thay đổi hoặc bị trùng. Vui lòng tải lại và thử lại.");
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .traceId(traceId)
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getErrorCode().name())
                        .message(ex.getMessage())
                        .fieldErrors(ex.getFieldErrors())
                        .build())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .traceId(UUID.randomUUID().toString())
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_FAILED.name())
                        .message("Dữ liệu đầu vào không hợp lệ")
                        .fieldErrors(fieldErrors)
                        .build())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .traceId(UUID.randomUUID().toString())
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ErrorCode.ACCESS_DENIED.name())
                        .message("Bạn không có quyền thực hiện thao tác này")
                        .build())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .traceId(UUID.randomUUID().toString())
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ErrorCode.AUTH_INVALID_CREDENTIALS.name())
                        .message("Thông tin xác thực không hợp lệ")
                        .build())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled application failure [traceId={}] path={}", traceId, request.getRequestURI(), ex);
        String accept = request.getHeader("Accept");
        String path = request.getRequestURI();
        if (accept != null && accept.contains("text/html") && !path.startsWith("/api/")) {
            String html = "<!doctype html><html lang=\"vi\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>UTE Tea - Tạm thời gián đoạn</title><style>body{margin:0;background:#faf9f3;"
                + "font:16px/1.6 system-ui,sans-serif;color:#303a2d;display:grid;place-items:center;min-height:100vh}"
                + "main{max-width:550px;padding:36px;background:#fff;border-radius:20px;box-shadow:0 12px 36px #263a2914}"
                + "a{display:inline-block;background:#565c45;color:white;padding:10px 18px;border-radius:10px;text-decoration:none}"
                + "small{color:#697166}</style></head><body><main><h1>UTE Tea</h1>"
                + "<h2>Trang đang tạm thời gián đoạn</h2><p>Không thể tải nội dung lúc này. "
                + "Vui lòng thử lại sau hoặc liên hệ quản trị viên.</p><p><a href=\"/\">Về trang chủ</a></p>"
                + "<small>Mã lỗi: " + traceId + "</small></main></body></html>";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_HTML).body(html);
        }
        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .traceId(traceId)
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ErrorCode.INTERNAL_ERROR.name())
                        .message("Đã xảy ra lỗi hệ thống. Vui lòng thử lại hoặc cung cấp mã lỗi cho quản trị viên.")
                        .build())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
