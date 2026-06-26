package com.su26isc301.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.su26isc301.backend.dto.request.ChatbotRequest;
import com.su26isc301.backend.dto.response.ChatbotResponse;
import com.su26isc301.backend.entity.*;
import com.su26isc301.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorChatbotService {

    private final GeminiApiClient geminiApiClient;
    private final ChatMessageRepository chatMessageRepository;
    private final ProfileRepository profileRepository;
    private final VendorRepository vendorRepository;
    private final VendorWalletRepository vendorWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PostPromotionRepository postPromotionRepository;
    private final ProductRepository productRepository;
    private final VendorReputationRepository vendorReputationRepository;
    private final VendorSubscriptionPlanRepository vendorSubscriptionPlanRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_HISTORY_MESSAGES = 20;

    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý phân tích kinh doanh AI của sàn thương mại điện tử 5Bros dành cho nhà bán hàng (Vendor).
            
            NHIỆM VỤ CỦA BẠN:
            1. Phân tích báo cáo tài chính (số dư ví, tổng nạp, tổng chi, lịch sử giao dịch).
            2. Phân tích ROI (Tỷ suất hoàn vốn) cho từng bài đăng quảng cáo (budget, spent, clicks, CPC).
            3. Đánh giá hiệu quả sản phẩm (lượt xem, số lượng bán, đánh giá trung bình).
            4. Đánh giá độ uy tín của shop (đơn thành công, đơn hủy, điểm uy tín).
            5. Tư vấn chiến lược kinh doanh, tối ưu quảng cáo và tăng doanh số dựa trên DATA THỰC TẾ được cung cấp.
            
            PHẠM VI HOẠT ĐỘNG:
            - CHỈ trả lời các câu hỏi liên quan đến cửa hàng của vendor, phân tích kinh doanh, quảng cáo, và sản phẩm trên 5Bros.
            - TUYỆT ĐỐI KHÔNG trả lời các câu hỏi ngoài lề (code, lịch sử, chính trị, v.v.). Nếu bị hỏi ngoài lề, hãy từ chối lịch sự và hướng họ quay lại công việc kinh doanh.
            
            QUAN TRỌNG - Format trả về BẮT BUỘC là JSON:
            {
              "reply": "Nội dung phân tích chi tiết (có thể dùng markdown, bảng biểu để trình bày data dễ nhìn)",
              "productIds": [1, 2, 3]
            }
            
            Quy tắc:
            - "reply": Nội dung bạn trả lời cho vendor. Phân tích số liệu thật kỹ và đưa ra insight hữu ích.
            - "productIds": Mảng ID của các sản phẩm của vendor mà bạn đang phân tích/nhắc đến (nếu có). Để rỗng [] nếu không nhắc đến sản phẩm cụ thể.
            """;

    @Transactional
    public ChatbotResponse chat(ChatbotRequest request, String userEmail) {
        try {
            Profile profile = profileRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy profile"));

            Vendor vendor = vendorRepository.findByProfileId(profile.getId())
                    .orElseThrow(() -> new RuntimeException("Bạn chưa đăng ký làm nhà bán hàng (Vendor)"));

            // 1. Lưu tin nhắn của vendor vào DB
            ChatMessage userMessage = ChatMessage.builder()
                    .profile(profile)
                    .role("user")
                    .content(request.getMessage())
                    .build();
            chatMessageRepository.save(userMessage);

            // 2. Load lịch sử chat
            List<ChatbotRequest.ChatMessage> history = loadHistory(profile.getId());

            // 3. Build data context cho vendor
            String vendorContext = buildVendorContext(vendor);
            String enrichedMessage = String.format("""
                    [DỮ LIỆU CỬA HÀNG CỦA TÔI]
                    %s
                    
                    [CÂU HỎI CỦA TÔI]
                    %s
                    """, vendorContext, request.getMessage());

            // 4. Gọi Gemini
            String aiResponse = geminiApiClient.generateContent(
                    SYSTEM_PROMPT,
                    history,
                    enrichedMessage
            );

            if (aiResponse == null || aiResponse.isBlank()) {
                return ChatbotResponse.builder()
                        .reply("Xin lỗi, tôi không thể phân tích dữ liệu lúc này. Vui lòng thử lại sau!")
                        .recommendedProducts(Collections.emptyList())
                        .build();
            }

            ChatbotResponse response = parseAiResponse(aiResponse);

            // 5. Lưu phản hồi của AI
            ChatMessage aiMessage = ChatMessage.builder()
                    .profile(profile)
                    .role("model")
                    .content(response.getReply())
                    .recommendedProductIds(extractProductIdsJson(aiResponse))
                    .build();
            chatMessageRepository.save(aiMessage);

            return response;

        } catch (RuntimeException e) {
            log.error("Lỗi xử lý vendor chatbot: {}", e.getMessage());
            return ChatbotResponse.builder()
                    .reply("Lỗi: " + e.getMessage())
                    .recommendedProducts(Collections.emptyList())
                    .build();
        } catch (Exception e) {
            log.error("Lỗi xử lý vendor chatbot", e);
            return ChatbotResponse.builder()
                    .reply("Đã có lỗi hệ thống xảy ra.")
                    .recommendedProducts(Collections.emptyList())
                    .build();
        }
    }

    private String buildVendorContext(Vendor vendor) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("- Tên Shop: %s\n", vendor.getShopName()));

        // Thông tin ví quảng cáo
        vendorWalletRepository.findByVendorId(vendor.getId()).ifPresent(wallet -> {
            sb.append("\n[TÀI CHÍNH - VÍ QUẢNG CÁO]\n");
            sb.append(String.format("- Số dư khả dụng: %s VND\n", wallet.getAvailableBalance()));
            sb.append(String.format("- Số dư đang khóa: %s VND\n", wallet.getLockedBalance()));
            sb.append(String.format("- Tổng tiền đã nạp: %s VND\n", wallet.getTotalDeposited()));
            sb.append(String.format("- Tổng tiền đã tiêu: %s VND\n", wallet.getTotalSpent()));
        });

        // Uy tín shop (Reputation)
        vendorReputationRepository.findById(vendor.getId()).ifPresent(rep -> {
            sb.append("\n[UY TÍN SHOP (REPUTATION)]\n");
            sb.append(String.format("- Điểm uy tín: %s\n", rep.getReputationScore()));
            sb.append(String.format("- Rating trung bình: %s (từ %d lượt đánh giá)\n", rep.getRatingAverage(), rep.getRatingCount()));
            sb.append(String.format("- Số đơn thành công: %d\n", rep.getCompletedOrders()));
            sb.append(String.format("- Số đơn bị hủy: %d\n", rep.getCancelledOrders()));
            sb.append(String.format("- Số khiếu nại: %d\n", rep.getComplaintCount()));
        });

        // Gói đăng ký (Subscription Plan)
        Optional<VendorSubscriptionPlan> planOpt = vendorSubscriptionPlanRepository.findByVendorId(vendor.getId());
        if (planOpt.isPresent()) {
            VendorSubscriptionPlan currentPlan = planOpt.get();
            sb.append("\n[GÓI ĐĂNG KÝ SHOP]\n");
            sb.append(String.format("- Loại gói: %s\n", currentPlan.getPlanType()));
            sb.append(String.format("- Số slot sản phẩm đang dùng / tổng: %d / %d\n", currentPlan.getUsedSlots(), currentPlan.getTotalSlots()));
        }

        // Lịch sử giao dịch (Top 5)
        List<WalletTransaction> txns = walletTransactionRepository.findByVendorIdOrderByCreatedAtDesc(vendor.getId());
        if (!txns.isEmpty()) {
            sb.append("\n[5 GIAO DỊCH VÍ GẦN NHẤT]\n");
            int count = 0;
            for (WalletTransaction tx : txns) {
                if (count >= 5) break;
                sb.append(String.format("- [%s] Loại: %s | Số tiền: %s VND | Trạng thái: %s\n",
                        tx.getCreatedAt() != null ? tx.getCreatedAt().toLocalDate() : "N/A",
                        tx.getType(),
                        tx.getAmount(),
                        tx.getStatus()));
                count++;
            }
        }

        // Bài đăng quảng cáo (Promotions)
        List<PostPromotion> promotions = postPromotionRepository.findByVendorId(vendor.getId());
        if (!promotions.isEmpty()) {
            sb.append("\n[HIỆU QUẢ CHẠY QUẢNG CÁO (ADS ROI)]\n");
            for (PostPromotion p : promotions) {
                sb.append(String.format("- ID: %d | Sản phẩm ID: %d | Trạng thái: %s\n", p.getId(), p.getProduct().getId(), p.getStatus()));
                sb.append(String.format("  + Ngân sách: %s VND | Đã tiêu: %s VND\n", p.getInitialBudget(), p.getSpentAmount()));
                sb.append(String.format("  + CPC (Giá mỗi click): %s VND\n", p.getRoiPerClick()));
                sb.append(String.format("  + Lượt Click thật / Dự kiến: %d / %d\n", p.getCustomerClicks(), p.getEstimatedClicks()));
            }
        }

        // Danh sách sản phẩm (Top 10 mới nhất)
        List<Product> products = productRepository.findByVendorId(vendor.getId());
        if (!products.isEmpty()) {
            sb.append("\n[DANH SÁCH SẢN PHẨM CỦA SHOP (Top 10)]\n");
            int count = 0;
            for (Product p : products) {
                if (count >= 10) break;
                sb.append(String.format("- ID: %d | Tên: %s\n", p.getId(), p.getName()));
                sb.append(String.format("  + Trạng thái: %s | Đang bán: %s\n", p.getStatus(), p.getIsActive() != null && p.getIsActive() ? "Có" : "Không"));
                sb.append(String.format("  + Đã bán: %d cái | Lượt xem: %d | Rating: %s\n", p.getSoldCount(), p.getViewCount(), p.getAvgRating()));
                count++;
            }
        }

        return sb.toString();
    }

    private List<ChatbotRequest.ChatMessage> loadHistory(UUID profileId) {
        List<ChatMessage> allMessages = chatMessageRepository.findByProfileIdOrderByCreatedAtAsc(profileId);
        if (allMessages.size() <= 1) return Collections.emptyList();
        
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

    private ChatbotResponse parseAiResponse(String aiResponse) {
        try {
            JsonNode jsonNode = objectMapper.readTree(aiResponse);
            String reply = jsonNode.has("reply") ? jsonNode.get("reply").asText("") : aiResponse;
            // Ở đây chatbot vendor không trả về danh sách ProductResponse detail như buyer
            // mà frontend có thể không cần hiển thị product card cho vendor chatbot, hoặc có thể custom sau.
            return ChatbotResponse.builder()
                    .reply(reply)
                    .recommendedProducts(Collections.emptyList())
                    .build();
        } catch (Exception e) {
            return ChatbotResponse.builder()
                    .reply(aiResponse)
                    .recommendedProducts(Collections.emptyList())
                    .build();
        }
    }
}
