package com.su26isc301.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.su26isc301.backend.dto.request.ChatbotRequest;
import com.su26isc301.backend.dto.response.ChatMessageResponse;
import com.su26isc301.backend.dto.response.ChatbotResponse;
import com.su26isc301.backend.dto.response.ProductResponse;
import com.su26isc301.backend.entity.ChatMessage;
import com.su26isc301.backend.entity.ChatSession;
import com.su26isc301.backend.entity.Product;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.mapper.ProductMapper;
import com.su26isc301.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý logic AI Chatbot giới thiệu sản phẩm.
 * Mỗi user chỉ có 1 session duy nhất — tự động tìm hoặc tạo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final GeminiApiClient geminiApiClient;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProfileRepository profileRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_PRODUCTS_CONTEXT = 30;
    private static final int MAX_HISTORY_MESSAGES = 20; // Giới hạn lịch sử gửi cho AI

    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý mua sắm AI của sàn thương mại điện tử 5Bros. 
            
            PHẠM VI HOẠT ĐỘNG (RẤT QUAN TRỌNG):
            - Bạn CHỈ được phép trả lời các câu hỏi liên quan đến: sản phẩm trên sàn 5Bros, mua sắm, so sánh sản phẩm, tư vấn lựa chọn sản phẩm, giá cả, danh mục sản phẩm, đơn hàng, thanh toán, giao hàng.
            - Bạn TUYỆT ĐỐI KHÔNG trả lời các câu hỏi ngoài lề như: chính trị, tôn giáo, viết code, làm bài tập, toán học, khoa học, lịch sử, tin tức, chuyện cá nhân, hoặc bất kỳ chủ đề nào KHÔNG liên quan đến mua sắm trên 5Bros.
            - Nếu khách hỏi ngoài phạm vi, hãy từ chối lịch sự và hướng họ quay lại mua sắm. Ví dụ: "Xin lỗi, tôi chỉ có thể hỗ trợ bạn về sản phẩm và mua sắm trên 5Bros thôi ạ! 😊 Bạn đang tìm sản phẩm gì không?"
            
            NHIỆM VỤ:
            1. Giúp khách hàng tìm sản phẩm phù hợp dựa trên nhu cầu của họ.
            2. Giới thiệu sản phẩm một cách tự nhiên, thân thiện, bằng tiếng Việt.
            3. Khi giới thiệu sản phẩm, LUÔN kèm theo product ID để frontend hiển thị product card.
            4. Không bịa thông tin sản phẩm. Chỉ giới thiệu sản phẩm có trong danh sách được cung cấp.
            5. Nếu không tìm thấy sản phẩm phù hợp, hãy thông báo và gợi ý khách tìm theo cách khác.
            
            QUAN TRỌNG - Format trả về BẮT BUỘC là JSON:
            {
              "reply": "Nội dung phản hồi cho khách hàng (có thể dùng markdown)",
              "productIds": [1, 2, 3]
            }
            
            Quy tắc:
            - "reply": Phần text trả lời cho khách, viết tự nhiên, có thể kèm giải thích tại sao gợi ý sản phẩm đó.
            - "productIds": Mảng các product ID bạn muốn giới thiệu (tối đa 5 sản phẩm). Để mảng rỗng [] nếu không cần giới thiệu sản phẩm.
            - Chỉ trả về ID của sản phẩm CÓ TRONG danh sách được cung cấp.
            """;

    // ========================
    // CHAT (gửi tin nhắn + lưu lịch sử)
    // ========================

    @Transactional
    public ChatbotResponse chat(ChatbotRequest request, String userEmail) {
        try {
            Profile profile = profileRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy profile"));

            // Tìm session duy nhất của user, nếu chưa có thì tạo mới
            ChatSession session = getOrCreateSingleSession(profile);

            // Lưu tin nhắn user vào DB
            ChatMessage userMessage = ChatMessage.builder()
                    .chatSession(session)
                    .role("user")
                    .content(request.getMessage())
                    .build();
            chatMessageRepository.save(userMessage);

            // Load lịch sử từ DB
            List<ChatbotRequest.ChatMessage> history = loadHistoryFromDb(session.getId());

            // Lấy sản phẩm active từ DB làm context
            List<Product> activeProducts = productRepository.searchActiveProductsPageable(
                    null, null, null, PageRequest.of(0, MAX_PRODUCTS_CONTEXT)
            ).getContent();

            String productContext = buildProductContext(activeProducts);
            String enrichedMessage = buildEnrichedMessage(request.getMessage(), productContext);

            // Gọi Gemini API
            String aiResponse = geminiApiClient.generateContent(
                    SYSTEM_PROMPT,
                    history,
                    enrichedMessage
            );

            if (aiResponse == null || aiResponse.isBlank()) {
                return ChatbotResponse.builder()
                        .reply("Xin lỗi, tôi không thể xử lý yêu cầu lúc này. Vui lòng thử lại!")
                        .recommendedProducts(Collections.emptyList())
                        .build();
            }

            ChatbotResponse response = parseAiResponse(aiResponse, activeProducts);

            // Lưu tin nhắn AI vào DB
            ChatMessage aiMessage = ChatMessage.builder()
                    .chatSession(session)
                    .role("model")
                    .content(response.getReply())
                    .recommendedProductIds(extractProductIdsJson(aiResponse))
                    .build();
            chatMessageRepository.save(aiMessage);

            return response;

        } catch (RuntimeException e) {
            String errorMsg = e.getMessage();

            if ("RATE_LIMIT".equals(errorMsg)) {
                log.warn("Gemini API rate limit reached");
                return ChatbotResponse.builder()
                        .reply("⚠️ Chatbot đang quá tải do nhiều người sử dụng cùng lúc. Vui lòng đợi khoảng 1 phút rồi thử lại nhé! 🙏")
                        .recommendedProducts(Collections.emptyList())
                        .build();
            }

            if ("INVALID_API_KEY".equals(errorMsg)) {
                log.error("Gemini API key is invalid or expired");
                return ChatbotResponse.builder()
                        .reply("⚠️ Chatbot đang bảo trì, vui lòng quay lại sau. Xin lỗi vì sự bất tiện!")
                        .recommendedProducts(Collections.emptyList())
                        .build();
            }

            log.error("Lỗi xử lý chatbot: {}", e.getMessage(), e);
            return ChatbotResponse.builder()
                    .reply("Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại sau!")
                    .recommendedProducts(Collections.emptyList())
                    .build();
        } catch (Exception e) {
            log.error("Lỗi xử lý chatbot: {}", e.getMessage(), e);
            return ChatbotResponse.builder()
                    .reply("Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại sau!")
                    .recommendedProducts(Collections.emptyList())
                    .build();
        }
    }

    // ========================
    // LỊCH SỬ CHAT
    // ========================

    /**
     * Lấy toàn bộ lịch sử chat của user.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatHistory(String userEmail) {
        Profile profile = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy profile"));

        List<ChatSession> sessions = chatSessionRepository.findByProfileIdOrderByUpdatedAtDesc(profile.getId());
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        ChatSession session = sessions.get(0); // Lấy session duy nhất
        List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());

        return messages.stream()
                .map(m -> {
                    List<ProductResponse> products = Collections.emptyList();
                    if ("model".equals(m.getRole()) && m.getRecommendedProductIds() != null) {
                        products = loadProductsFromIds(m.getRecommendedProductIds());
                    }
                    return ChatMessageResponse.builder()
                            .id(m.getId())
                            .role(m.getRole())
                            .content(m.getContent())
                            .recommendedProducts(products)
                            .createdAt(m.getCreatedAt())
                            .build();
                })
                .toList();
    }

    /**
     * Xóa toàn bộ lịch sử chat của user (tạo session mới).
     */
    @Transactional
    public void clearChatHistory(String userEmail) {
        Profile profile = profileRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy profile"));

        List<ChatSession> sessions = chatSessionRepository.findByProfileIdOrderByUpdatedAtDesc(profile.getId());
        chatSessionRepository.deleteAll(sessions); // Cascade xóa luôn messages
    }

    // ========================
    // PRIVATE HELPERS
    // ========================

    /**
     * Tìm session duy nhất của user, hoặc tạo mới nếu chưa có.
     */
    private ChatSession getOrCreateSingleSession(Profile profile) {
        List<ChatSession> sessions = chatSessionRepository.findByProfileIdOrderByUpdatedAtDesc(profile.getId());

        if (!sessions.isEmpty()) {
            return sessions.get(0); // Trả về session duy nhất
        }

        // Tạo session mới
        ChatSession newSession = ChatSession.builder()
                .profile(profile)
                .title("Trò chuyện với 5Bros AI")
                .build();

        return chatSessionRepository.save(newSession);
    }

    /**
     * Load lịch sử chat từ DB → format cho Gemini.
     * Bỏ tin nhắn cuối (vừa save) và giới hạn số lượng.
     */
    private List<ChatbotRequest.ChatMessage> loadHistoryFromDb(Long sessionId) {
        List<ChatMessage> allMessages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId);

        if (allMessages.size() <= 1) {
            return Collections.emptyList();
        }

        // Lấy N tin nhắn gần nhất, bỏ tin cuối (tin mới nhất vừa save)
        List<ChatMessage> historyMessages = allMessages.subList(
                Math.max(0, allMessages.size() - 1 - MAX_HISTORY_MESSAGES),
                allMessages.size() - 1
        );

        return historyMessages.stream()
                .map(m -> new ChatbotRequest.ChatMessage(m.getRole(), m.getContent()))
                .toList();
    }

    private String extractProductIdsJson(String aiResponse) {
        try {
            JsonNode jsonNode = objectMapper.readTree(aiResponse);
            if (jsonNode.has("productIds") && jsonNode.get("productIds").isArray()) {
                return jsonNode.get("productIds").toString();
            }
        } catch (Exception e) {
            log.debug("Không thể parse productIds từ AI response");
        }
        return null;
    }

    private List<ProductResponse> loadProductsFromIds(String productIdsJson) {
        try {
            JsonNode ids = objectMapper.readTree(productIdsJson);
            if (!ids.isArray()) return Collections.emptyList();

            List<ProductResponse> products = new ArrayList<>();
            for (JsonNode idNode : ids) {
                productRepository.findByIdAndIsActiveTrue(idNode.asLong())
                        .ifPresent(p -> products.add(productMapper.mapToProductResponse(p)));
            }
            return products;
        } catch (Exception e) {
            log.debug("Không thể load products từ IDs: {}", productIdsJson);
            return Collections.emptyList();
        }
    }

    private String buildProductContext(List<Product> products) {
        if (products.isEmpty()) {
            return "Hiện tại chưa có sản phẩm nào trên sàn.";
        }

        StringBuilder sb = new StringBuilder("Danh sách sản phẩm hiện có trên sàn 5Bros:\n");
        for (Product p : products) {
            sb.append(String.format("- ID: %d | Tên: %s | Danh mục: %s",
                    p.getId(),
                    p.getName(),
                    p.getCategory() != null ? p.getCategory().getName() : "N/A"
            ));

            if (p.getVariants() != null && !p.getVariants().isEmpty()) {
                var firstVariant = p.getVariants().get(0);
                sb.append(String.format(" | Giá: %s VND", firstVariant.getPrice().toPlainString()));
                if (firstVariant.getDiscountPercent() != null && firstVariant.getDiscountPercent() > 0) {
                    sb.append(String.format(" (Giảm %d%%)", firstVariant.getDiscountPercent()));
                }
            }

            if (p.getAvgRating() != null) {
                sb.append(String.format(" | Rating: %s", p.getAvgRating().toPlainString()));
            }
            if (p.getSoldCount() != null && p.getSoldCount() > 0) {
                sb.append(String.format(" | Đã bán: %d", p.getSoldCount()));
            }

            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                String shortDesc = p.getDescription().length() > 100
                        ? p.getDescription().substring(0, 100) + "..."
                        : p.getDescription();
                shortDesc = shortDesc.replaceAll("<[^>]*>", "").trim();
                if (!shortDesc.isEmpty()) {
                    sb.append(String.format(" | Mô tả: %s", shortDesc));
                }
            }

            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildEnrichedMessage(String userMessage, String productContext) {
        return String.format("""
                [THÔNG TIN SẢN PHẨM TRÊN SÀN]
                %s
                
                [CÂU HỎI CỦA KHÁCH HÀNG]
                %s
                """, productContext, userMessage);
    }

    private ChatbotResponse parseAiResponse(String aiResponse, List<Product> availableProducts) {
        try {
            JsonNode jsonNode = objectMapper.readTree(aiResponse);

            String reply = jsonNode.has("reply") ? jsonNode.get("reply").asText("") : aiResponse;
            List<Long> productIds = new ArrayList<>();

            if (jsonNode.has("productIds") && jsonNode.get("productIds").isArray()) {
                for (JsonNode idNode : jsonNode.get("productIds")) {
                    productIds.add(idNode.asLong());
                }
            }

            List<ProductResponse> recommendedProducts = new ArrayList<>();
            if (!productIds.isEmpty()) {
                var productMap = availableProducts.stream()
                        .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

                for (Long id : productIds) {
                    Product product = productMap.get(id);
                    if (product != null) {
                        recommendedProducts.add(productMapper.mapToProductResponse(product));
                    }
                }

                if (recommendedProducts.size() < productIds.size()) {
                    List<Long> missingIds = productIds.stream()
                            .filter(id -> !productMap.containsKey(id))
                            .toList();
                    for (Long missingId : missingIds) {
                        productRepository.findByIdAndIsActiveTrue(missingId)
                                .ifPresent(p -> recommendedProducts.add(productMapper.mapToProductResponse(p)));
                    }
                }
            }

            return ChatbotResponse.builder()
                    .reply(reply)
                    .recommendedProducts(recommendedProducts)
                    .build();

        } catch (Exception e) {
            log.warn("Không thể parse JSON từ Gemini, trả về raw text: {}", e.getMessage());
            return ChatbotResponse.builder()
                    .reply(aiResponse)
                    .recommendedProducts(Collections.emptyList())
                    .build();
        }
    }
}
