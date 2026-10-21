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
 * {@code TripService.getTripsForList(status, since)} — phạm vi của màn Quản lý chuyến
 * (QĐ-12, docs/development/forecast_decisions.md), thay cho việc nạp toàn bộ lịch sử.
 *
 * Ghim đúng luật chọn:
 * <ul>
 * <li>chuyến CHƯA kết thúc (PENDING_APPROVAL, ACTIVE, DEPARTED) vào hết, bất kể ngày —
 * kể cả DEPARTED bị bỏ quên từ lâu, thứ Admin cần thấy nhất;</li>
 * <li>chuyến ĐÃ kết thúc (COMPLETED, CANCELLED — suy từ FSM) chỉ vào khi khởi hành từ
 * {@code since} trở đi, biên {@code >=};</li>
 * <li>lọc trạng thái áp CÙNG cửa sổ đó: lọc COMPLETED không được nạp lại cả lịch sử;</li>
 * <li>chuyến xóa mềm không vào; mới nhất lên đầu.</li>
 * </ul>
 *
 * Non-vacuous: đổi {@code >=} thành {@code >} trong query → test biên đỏ; bỏ vế
 * "chưa kết thúc" của OR → test chuyến cũ chưa kết thúc đỏ (đã probe khi viết).
 */
@SpringBootTest
@Transactional
class TripServiceTripListTest {

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

        since = LocalDateTime.now().minusDays(30).withSecond(0).withNano(0);
    }

    @Test
    void unfinishedTrips_areListedRegardlessOfDate() {
        Trip pendingOld = trip(TripStatus.PENDING_APPROVAL, since.minusDays(40));
        Trip activeOld = trip(TripStatus.ACTIVE, since.minusDays(20));
        Trip departedOld = trip(TripStatus.DEPARTED, since.minusDays(90));
        Trip activeFuture = trip(TripStatus.ACTIVE, LocalDateTime.now().plusDays(60));

        Set<Long> ids = idsOf(tripService.getTripsForList(null, since));

        assertTrue(ids.contains(pendingOld.getId()), "chờ duyệt từ lâu vẫn hiện — còn chờ Admin xử lý");
        assertTrue(ids.contains(activeOld.getId()), "ACTIVE quá giờ vẫn hiện");
        assertTrue(ids.contains(departedOld.getId()), "DEPARTED bị bỏ quên 120 ngày vẫn hiện");
        assertTrue(ids.contains(activeFuture.getId()), "chuyến sắp chạy vẫn hiện");
    }

    @Test
    void finishedTrips_areListedOnlyFromSinceOnwards() {
        Trip completedAtBoundary = trip(TripStatus.COMPLETED, since);
        Trip completedJustBefore = trip(TripStatus.COMPLETED, since.minusMinutes(1));
        Trip cancelledRecent = trip(TripStatus.CANCELLED, since.plusDays(3));
        Trip cancelledOld = trip(TripStatus.CANCELLED, since.minusDays(1));

        Set<Long> ids = idsOf(tripService.getTripsForList(null, since));

        assertTrue(ids.contains(completedAtBoundary.getId()), "đúng mốc since thì vào (>=)");
        assertFalse(ids.contains(completedJustBefore.getId()), "sớm hơn mốc một phút thì không");
        assertTrue(ids.contains(cancelledRecent.getId()));
        assertFalse(ids.contains(cancelledOld.getId()), "hủy trước cửa sổ thì không");
    }

    @Test
    void statusFilter_keepsTheWindowForFinishedStatuses() {
        Trip completedRecent = trip(TripStatus.COMPLETED, since.plusDays(1));
        Trip completedOld = trip(TripStatus.COMPLETED, since.minusDays(200));
        Trip departedOld = trip(TripStatus.DEPARTED, since.minusDays(200));

        Set<Long> completed = idsOf(tripService.getTripsForList(TripStatus.COMPLETED, since));
        Set<Long> departed = idsOf(tripService.getTripsForList(TripStatus.DEPARTED, since));

        assertEquals(Set.of(completedRecent.getId()), completed,
                "lọc COMPLETED chỉ trả chuyến trong cửa sổ, không nạp lại cả lịch sử");
        assertEquals(Set.of(departedOld.getId()), departed, "lọc trạng thái chưa kết thúc thì không xét ngày");
        assertFalse(completed.contains(completedOld.getId()));
    }

    @Test
    void softDeletedTrip_isNotListed() {
        Trip deleted = trip(TripStatus.COMPLETED, since.plusDays(1));
        tripRepository.delete(deleted); // @SQLDelete → is_deleted = true
        tripRepository.flush();

        assertFalse(idsOf(tripService.getTripsForList(null, since)).contains(deleted.getId()));
    }

    @Test
    void newestFirst() {
        Trip older = trip(TripStatus.COMPLETED, since.plusDays(1));
        Trip newer = trip(TripStatus.COMPLETED, since.plusDays(2));
        Trip upcoming = trip(TripStatus.ACTIVE, LocalDateTime.now().plusDays(5));

        List<Long> ids = tripService.getTripsForList(null, since).stream().map(Trip::getId).toList();

        assertTrue(ids.indexOf(upcoming.getId()) < ids.indexOf(newer.getId()), "khởi hành mới hơn đứng trước");
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
