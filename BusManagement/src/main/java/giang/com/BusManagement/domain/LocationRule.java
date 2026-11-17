package giang.com.BusManagement.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/**
 * Luật vị trí DUY NHẤT của hệ thống (QĐ-23, CT5a trong docs/development/route_pairs_decisions.md):
 * một chiếc xe — hay một tài xế, phụ xe — đang đứng ở bến nào, và đã xuất phát được từ một bến
 * chưa. Bộ sinh lịch sử (CT3), script kiểm sau khi sinh và việc chọn xe, chọn tài xế của app
 * (CT5b) cùng gọi lớp này, để lịch sử và app không bao giờ hiểu "vị trí" theo hai cách.
 *
 * ====================================================================
 * KHÔNG GPS: VỊ TRÍ SUY RA TỪ CÁC CHUYẾN ĐÃ CHẠY
 * ====================================================================
 * Vị trí lúc t = bến CUỐI của lượt chạy gần nhất có giờ đến ≤ t (chuyến bị hủy không tính —
 * xem legOf). Chưa có lượt nào thì là BẾN NHÀ (Bus.homeStation, Driver.homeStation). Bến nhà
 * trống và chưa chạy lượt nào nghĩa là "không biết ở đâu": canDepart() trả false, để máy không
 * bao giờ chọn một xe mà nó không biết đang ở đâu.
 *
 * Lớp này KHÔNG xét trùng lịch: một lượt đang chạy dở lúc t (khởi hành ≤ t < đến) không đổi vị
 * trí cho tới khi tới nơi, còn việc "xe đang bận" vẫn là của luật bận sẵn có
 * (TripService.isBusBusy, isDriverBusy). Hai luật đan xen trên cùng dòng ứng viên, đúng nguyên
 * tắc §3 của roadmap — không nhân bản luật bận ở đây.
 *
 * Hàm thuần, chỉ làm việc với id bến và thời điểm, không đọc DB — cùng tinh thần HolidayCalendar.
 * Thời gian nghỉ tối thiểu do nơi gọi truyền vào (xe 1 giờ chuẩn bị, tài xế 30 phút), để luật
 * không khai lại các hằng số đang sống ở TripService.
 */
public final class LocationRule {

    private LocationRule() {
    }

    /** Một lượt chạy: đi từ bến nào, tới bến nào, khởi hành và tới nơi lúc nào. */
    public record Leg(Long fromStationId, Long toStationId, LocalDateTime departure, LocalDateTime arrival) {
        public Leg {
            Objects.requireNonNull(fromStationId, "fromStationId");
            Objects.requireNonNull(toStationId, "toStationId");
            Objects.requireNonNull(departure, "departure");
            Objects.requireNonNull(arrival, "arrival");
            if (arrival.isBefore(departure)) {
                throw new IllegalArgumentException("Giờ đến " + arrival + " trước giờ khởi hành " + departure);
            }
        }
    }

    /**
     * Lượt chạy của một chuyến, hay null nếu chuyến không làm xe / người đổi chỗ: chuyến bị hủy,
     * hoặc thiếu tuyến, bến đầu / bến cuối, giờ đi, giờ đến. Bến đầu, bến cuối lấy theo stopOrder
     * (Route.getDepartureStation / getDestinationStation) — tuyến nhiều trạm cũng chỉ đổi chỗ từ
     * trạm đầu tới trạm cuối. Chuyến xóa mềm không bao giờ tới được đây (@SQLRestriction của Trip).
     */
    public static Leg legOf(Trip trip) {
        if (trip == null || trip.getStatus() == TripStatus.CANCELLED || trip.getRoute() == null
                || trip.getDepartureTime() == null || trip.getArrivalTimeExpected() == null) {
            return null;
        }
        Station from = trip.getRoute().getDepartureStation();
        Station to = trip.getRoute().getDestinationStation();
        if (from == null || to == null || from.getId() == null || to.getId() == null) {
            return null;
        }
        return new Leg(from.getId(), to.getId(), trip.getDepartureTime(), trip.getArrivalTimeExpected());
    }

    /** Lượt có giờ đến lớn nhất mà vẫn ≤ t; null nếu chưa có lượt nào tới nơi trước hoặc đúng lúc t. */
    public static Leg lastLegArrivedBy(Collection<Leg> legs, LocalDateTime t) {
        Objects.requireNonNull(t, "t");
        if (legs == null) {
            return null;
        }
        return legs.stream()
                .filter(Objects::nonNull)
                .filter(leg -> !leg.arrival().isAfter(t))
                .max(Comparator.comparing(Leg::arrival))
                .orElse(null);
    }

    /** Bến đang đứng lúc t: bến cuối của lượt gần nhất đã tới; chưa có lượt nào thì bến nhà (có thể null). */
    public static Long stationAt(Collection<Leg> legs, Long homeStationId, LocalDateTime t) {
        Leg last = lastLegArrivedBy(legs, t);
        return last != null ? last.toStationId() : homeStationId;
    }

    /**
     * Xuất phát được từ bến fromStationId lúc departure không — tức đang đứng đúng bến đó và đã
     * nghỉ ít nhất rest kể từ lúc tới. Không xét trùng lịch (xem javadoc lớp).
     */
    public static boolean canDepart(Collection<Leg> legs, Long homeStationId, Long fromStationId,
            LocalDateTime departure, Duration rest) {
        return canDepart(lastLegArrivedBy(legs, departure), homeStationId, fromStationId, departure, rest);
    }

    /**
     * Cùng luật, khi nơi gọi đã giữ sẵn lượt gần nhất (bộ sinh theo dõi từng xe theo ngày, không
     * cần quét lại cả danh sách). lastLeg phải là lượt gần nhất có giờ đến ≤ departure, hoặc null.
     */
    public static boolean canDepart(Leg lastLeg, Long homeStationId, Long fromStationId,
            LocalDateTime departure, Duration rest) {
        Objects.requireNonNull(departure, "departure");
        Objects.requireNonNull(rest, "rest");
        if (fromStationId == null) {
            return false;
        }
        if (lastLeg == null) {
            return fromStationId.equals(homeStationId);
        }
        if (lastLeg.arrival().isAfter(departure)) {
            throw new IllegalArgumentException("lastLeg tới nơi lúc " + lastLeg.arrival()
                    + ", sau giờ xuất phát " + departure + " — không phải lượt gần nhất trước đó.");
        }
        return fromStationId.equals(lastLeg.toStationId()) && !lastLeg.arrival().plus(rest).isAfter(departure);
    }

    /** Lượt sớm nhất khởi hành KHÔNG SỚM HƠN t (lượt kế tiếp sau một mốc), hoặc null. */
    public static Leg firstLegDepartingFrom(Collection<Leg> legs, LocalDateTime t) {
        Objects.requireNonNull(t, "t");
        if (legs == null) {
            return null;
        }
        return legs.stream()
                .filter(Objects::nonNull)
                .filter(leg -> !leg.departure().isBefore(t))
                .min(Comparator.comparing(Leg::departure))
                .orElse(null);
    }

    /**
     * Route pairs CT5b: chèn lượt {@code planned} vào lịch của một xe hay một người mà chuỗi vị
     * trí vẫn liền ở CẢ HAI phía — lúc khởi hành họ đứng ở bến đi của nó (stationAt), và lượt kế
     * tiếp đã cam kết, nếu có, khởi hành từ bến đến của nó. Chỉ xét phía trước là chưa đủ khi các
     * lượt được xếp không theo thứ tự thời gian: màn Đề xuất xếp thẻ theo lãi, nên một thẻ 06:00
     * có thể được xét SAU thẻ 18:00 cùng ngày của cùng một xe.
     *
     * Không xét thời gian nghỉ hay trùng giờ — bên gọi lo (TripService dùng cửa sổ "bận").
     *
     * @param legs các lượt đã cam kết của họ (ít nhất: lượt gần nhất tới nơi trước giờ đi, và lượt
     *             sớm nhất khởi hành từ giờ đến trở đi)
     */
    public static boolean fits(Collection<Leg> legs, Long homeStationId, Leg planned) {
        Objects.requireNonNull(planned, "planned");
        if (!planned.fromStationId().equals(stationAt(legs, homeStationId, planned.departure()))) {
            return false;
        }
        Leg next = firstLegDepartingFrom(legs, planned.arrival());
        return next == null || next.fromStationId().equals(planned.toStationId());
    }
}
