package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private boolean success;   // Trạng thái (true: thành công, false: thất bại)
    private String message;    // Thông điệp phản hồi (ví dụ: "Đăng nhập thành công")
    private T data;            // Dữ liệu trả về (có thể là Object, List, String, Integer hoặc null)

    // Hàm tiện ích để tạo nhanh phản hồi THÀNH CÔNG có kèm dữ liệu
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // Hàm tiện ích để tạo nhanh phản hồi THÀNH CÔNG không cần trả dữ liệu (ví dụ: Xóa thành công)
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(null)
                .build();
    }

    // Hàm tiện ích để tạo nhanh phản hồi THẤT BẠI / LỖI
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();
    }
}
