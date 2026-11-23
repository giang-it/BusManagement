package giang.com.BusManagement.domain;

import java.util.List;

/**
 * Lịch thường lệ (route pairs CT6; QĐ-20, CM-4 trong docs/development/route_pairs_decisions.md).
 *
 * <p>Viết ĐÚNG MỘT CHỖ cho cả hai nơi dùng:
 * <ul>
 * <li>bộ sinh lịch sử ({@code config/HistorySimulation}, {@code config/HistoricalDataBackfill}) —
 * lịch sử mà dự báo học;</li>
 * <li>app ({@code service/RegularTimetableService}) — chuyến thường lệ thật của những ngày tới.</li>
 * </ul>
 * Hai nơi cùng giờ và cùng tập tuyến thì khung giờ dự báo (tuyến × giờ khởi hành) luôn khớp với
 * chuyến thật, và màn Đề xuất luôn có dự báo cho mọi khung của lịch.
 *
 * <p>Là hằng số, không phải bảng trong DB (CM-4 cách 1): đổi được giờ của từng tuyến thì lịch sử
 * và dự báo, vốn chia theo khung 06/12/18, sẽ không còn khung tương ứng — cùng lý do đã loại B3
 * (QĐ-27).
 */
public final class RegularTimetable {

    /** Giờ khởi hành thường lệ, cho mọi tuyến có lịch, cả hai chiều (QĐ-20). */
    public static final List<Integer> DEPARTURE_HOURS = List.of(6, 12, 18);

    /**
     * Các tuyến có lịch, mặc định: ba cặp quanh Bến xe Miền Đông trên DB thật (QĐ-19) — Đà Lạt
     * 2/12, Cần Thơ 13/5, Vũng Tàu 10/11. Là id trong DB, nên đọc qua khoá cấu hình bên dưới.
     */
    public static final String DEFAULT_ROUTE_IDS = "2,12,5,13,10,11";

    /**
     * Khoá cấu hình của danh sách tuyến, kèm mặc định. Bộ sinh lịch sử và app đọc CÙNG một khoá,
     * nên chạy bộ sinh với {@code -Dbackfill.route-ids=…} thì lịch thật cũng theo đúng danh sách
     * đó. Tên khoá giữ nguyên từ CT3 để các script và tài liệu cũ vẫn đúng.
     */
    public static final String ROUTE_IDS_PROPERTY = "${backfill.route-ids:" + DEFAULT_ROUTE_IDS + "}";

    private RegularTimetable() {
    }
}
