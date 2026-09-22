package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code TripService.getRunningAndRecentTrips(since)} — phạm vi mời của dropdown
 * "Chuyến xe liên quan" trên form sự cố, thay cho việc đổ toàn bộ lịch sử.
 *
 * Ghim đúng hai vế của luật chọn:
 * <ul>
 * <li>ACTIVE / DEPARTED vào hết, bất kể ngày (kể cả khởi hành xa trong tương lai hay
 * đã khởi hành lâu mà chưa hoàn thành);</li>
 * <li>COMPLETED / CANCELLED chỉ vào khi khởi hành từ {@code since} trở đi — biên
 * {@code >=} (đúng mốc thì vào, sớm hơn một phút thì không);</li>
 * <li>PENDING_APPROVAL không bao giờ vào; chuyến xóa mềm không vào
 * ({@code @SQLRestriction}); kết quả mới nhất lên đầu.</li>
 * </ul>
 *
 * Non-vacuous: đổi {@code >=} thành {@code >} trong query → test biên đỏ; bỏ vế
 * OR thứ nhất → test ACTIVE/DEPARTED đỏ (đã probe khi viết).
 */
@SpringBootTest
@Transactional
class TripServiceRunningAndRecentTripsTest {

    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;
    @Autowired private RouteRepository routeRepository;

    private Route route;
    private LocalDateTime since;

    @BeforeEach
    void setUp() {
        route = new Route();
        route.setDistanceKm(50.0);
        route.setEstimatedDuration(60);
        route = routeRepository.save(route);

        since = LocalDateTime.now().minusDays(7).withSecond(0).withNano(0);
    }

    @Test
    void activeAndDeparted_areIncludedRegardlessOfDate() {
        Trip activeFarFuture = trip(TripStatus.ACTIVE, LocalDateTime.now().plusDays(60));
        Trip departedLongAgo = trip(TripStatus.DEPARTED, since.minusDays(30));

        Set<Long> ids = idsOf(tripService.getRunningAndRecentTrips(since));

        assertTrue(ids.contains(activeFarFuture.getId()), "ACTIVE khởi hành 60 ngày nữa vẫn được mời");
        assertTrue(ids.contains(departedLongAgo.getId()), "DEPARTED từ 37 ngày trước (chưa hoàn thành) vẫn được mời");
    }

    @Test
    void completedAndCancelled_areIncludedOnlyFromSinceOnwards() {
        Trip completedAtBoundary = trip(TripStatus.COMPLETED, since);
        Trip completedJustBefore = trip(TripStatus.COMPLETED, since.minusMinutes(1));
        Trip cancelledRecent = trip(TripStatus.CANCELLED, since.plusDays(3));
        Trip cancelledOld = trip(TripStatus.CANCELLED, since.minusDays(1));

        Set<Long> ids = idsOf(tripService.getRunningAndRecentTrips(since));

        assertTrue(ids.contains(completedAtBoundary.getId()), "đúng mốc since thì vào (>=)");
        assertFalse(ids.contains(completedJustBefore.getId()), "sớm hơn mốc một phút thì không");
        assertTrue(ids.contains(cancelledRecent.getId()), "hủy gần đây vẫn hiện — admin hủy vì sự cố rồi mới ghi sự cố");
        assertFalse(ids.contains(cancelledOld.getId()), "hủy trước cửa sổ thì không");
    }

    @Test
    void pendingApproval_isNeverIncluded() {
        Trip pendingRecent = trip(TripStatus.PENDING_APPROVAL, LocalDateTime.now().plusDays(2));

        Set<Long> ids = idsOf(tripService.getRunningAndRecentTrips(since));

        assertFalse(ids.contains(pendingRecent.getId()), "chuyến chờ duyệt chưa tồn tại thật, không có sự cố nào gắn được");
    }

    @Test
    void softDeletedTrip_isNotOffered() {
        Trip deleted = trip(TripStatus.COMPLETED, since.plusDays(1));
        tripRepository.delete(deleted); // @SQLDelete → is_deleted = true
        tripRepository.flush();

        Set<Long> ids = idsOf(tripService.getRunningAndRecentTrips(since));

        assertFalse(ids.contains(deleted.getId()), "chuyến xóa mềm bị @SQLRestriction ẩn khỏi mọi query");
    }

    @Test
    void newestFirst() {
        Trip older = trip(TripStatus.COMPLETED, since.plusDays(1));
        Trip newer = trip(TripStatus.COMPLETED, since.plusDays(2));
        Trip running = trip(TripStatus.DEPARTED, since.plusDays(3));

        List<Long> ids = tripService.getRunningAndRecentTrips(since).stream().map(Trip::getId).toList();

        assertTrue(ids.indexOf(running.getId()) < ids.indexOf(newer.getId()), "khởi hành mới hơn đứng trước");
        assertTrue(ids.indexOf(newer.getId()) < ids.indexOf(older.getId()));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Trip trip(TripStatus status, LocalDateTime departure) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusHours(1));
        t.setTotalSeats(20);
        t.setTicketsSold(0);
        t.setPrice(new BigDecimal("50000"));
        t.setStatus(status);
        return tripRepository.save(t);
    }

    private Set<Long> idsOf(List<Trip> trips) {
        return trips.stream().map(Trip::getId).collect(Collectors.toSet());
    }
}
