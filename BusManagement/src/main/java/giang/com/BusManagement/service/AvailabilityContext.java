package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Trip;

import java.util.List;

/**
 * PHASE 7 (bước 2) — NGUỒN DỮ LIỆU đã preload cho việc chọn tài nguyên trong bộ
 * nhớ.
 *
 * <p><b>Chỉ là dữ liệu, KHÔNG chứa luật.</b> Recommendation Engine cần chọn xe +
 * tài xế cho hàng chục chuyến ứng viên; nếu mỗi lần chọn lại truy vấn DB từng xe
 * / từng tài xế (như đường phân công trực tiếp làm cho MỘT chuyến) thì số truy
 * vấn bùng nổ. Context này nạp một lần toàn bộ lịch liên quan (xem
 * {@link TripService#buildAvailabilityContext}), rồi các method chọn tài nguyên
 * của {@link TripService} đọc "bận / giờ lái" từ đây thay vì DB.
 *
 * <p>Quan trọng: mọi QUY TẮC (trạng thái nào tính là bận, ngưỡng giờ lái, bảo
 * trì, bằng lái, thứ tự ưu tiên...) vẫn nằm nguyên trong {@link TripService}.
 * Lớp này không quyết định gì — nó chỉ trả về danh sách chuyến đã nạp để luật
 * đánh giá trên đó. Việc predicate giao khoảng thời gian được đánh giá bằng Java
 * (thay vì SQL) được chốt tương đương với điều kiện SQL bởi
 * {@code TripServiceAvailabilityContextTest}.
 */
public final class AvailabilityContext {

    private final List<Trip> trips;

    public AvailabilityContext(List<Trip> trips) {
        this.trips = (trips != null) ? trips : List.of();
    }

    /** Các chuyến đã preload trong khoảng đang xét (chưa hủy). Không bao giờ null. */
    public List<Trip> trips() {
        return trips;
    }
}
