package yowyob.comops.api.common.domain.model;

public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;

    private ApiResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, code + ": " + message);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, null, code + ": " + message);
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getMessage() { return message; }
}
