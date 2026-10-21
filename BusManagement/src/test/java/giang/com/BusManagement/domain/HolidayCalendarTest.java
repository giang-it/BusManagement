package giang.com.BusManagement.domain;

import giang.com.BusManagement.domain.HolidayCalendar.HolidayDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chốt lịch ngày lễ của QĐ-9 (docs/development/forecast_decisions.md): phạm vi
 * 2023–2032, bảng ngày âm lịch, và phép tính "ngày này thuộc cửa sổ lễ nào".
 *
 * JUnit thuần, không @SpringBootTest — cùng lý do WhatIfCoverageTest: HolidayCalendar
 * là hàm thuần, không cần database.
 *
 * Bảng âm lịch đã được đối chiếu với hai nguồn khi viết (xem javadoc của
 * HolidayCalendar). Các test bảng ở đây không chép lại bảng mà kiểm những tính chất
 * mà một lỗi gõ sẽ phá vỡ: Tết nằm trong khoảng 21/1–20/2, và Giỗ Tổ cách Tết đúng
 * số ngày của hai (hoặc ba, nếu có tháng nhuận) tháng âm lịch.
 *
 * Các test cửa sổ dựa trên cửa sổ HIỆN TẠI của Holiday (tạm theo bước 0); nếu GĐ2
 * chốt cửa sổ khác thì các ngày mẫu ở đây phải được tính lại.
 */
class HolidayCalendarTest {

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    // =========================================================================
    // Phạm vi
    // =========================================================================

    @Test
    void coverage_runsFromFirstDayOf2023ToLastDayOf2032() {
        assertTrue(HolidayCalendar.covers(d(2023, 1, 1)));
        assertTrue(HolidayCalendar.covers(d(2032, 12, 31)));
        assertFalse(HolidayCalendar.covers(d(2022, 12, 31)));
        assertFalse(HolidayCalendar.covers(d(2033, 1, 1)));
    }

    /** Ngoài phạm vi phải bị từ chối — trả "ngày thường" cho Tết 2033 là lỗi âm thầm. */
    @Test
    void outsideCoverage_isRefused_ratherThanAnsweredAsAnOrdinaryDay() {
        assertThrows(IllegalArgumentException.class, () -> HolidayCalendar.holidaysOn(d(2033, 1, 31)));
        assertThrows(IllegalArgumentException.class, () -> HolidayCalendar.holidaysOn(d(2022, 12, 31)));
        assertThrows(IllegalArgumentException.class, () -> HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, 2033));
        assertThrows(IllegalArgumentException.class, () -> HolidayCalendar.anchorOf(Holiday.NEW_YEAR, 2022));
    }

    // =========================================================================
    // Bảng âm lịch
    // =========================================================================

    /** Đúng các ngày mà thí nghiệm bước 0 đã dùng (forecast_research/step0/run_synth.py). */
    @Test
    void lunarAnchors_matchTheDatesUsedInStep0() {
        assertEquals(d(2024, 2, 10), HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, 2024));
        assertEquals(d(2025, 1, 29), HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, 2025));
        assertEquals(d(2026, 2, 17), HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, 2026));
        assertEquals(d(2024, 4, 18), HolidayCalendar.anchorOf(Holiday.HUNG_KINGS_COMMEMORATION, 2024));
        assertEquals(d(2025, 4, 7), HolidayCalendar.anchorOf(Holiday.HUNG_KINGS_COMMEMORATION, 2025));
        assertEquals(d(2026, 4, 26), HolidayCalendar.anchorOf(Holiday.HUNG_KINGS_COMMEMORATION, 2026));
    }

    /**
     * Lịch Việt Nam tính theo GMT+7: Tết 2030 là 02/02, trong khi lịch Trung Quốc
     * (GMT+8) là 03/02. Dòng này chốt lý do không dùng thư viện lịch âm Trung Quốc.
     */
    @Test
    void lunarNewYear2030_followsTheVietnameseTimeZone() {
        assertEquals(d(2030, 2, 2), HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, 2030));
    }

    @Test
    void everyLunarNewYear_fallsBetween21JanuaryAnd20February() {
        for (int year = HolidayCalendar.FIRST_COVERED_YEAR; year <= HolidayCalendar.LAST_COVERED_YEAR; year++) {
            LocalDate tet = HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, year);
            assertFalse(tet.isBefore(d(year, 1, 21)) || tet.isAfter(d(year, 2, 20)), "Tết " + year + ": " + tet);
        }
    }

    /**
     * 10/3 âm lịch = hết hai tháng âm (mỗi tháng 29 hoặc 30 ngày) + 9 ngày, tức 67–69
     * ngày sau Tết. Năm có tháng nhuận đứng TRƯỚC tháng 3 thì thêm một tháng: 96–99
     * ngày. Trong phạm vi chỉ năm 2023 như vậy (nhuận tháng 2); năm 2031 nhuận tháng 3,
     * nhưng Giỗ Tổ ở tháng 3 thường nên không bị ảnh hưởng.
     */
    @Test
    void everyHungKingsCommemoration_isTheTenthDayOfTheThirdLunarMonth() {
        for (int year = HolidayCalendar.FIRST_COVERED_YEAR; year <= HolidayCalendar.LAST_COVERED_YEAR; year++) {
            long days = ChronoUnit.DAYS.between(HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, year),
                    HolidayCalendar.anchorOf(Holiday.HUNG_KINGS_COMMEMORATION, year));
            if (year == 2023) {
                assertTrue(days >= 96 && days <= 99, "Giỗ Tổ 2023 (có nhuận tháng 2): " + days + " ngày sau Tết");
            } else {
                assertTrue(days >= 67 && days <= 69, "Giỗ Tổ " + year + ": " + days + " ngày sau Tết");
            }
        }
    }

    @Test
    void fixedDateHolidays_anchorOnTheirLegalDates() {
        assertEquals(d(2027, 1, 1), HolidayCalendar.anchorOf(Holiday.NEW_YEAR, 2027));
        assertEquals(d(2027, 4, 30), HolidayCalendar.anchorOf(Holiday.REUNIFICATION_AND_LABOUR_DAY, 2027));
        assertEquals(d(2027, 9, 2), HolidayCalendar.anchorOf(Holiday.NATIONAL_DAY, 2027));
    }

    @Test
    void everyAnchor_fallsInsideItsOwnYear() {
        for (int year = HolidayCalendar.FIRST_COVERED_YEAR; year <= HolidayCalendar.LAST_COVERED_YEAR; year++) {
            for (Holiday holiday : Holiday.values()) {
                assertEquals(year, HolidayCalendar.anchorOf(holiday, year).getYear(), holiday + " " + year);
            }
        }
    }

    // =========================================================================
    // Cửa sổ quanh ngày lễ
    // =========================================================================

    @Test
    void anOrdinaryDay_belongsToNoHoliday() {
        assertEquals(List.of(), HolidayCalendar.holidaysOn(d(2025, 3, 15)));
    }

    /** Hai đầu cửa sổ đều được tính, ngày ngay ngoài thì không. */
    @Test
    void lunarNewYearWindow_includesBothEnds_andNothingBeyond() {
        Holiday tet = Holiday.LUNAR_NEW_YEAR;
        LocalDate anchor = HolidayCalendar.anchorOf(tet, 2026);
        LocalDate first = anchor.minusDays(tet.getDaysBefore());
        LocalDate last = anchor.plusDays(tet.getDaysAfter());

        assertEquals(List.of(new HolidayDay(tet, anchor, -tet.getDaysBefore())), HolidayCalendar.holidaysOn(first));
        assertEquals(List.of(new HolidayDay(tet, anchor, 0)), HolidayCalendar.holidaysOn(anchor));
        assertEquals(List.of(new HolidayDay(tet, anchor, tet.getDaysAfter())), HolidayCalendar.holidaysOn(last));
        assertEquals(List.of(), HolidayCalendar.holidaysOn(first.minusDays(1)));
        assertEquals(List.of(), HolidayCalendar.holidaysOn(last.plusDays(1)));
    }

    /** Năm 2026 Giỗ Tổ (26/4) rơi vào cửa sổ của 30/4: ngày đó thuộc CẢ hai dịp. */
    @Test
    void overlappingWindows_reportBothHolidays() {
        assertEquals(List.of(
                new HolidayDay(Holiday.HUNG_KINGS_COMMEMORATION, d(2026, 4, 26), 0),
                new HolidayDay(Holiday.REUNIFICATION_AND_LABOUR_DAY, d(2026, 4, 30), -4)),
                HolidayCalendar.holidaysOn(d(2026, 4, 26)));
        assertEquals(List.of(new HolidayDay(Holiday.REUNIFICATION_AND_LABOUR_DAY, d(2026, 4, 30), -1)),
                HolidayCalendar.holidaysOn(d(2026, 4, 29)), "cửa sổ Giỗ Tổ đã hết từ 28/4");
    }

    /** Cuối tháng 12 thuộc Tết Dương lịch của năm SAU — kể cả ngày cuối cùng của phạm vi. */
    @Test
    void newYearWindow_crossesTheYearBoundary() {
        assertEquals(List.of(new HolidayDay(Holiday.NEW_YEAR, d(2026, 1, 1), -2)),
                HolidayCalendar.holidaysOn(d(2025, 12, 30)));
        assertEquals(List.of(new HolidayDay(Holiday.NEW_YEAR, d(2033, 1, 1), -1)),
                HolidayCalendar.holidaysOn(d(2032, 12, 31)));
    }

    /** HolidayCalendar chỉ xét ngày neo của năm trước, năm nay, năm sau. */
    @Test
    void everyWindow_isAtMostAYearOnEachSide() {
        for (Holiday holiday : Holiday.values()) {
            assertTrue(holiday.getDaysBefore() >= 0 && holiday.getDaysBefore() <= 365, holiday.name());
            assertTrue(holiday.getDaysAfter() >= 0 && holiday.getDaysAfter() <= 365, holiday.name());
        }
    }
}
