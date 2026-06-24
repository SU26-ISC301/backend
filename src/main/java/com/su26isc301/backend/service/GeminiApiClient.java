package com.su26isc301.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.su26isc301.backend.dto.request.ChatbotRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Client gọi Google Gemini API (gemini-2.0-flash).
 * Dùng RestClient (Spring Boot 4.x) để gửi HTTP POST.
 */
@Slf4j
@Service
public class GeminiApiClient {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiApiClient() {
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    /**
     * Gọi Gemini API để generate nội dung.
     *
     * @param systemPrompt System instruction cho AI
     * @param history      Lịch sử chat (role: "user" hoặc "model")
     * @param userMessage  Tin nhắn mới nhất của user
     * @return Response text từ Gemini
     */
    public String generateContent(String systemPrompt, List<ChatbotRequest.ChatMessage> history, String userMessage) {
        try {
            // Build request body theo Gemini API format
            ObjectNode requestBody = objectMapper.createObjectNode();

            // System instruction
            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ObjectNode systemPart = objectMapper.createObjectNode();
            systemPart.put("text", systemPrompt);
            ArrayNode systemParts = objectMapper.createArrayNode();
            systemParts.add(systemPart);
            systemInstruction.set("parts", systemParts);
            requestBody.set("system_instruction", systemInstruction);

            // Contents (history + current message)
            ArrayNode contents = objectMapper.createArrayNode();

            // Thêm lịch sử chat
            if (history != null) {
                for (ChatbotRequest.ChatMessage msg : history) {
                    ObjectNode content = objectMapper.createObjectNode();
                    content.put("role", msg.getRole()); // "user" hoặc "model"
                    ArrayNode parts = objectMapper.createArrayNode();
                    ObjectNode part = objectMapper.createObjectNode();
                    part.put("text", msg.getContent());
                    parts.add(part);
                    content.set("parts", parts);
                    contents.add(content);
                }
            }

            // Thêm tin nhắn hiện tại
            ObjectNode currentContent = objectMapper.createObjectNode();
            currentContent.put("role", "user");
            ArrayNode currentParts = objectMapper.createArrayNode();
            ObjectNode currentPart = objectMapper.createObjectNode();
            currentPart.put("text", userMessage);
            currentParts.add(currentPart);
            currentContent.set("parts", currentParts);
            contents.add(currentContent);

            requestBody.set("contents", contents);

            // Generation config
            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 2048);
            generationConfig.put("responseMimeType", "application/json");
            requestBody.set("generation_config", generationConfig);

            String requestJson = objectMapper.writeValueAsString(requestBody);
            log.debug("Gemini request: {}", requestJson);

            // Gọi API — xử lý riêng lỗi 429 (rate limit)
            String responseJson = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int statusCode = res.getStatusCode().value();
                        String body = new String(res.getBody().readAllBytes());
                        log.error("Gemini API error {}: {}", statusCode, body);

                        if (statusCode == 429) {
                            throw new RuntimeException("RATE_LIMIT");
                        } else if (statusCode == 401 || statusCode == 403) {
                            throw new RuntimeException("INVALID_API_KEY");
                        } else {
                            throw new RuntimeException("Gemini API error " + statusCode + ": " + body);
                        }
                    })
                    .body(String.class);

            log.debug("Gemini response: {}", responseJson);

            // Parse response → lấy text
            JsonNode responseNode = objectMapper.readTree(responseJson);
            JsonNode candidates = responseNode.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode firstCandidate = candidates.get(0);
                JsonNode contentNode = firstCandidate.path("content").path("parts");
                if (contentNode.isArray() && !contentNode.isEmpty()) {
                    return contentNode.get(0).path("text").asText("");
                }
            }

            log.warn("Gemini returned empty response");
            return null;

        } catch (RuntimeException e) {
            // Re-throw rate limit and API key errors with their specific messages
            if ("RATE_LIMIT".equals(e.getMessage()) || "INVALID_API_KEY".equals(e.getMessage())) {
                throw e;
            }
            log.error("Lỗi khi gọi Gemini API: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể kết nối đến AI service: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Lỗi khi gọi Gemini API: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể kết nối đến AI service: " + e.getMessage(), e);
        }
    }
}
