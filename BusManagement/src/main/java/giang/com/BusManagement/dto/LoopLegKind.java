package giang.com.BusManagement.dto;

/**
 * Route pairs CT7 (QĐ-22, phương án PA-B) — LƯỢT ĐI KÈM của một chuyến thêm, tức lượt chạy mà
 * chiếc xe tăng cường buộc phải chạy thêm vì chuyến thêm làm nó rời bến nhà.
 *
 * <p>Lãi của một chuyến thêm là lãi CẢ VÒNG: tiền vé của chuyến thêm và của lượt đi kèm, trừ chi
 * phí của cả hai. Lượt đi kèm đi cùng giờ một chuyến thường lệ của chiều ngược, và chở phần khách
 * còn tràn của khung đó.
 */
public enum LoopLegKind {

    /**
     * Không cần lượt đi kèm: xe đang đứng ở bến xa và chuyến thêm đưa nó về đúng bến nhà. Ví dụ xe
     * bến nhà Sài Gòn đang rảnh ở Đà Lạt chạy chuyến thêm Đà Lạt → Sài Gòn. Cả vòng = một lượt.
     */
    NONE,

    /**
     * "Về ngay" (luật H1 của lịch sử, QĐ-21 (d)): xe rời bến nhà bằng chuyến thêm, rồi quay về bằng
     * khung chiều ngược SỚM NHẤT mà xe (nghỉ ≥ 1 giờ) và một tài xế đều kịp.
     */
    RETURN,

    /**
     * "Đưa xe tới trước": bến đi của chuyến thêm không có xe rảnh, nên một xe từ đầu kia đi cùng khung
     * chiều ngược MUỘN NHẤT còn kịp, rồi chạy chuyến thêm và kết thúc ở chỗ nó xuất phát.
     */
    BRING,

    /**
     * Chưa xếp được lượt đi kèm cụ thể — tuyến không có đúng một tuyến chiều ngược, hay trong 2 ngày
     * không có khung nào xe và tài xế kịp — nên tính như MỘT LƯỢT VỀ RỖNG: cộng chi phí một lượt, không
     * tiền vé. Không bao giờ nói lãi quá tay.
     */
    ASSUMED_RETURN
}
