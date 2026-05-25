package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.CccdVerificationResponse;
import com.su26isc301.backend.dto.response.FptCccdOcrResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FptCccdVerificationService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    @Value("${fpt.ai.api-key:}")
    private String apiKey;

    @Value("${fpt.ai.idr.endpoint:https://api.fpt.ai/vision/idr/vnm}")
    private String idrEndpoint;

    @Value("${fpt.ai.api-key-header:api_key}")
    private String apiKeyHeader;

    private final RestTemplate restTemplate = new RestTemplate();

    public CccdVerificationResponse verify(MultipartFile frontImage, MultipartFile backImage) {
        validateConfig();
        validateImage(frontImage, "frontImage");
        validateImage(backImage, "backImage");

        FptCccdOcrResult front = recognize(frontImage);
        FptCccdOcrResult back = recognize(backImage);

        boolean frontSideValid = front.isSuccess() && "front".equals(front.getSide());
        boolean backSideValid = back.isSuccess() && "back".equals(back.getSide());
        boolean verified = frontSideValid && backSideValid;

        return CccdVerificationResponse.builder()
                .verified(verified)
                .message(buildVerificationMessage(front, back, frontSideValid, backSideValid))
                .cccdNumber(front.getCardNumber())
                .frontSideValid(frontSideValid)
                .backSideValid(backSideValid)
                .front(front)
                .back(back)
                .build();
    }

    private FptCccdOcrResult recognize(MultipartFile image) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set(apiKeyHeader, apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", toImagePart(image));

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    idrEndpoint,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<String, Object> raw = response.getBody();
            if (raw == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "FPT.AI không trả dữ liệu");
            }

            Integer errorCode = toInteger(raw.get("errorCode"));
            String errorMessage = Optional.ofNullable(raw.get("errorMessage"))
                    .map(Object::toString)
                    .orElse("");
            Map<String, Object> extractedData = firstDataItem(raw);
            String type = firstNonBlank(extractedData, "type_new", "type");

            return FptCccdOcrResult.builder()
                    .success(Integer.valueOf(0).equals(errorCode))
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .cardNumber(firstNonBlank(extractedData, "id"))
                    .cardType(type)
                    .side(detectSide(type))
                    .extractedData(extractedData)
                    .rawResponse(raw)
                    .build();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được file ảnh: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không gọi được FPT.AI: " + e.getMessage(), e);
        }
    }

    private HttpEntity<ByteArrayResource> toImagePart(MultipartFile image) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(image.getBytes()) {
            @Override
            public String getFilename() {
                return image.getOriginalFilename() == null ? "cccd.jpg" : image.getOriginalFilename();
            }
        };

        HttpHeaders partHeaders = new HttpHeaders();
        MediaType contentType = image.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(image.getContentType());
        partHeaders.setContentType(contentType);

        return new HttpEntity<>(resource, partHeaders);
    }

    private void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Thiếu FPT_AI_API_KEY trong file .env hoặc biến môi trường"
            );
        }
    }

    private void validateImage(MultipartFile image, String fieldName) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu ảnh " + fieldName);
        }
        if (image.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " vượt quá 5MB theo giới hạn FPT.AI");
        }
        String contentType = image.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " phải là file ảnh");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstDataItem(Map<String, Object> raw) {
        Object data = raw.get("data");
        if (data instanceof List<?> items && !items.isEmpty() && items.get(0) instanceof Map<?, ?> first) {
            return (Map<String, Object>) first;
        }
        return Map.of();
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNonBlank(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank() && !"N/A".equalsIgnoreCase(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private String detectSide(String type) {
        if (type == null) {
            return null;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("back")) {
            return "back";
        }
        if (normalized.contains("front") || "new".equals(normalized) || "old".equals(normalized)) {
            return "front";
        }
        return null;
    }

    private String buildVerificationMessage(
            FptCccdOcrResult front,
            FptCccdOcrResult back,
            boolean frontSideValid,
            boolean backSideValid
    ) {
        if (!front.isSuccess()) {
            return "FPT.AI không OCR được mặt trước: " + front.getErrorMessage();
        }
        if (!back.isSuccess()) {
            return "FPT.AI không OCR được mặt sau: " + back.getErrorMessage();
        }
        if (!frontSideValid) {
            return "Ảnh frontImage không phải mặt trước CCCD/CMND";
        }
        if (!backSideValid) {
            return "Ảnh backImage không phải mặt sau CCCD/CMND";
        }
        return "Xác thực OCR CCCD thành công";
    }
}
