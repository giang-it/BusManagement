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
    // hai thứ, vẫn chỉ là dữ liệu:
    //   - XE ĐƯỢC XẾP LỊCH: thay cho "xe READY ngay lúc này". Lịch là cho những ngày tới, nên
    //     một xe đang chạy hôm nay vẫn được xếp — TripService chọn danh sách này theo đúng luật
    //     trạng thái của validateBusForTrip (lỗi #19);
    //   - KM ĐÃ CAM KẾT của từng xe ở các chuyến chưa chạy xong: km thật chỉ tăng khi chuyến
    //     hoàn thành, nên không có số này thì luật bảo trì chỉ thấy MỘT chuyến đang xét, không
    //     thấy cả tuần đã xếp cho xe đó.
    // Context của màn Đề xuất không có hai thứ này: isLookAhead() == false, committedKm == 0,
    // nên mọi thứ chạy y như trước.

    private List<giang.com.BusManagement.domain.Bus> schedulableBuses;
    private final Map<Long, Double> committedKm = new HashMap<>();

    /** Biến context thành context lập lịch: danh sách xe được xếp và km đã cam kết, theo id xe. */
    public void lookAhead(List<giang.com.BusManagement.domain.Bus> buses, Map<Long, Double> kmByBus) {
        this.schedulableBuses = new ArrayList<>(buses);
        putAllKm(kmByBus);
    }

    private void putAllKm(Map<Long, Double> kmByBus) {
        if (kmByBus != null) {
            committedKm.putAll(kmByBus);
        }
    }

    /** true khi đây là context lập lịch (buildTimetableContext). */
    public boolean isLookAhead() {
        return schedulableBuses != null;
    }

    /** Xe được xếp lịch; chỉ có nghĩa khi isLookAhead(). */
    public List<giang.com.BusManagement.domain.Bus> schedulableBuses() {
        return Collections.unmodifiableList(schedulableBuses);
    }

    /** Km xe này đã cam kết ở các chuyến chưa chạy xong; 0 nếu không có (hay không phải context lập lịch). */
    public double committedKm(Long busId) {
        return committedKm.getOrDefault(busId, 0.0);
    }

    /** Cộng km của một chuyến vừa xếp cho xe — cùng lúc với reserve() của chuyến đó. */
    public void addCommittedKm(Long busId, double km) {
        committedKm.merge(busId, km, Double::sum);
    }
}
