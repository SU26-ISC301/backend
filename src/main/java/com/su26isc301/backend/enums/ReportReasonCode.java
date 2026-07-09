package com.su26isc301.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportReasonCode {
    MISLEADING_LISTING("Sản phẩm/bài đăng sai mô tả", ReportPriority.MEDIUM),
    FAKE_OR_COUNTERFEIT("Nghi ngờ hàng giả/hàng nhái", ReportPriority.HIGH),
    SCAM_OR_FRAUD("Người bán có dấu hiệu lừa đảo", ReportPriority.HIGH),
    OFF_PLATFORM_TRANSACTION("Lôi kéo giao dịch ngoài sàn", ReportPriority.HIGH),
    SPAM_OR_HARASSMENT("Spam hoặc gây phiền trong chat", ReportPriority.MEDIUM),
    ABUSIVE_LANGUAGE("Ngôn từ xúc phạm/đe dọa", ReportPriority.HIGH),
    FALSE_INFORMATION("Shop dùng thông tin/hình ảnh sai sự thật", ReportPriority.MEDIUM),
    PROHIBITED_ITEM("Sản phẩm bị cấm/hạn chế", ReportPriority.HIGH),
    PRICE_OR_PROMOTION_MISLEADING("Giá/khuyến mãi gây hiểu nhầm", ReportPriority.MEDIUM),
    IMPERSONATION("Mạo danh shop/người bán khác", ReportPriority.HIGH),
    OTHER("Khác", ReportPriority.LOW);

    private final String label;
    private final ReportPriority defaultPriority;
}
