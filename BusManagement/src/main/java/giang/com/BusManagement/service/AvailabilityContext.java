package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.LocationRule;
import giang.com.BusManagement.domain.Trip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *
 * <p><b>Giữ chỗ tạm (GĐ6, QĐ-17, G6-3).</b> Recommendation chọn xe + tài xế cho
 * từng chuyến thêm theo thứ tự; chọn xong một chuyến thì thêm nó vào danh sách
 * bằng {@link #reserve}, như một chuyến PENDING_APPROVAL — đúng trạng thái của
 * chuyến tăng cường mới tạo. Các luật sẵn có của TripService coi chuyến đó là bận
 * và cộng giờ lái của nó, nên chuyến sau không chọn trùng xe, trùng tài xế hay
 * vượt 8 giờ lái. Chuyến giữ chỗ chỉ sống trong context của một lần xem trang:
 * không lưu DB, và cổng kiểm tra cuối (chạy SQL) không thấy nó.
 */
public final class AvailabilityContext {

    private final List<Trip> trips;

    public AvailabilityContext(List<Trip> trips) {
        this.trips = (trips != null) ? new ArrayList<>(trips) : new ArrayList<>();
    }

    /** Các chuyến đã preload trong khoảng đang xét (chưa hủy), kèm chuyến giữ chỗ tạm. Không bao giờ null. */
    public List<Trip> trips() {
        return Collections.unmodifiableList(trips);
    }

    /**
     * Giữ chỗ tạm: từ đây các luật chọn tài nguyên coi chuyến này như một chuyến đã có
     * trong lịch. Chuyến phải mang trạng thái, xe, tài xế và giờ đi/đến của nó — không
     * kiểm tra gì ở đây, vì lớp này không chứa luật.
     */
    public void reserve(Trip trip) {
        trips.add(trip);
        reserved.add(trip);
    }

    // ── Vị trí (route pairs CT5b, QĐ-23) ─────────────────────────────────────────
    // Các chuyến ở trên chỉ là những chuyến GIAO với khoảng đã nạp. Luật vị trí
    // (LocationRule.fits) còn cần hai lượt nằm ngoài khoảng: lượt gần nhất tới nơi
    // TRƯỚC đầu khoảng (một xe dự phòng có thể đứng yên ở bến từ nhiều tuần trước), và
    // lượt sớm nhất khởi hành SAU cuối khoảng (chuyến đã lên lịch xa hơn). Với hai lượt
    // đó, xét trên context cho đúng câu trả lời như hỏi DB, cho mọi chuyến kết thúc
    // trong khoảng. buildAvailabilityContext nạp sẵn chúng cho từng xe, từng người.

    private final Map<Long, LocationRule.Leg> busLegBefore = new HashMap<>();
    private final Map<Long, LocationRule.Leg> personLegBefore = new HashMap<>();
    private final Map<Long, LocationRule.Leg> busLegAfter = new HashMap<>();
    private final Map<Long, LocationRule.Leg> personLegAfter = new HashMap<>();

    /**
     * @param busBefore    lượt gần nhất tới nơi không muộn hơn đầu khoảng, theo id xe
     * @param personBefore như trên, theo userId của tài xế / phụ xe
     * @param busAfter     lượt sớm nhất khởi hành từ cuối khoảng trở đi, theo id xe
     * @param personAfter  như trên, theo userId
     */
    public AvailabilityContext(List<Trip> trips, Map<Long, LocationRule.Leg> busBefore,
            Map<Long, LocationRule.Leg> personBefore, Map<Long, LocationRule.Leg> busAfter,
            Map<Long, LocationRule.Leg> personAfter) {
        this(trips);
        putAll(busLegBefore, busBefore);
        putAll(personLegBefore, personBefore);
        putAll(busLegAfter, busAfter);
        putAll(personLegAfter, personAfter);
    }

    private static void putAll(Map<Long, LocationRule.Leg> target, Map<Long, LocationRule.Leg> source) {
        if (source != null) {
            target.putAll(source);
        }
    }

    /** Lượt gần nhất của xe trước khoảng đã nạp; null nếu không có (hay context dựng không kèm vị trí). */
    public LocationRule.Leg busLegBefore(Long busId) {
        return busLegBefore.get(busId);
    }

    /** Cùng ý cho một người (theo userId). */
    public LocationRule.Leg personLegBefore(Long userId) {
        return personLegBefore.get(userId);
    }

    /** Lượt sớm nhất của xe sau khoảng đã nạp; null nếu không có. */
    public LocationRule.Leg busLegAfter(Long busId) {
        return busLegAfter.get(busId);
    }

    /** Cùng ý cho một người (theo userId). */
    public LocationRule.Leg personLegAfter(Long userId) {
        return personLegAfter.get(userId);
    }

    // ── Lập lịch trước (route pairs CT6, CM-21) ─────────────────────────────────
    // Context do TripService.buildTimetableContext dựng cho bộ lập lịch thường lệ mang thêm
    // XE ĐƯỢC XẾP LỊCH, thay cho "xe READY ngay lúc này": lịch là cho những ngày tới, nên một xe
    // đang chạy hôm nay vẫn được xếp — TripService chọn danh sách này theo đúng luật trạng thái
    // của validateBusForTrip (lỗi #19). Context của màn Đề xuất không có nó: isLookAhead() == false.

    private List<giang.com.BusManagement.domain.Bus> schedulableBuses;

    /** Biến context thành context lập lịch: danh sách xe được xếp. */
    public void lookAhead(List<giang.com.BusManagement.domain.Bus> buses) {
        this.schedulableBuses = new ArrayList<>(buses);
    }

    /** true khi đây là context lập lịch (buildTimetableContext). */
    public boolean isLookAhead() {
        return schedulableBuses != null;
    }

    /** Xe được xếp lịch; chỉ có nghĩa khi isLookAhead(). */
    public List<giang.com.BusManagement.domain.Bus> schedulableBuses() {
        return Collections.unmodifiableList(schedulableBuses);
    }

    // ── Km đã cam kết (route pairs CT6 CM-21; từ CT7d cho MỌI context, CM-25) ────────────
    // Km thật chỉ tăng khi chuyến hoàn thành (updateTripStatus), nên luật bảo trì của máy phải cộng
    // thêm km của mọi chuyến chưa chạy xong của xe — không thì nó chỉ thấy MỘT chuyến đang xét, không
    // thấy cả tuần đã xếp. Hai nguồn: các chuyến chưa xong đã có trong DB (buildAvailabilityContext nạp
    // một lần, mọi ngày, không chỉ trong khoảng đã nạp), và các chuyến giữ chỗ bằng reserve(). Mỗi
    // mục giữ giờ khởi hành, để TripService tách km trước / sau một ngày bảo trì dự kiến (CT7d).
    // Vẫn chỉ là dữ liệu: cộng số km trong một khoảng giờ, không xét gì.

    private record CommittedLeg(java.time.LocalDateTime departure, double km) {
    }

    private final Map<Long, List<CommittedLeg>> committed = new HashMap<>();
    private final List<Trip> reserved = new ArrayList<>();

    /** Một chuyến chưa xong đã có trong DB: xe, giờ khởi hành, km tuyến (nạp lúc dựng context). */
    public void addCommitted(Long busId, java.time.LocalDateTime departure, double km) {
        committed.computeIfAbsent(busId, id -> new ArrayList<>()).add(new CommittedLeg(departure, km));
    }

    /**
     * Km xe này đã cam kết ở các chuyến chưa xong và các chuyến giữ chỗ, khởi hành trong [from, to);
     * from / to null = không chặn phía đó. 0 nếu không có.
     */
    public double committedKm(Long busId, java.time.LocalDateTime from, java.time.LocalDateTime to) {
        double km = 0.0;
        for (CommittedLeg leg : committed.getOrDefault(busId, List.of())) {
            if (within(leg.departure(), from, to)) {
                km += leg.km();
            }
        }
        for (Trip trip : reserved) {
            if (trip.getBus() != null && busId.equals(trip.getBus().getId()) && trip.getRoute() != null
                    && trip.getRoute().getDistanceKm() != null && within(trip.getDepartureTime(), from, to)) {
                km += trip.getRoute().getDistanceKm();
            }
        }
        return km;
    }

    private static boolean within(java.time.LocalDateTime t, java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return t != null && (from == null || !t.isBefore(from)) && (to == null || t.isBefore(to));
    }
}
