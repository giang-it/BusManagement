package giang.com.BusManagement.dto;

/**
 * PHASE 8 — nút thắt năng lực trong một kịch bản What-if.
 *
 * Trả lời câu hỏi vận hành "mua xe hay tuyển tài xế?": khi nhu cầu vượt năng lực
 * phục vụ, phía nào (xe hay tài xế) là trần nhỏ hơn thì đó là chỗ cần bổ sung.
 * Là ENUM, không bao giờ suy từ chuỗi — giống RecommendationStatus.
 */
public enum WhatIfBottleneck {

    BUS("Thiếu xe — cân nhắc bổ sung xe"),
    DRIVER("Thiếu tài xế — cân nhắc tuyển thêm tài xế"),
    BALANCED("Xe và tài xế bó ngang nhau"),
    NONE("Đủ năng lực — không bị bó");

    private final String label;

    WhatIfBottleneck(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
