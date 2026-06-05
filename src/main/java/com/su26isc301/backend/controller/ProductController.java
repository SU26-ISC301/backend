package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.ProductCreateRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.ProductResponse;
import com.su26isc301.backend.service.ProductService;
import com.su26isc301.backend.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final SupabaseStorageService supabaseStorageService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            Authentication authentication,
            @RequestBody ProductCreateRequest request
    ) {
        String email = authentication.getName();
        ProductResponse response = productService.createProduct(email, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo sản phẩm thành công", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        ProductResponse response = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin sản phẩm thành công", response));
    }

    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByVendor(@PathVariable Long vendorId) {
        List<ProductResponse> responses = productService.getProductsByVendor(vendorId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", responses));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody ProductCreateRequest request
    ) {
        String email = authentication.getName();
        ProductResponse response = productService.updateProduct(email, id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(value = "hard", defaultValue = "false") boolean hard
    ) {
        String email = authentication.getName();
        productService.deleteProduct(email, id, hard);
        String msg = hard ? "Xóa cứng sản phẩm thành công" : "Xóa mềm sản phẩm thành công";
        return ResponseEntity.ok(ApiResponse.success(msg, null));
    }

    @PostMapping("/upload-media")
    public ResponseEntity<ApiResponse<List<String>>> uploadProductMedia(
            @RequestParam("files") MultipartFile[] files
    ) {
        List<String> uploadedUrls = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    String url = supabaseStorageService.uploadFile(file, "product-media");
                    uploadedUrls.add(url);
                }
            }
            return ResponseEntity.ok(ApiResponse.success("Upload ảnh/video sản phẩm thành công", uploadedUrls));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Lỗi khi upload file: " + e.getMessage()));
        }
    }
}