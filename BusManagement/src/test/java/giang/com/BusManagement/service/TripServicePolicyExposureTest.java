package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lỗi #23/#24 — hai chính sách của TripService được phơi ra cho tầng UI HỎI thay
 * vì tự chép: {@code allowedTransitionsFrom(status)} (whitelist FSM) và
 * {@code deleteRefusalReason(trip)} (chính sách xóa theo trạng thái).
 *
 * Điều đáng chốt không phải giá trị trả về mà là TÍNH TƯƠNG ĐƯƠNG: cái UI được
 * mời phải đúng bằng cái service chấp nhận. Nên mỗi hàm được đối chiếu với đường
 * thực thi thật trên MỌI trạng thái: {@code allowedTransitionsFrom} với
 * {@code updateTripStatus()} trên cả 25 cặp (from, to); {@code deleteRefusalReason}
 * với {@code deleteTrip()} trên 5 trạng thái × có/không có vé. Nếu ai sửa bảng
 * canTransition() hay switch trong deleteRefusalReason() mà quên bên kia, test này
 * đỏ.
 *
 * Chạy trên busmanagement_test, @Transactional nên mọi bản ghi được rollback.
 */
@SpringBootTest
@Transactional
class TripServicePolicyExposureTest {

    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;

    // =====================================================================
    // allowedTransitionsFrom — bảng, và tương đương với updateTripStatus()
    // =====================================================================

    @Test
    void allowedTransitions_matchTheDocumentedWhitelist_includingSelf() {
        assertEquals(List.of(TripStatus.ACTIVE, TripStatus.PENDING_APPROVAL, TripStatus.CANCELLED),
                tripService.allowedTransitionsFrom(TripStatus.PENDING_APPROVAL));
        assertEquals(List.of(TripStatus.ACTIVE, TripStatus.DEPARTED, TripStatus.CANCELLED),
                tripService.allowedTransitionsFrom(TripStatus.ACTIVE));
        assertEquals(List.of(TripStatus.DEPARTED, TripStatus.COMPLETED),
                tripService.allowedTransitionsFrom(TripStatus.DEPARTED));
        assertEquals(List.of(TripStatus.COMPLETED), tripService.allowedTransitionsFrom(TripStatus.COMPLETED),
                "terminal: chỉ còn chính nó");
        assertEquals(List.of(TripStatus.CANCELLED), tripService.allowedTransitionsFrom(TripStatus.CANCELLED),
                "terminal: chỉ còn chính nó");
    }

    @Test
    void allowedTransitions_isExactlyWhatUpdateTripStatusAccepts_onAll25Pairs() {
        for (TripStatus from : TripStatus.values()) {
            for (TripStatus to : TripStatus.values()) {
                Trip t = saveTrip(from, 0);
                boolean offered = tripService.allowedTransitionsFrom(from).contains(to);
                boolean accepted;
                try {
                    tripService.updateTripStatus(t.getId(), to);
                    accepted = true;
                } catch (IllegalStateException refused) {
                    accepted = false;
                }
                assertEquals(accepted, offered,
                        "(" + from + " → " + to + "): UI mời=" + offered + " nhưng FSM nhận=" + accepted);
            }
        }
    }

    @Test
    void cancelButtonRule_theBoardWillOffer_onlyPendingAndActive() {
        // Đúng cái nút "Hủy" của trip-list.html sẽ hỏi (lỗi #23): DEPARTED không còn hủy được.
        assertTrue(tripService.allowedTransitionsFrom(TripStatus.PENDING_APPROVAL).contains(TripStatus.CANCELLED));
        assertTrue(tripService.allowedTransitionsFrom(TripStatus.ACTIVE).contains(TripStatus.CANCELLED));
        assertFalse(tripService.allowedTransitionsFrom(TripStatus.DEPARTED).contains(TripStatus.CANCELLED));
        assertFalse(tripService.allowedTransitionsFrom(TripStatus.COMPLETED).contains(TripStatus.CANCELLED));
        // CANCELLED chứa chính nó (same-state luôn hợp lệ) — nhưng "hủy một chuyến đã
        // hủy" là no-op, nên populateTripList() loại thêm same-state trước khi mời.
        // Chốt ở đây để biết hai lớp cố ý khác nhau đúng một điều kiện đó.
        assertTrue(tripService.allowedTransitionsFrom(TripStatus.CANCELLED).contains(TripStatus.CANCELLED));
    }

    // =====================================================================
    // deleteRefusalReason — bảng, và tương đương với deleteTrip()
    // =====================================================================

    @Test
    void deleteRefusal_followsThePerStatusPolicy() {
        assertNotNull(tripService.deleteRefusalReason(saveTrip(TripStatus.DEPARTED, 0)));
        assertNotNull(tripService.deleteRefusalReason(saveTrip(TripStatus.COMPLETED, 0)));
        assertNotNull(tripService.deleteRefusalReason(saveTrip(TripStatus.ACTIVE, 5)), "ACTIVE có vé → phải đi luồng Hủy");
        assertNull(tripService.deleteRefusalReason(saveTrip(TripStatus.ACTIVE, 0)), "ACTIVE chưa bán vé → xóa được");
        assertNull(tripService.deleteRefusalReason(saveTrip(TripStatus.PENDING_APPROVAL, 7)), "PENDING xóa được kể cả có vé");
        assertNull(tripService.deleteRefusalReason(saveTrip(TripStatus.CANCELLED, 21)), "CANCELLED xóa được kể cả có vé");
    }

    @Test
    void deleteRefusal_isExactlyWhatDeleteTripEnforces_onAllStatusesWithAndWithoutTickets() {
        for (TripStatus status : TripStatus.values()) {
            for (int sold : new int[] { 0, 3 }) {
                Trip t = saveTrip(status, sold);
                String reason = tripService.deleteRefusalReason(t);
                if (reason == null) {
                    tripService.deleteTrip(t.getId()); // không được ném
                    assertTrue(tripRepository.findById(t.getId()).isEmpty(),
                            status + "/" + sold + " vé: đã báo xóa được thì phải xóa mềm thật");
                } else {
                    IllegalStateException ex = assertThrows(IllegalStateException.class,
                            () -> tripService.deleteTrip(t.getId()), status + "/" + sold + " vé");
                    assertEquals(reason, ex.getMessage(), "cùng một câu từ chối ở cả hai nơi");
                }
            }
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Trip saveTrip(TripStatus status, int sold) {
        Trip t = new Trip();
        t.setStatus(status);
        t.setTotalSeats(40);
        t.setTicketsSold(sold);
        t.setDepartureTime(LocalDateTime.now().plusDays(2));
        t.setArrivalTimeExpected(LocalDateTime.now().plusDays(2).plusHours(2));
        return tripRepository.save(t);
    }
}
