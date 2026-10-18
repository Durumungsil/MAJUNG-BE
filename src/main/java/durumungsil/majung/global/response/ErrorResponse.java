package durumungsil.majung.global.response;

/**
 * 공통 실패 응답. { "success": false, "error_code": "404", "message": "..." }
 */
public record ErrorResponse(boolean success, String errorCode, String message) {

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(false, String.valueOf(status), message);
    }
}
