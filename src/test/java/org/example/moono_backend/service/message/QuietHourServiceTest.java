package org.example.moono_backend.service.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
@DisplayName("QuietHoursService 테스트")
class QuietHourServiceTest {

    private QuietHourService quietHourService;

    @BeforeEach
    void setUp() {
        quietHourService = new QuietHourService();
    }

    //======= 같은 날 범위 테스트 =====d
    @Test
    @DisplayName("같은 날 범위 - 시작 시간 직전은 금칙시간 아님")
    void sameDayRange_beforeStartTime_shouldReturnFalse() {
        // given
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(12, 0);
        LocalTime now = LocalTime.of(9, 59);

        // when
        boolean result = quietHourService.isDndTime(start, end, now);

        // then
        assertFalse(result, "시작 시간 직전은 금칙시간이 아님");
    }
    @Test
    @DisplayName("같은 날 범위 - 시작 시간은 금칙시간 (보내면 안됨)")
    void sameDayRange_atStartTime_shouldReturnTrue() {
        // given
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(12, 0);
        LocalTime now = LocalTime.of(10, 0);

        // when
        boolean result = quietHourService.isDndTime(start, end, now);

        // then
        assertTrue(result, "시작 시간은 금칙시간임 (보내면 안됨)");
    }

    @Test
    @DisplayName("같은 날 범위 - 종료 시간은 금칙시간 아님 (보내도 됨)")
    void sameDayRange_atEndTime_shouldReturnFalse() {
        // given
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(12, 0);
        LocalTime now = LocalTime.of(12, 0);

        // when
        boolean result = quietHourService.isDndTime(start, end, now);

        // then
        assertFalse(result, "종료 시간은 금칙시간이 아님 (보내도 됨)");
    }

    @Test
    @DisplayName("같은 날 범위 - 종료 시간 이후는 금칙시간 아님")
    void sameDayRange_afterEndTime_shouldReturnFalse() {
        // given
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(12, 0);
        LocalTime now = LocalTime.of(12, 1);

        // when
        boolean result = quietHourService.isDndTime(start, end, now);

        // then
        assertFalse(result, "종료 시간 이후는 금칙시간이 아님");
    }

    //======= 날짜 경계 넘는 범위 테스트 (startDndTime > endDndTime) =====
    @Test
    @DisplayName("날짜 경계 범위 - 시작 시간 직전은 금칙시간 아님")
    void crossDayRange_beforeStartTime_shouldReturnFalse() {
        // given
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(2, 0);
        LocalTime now = LocalTime.of(21, 59);

        // when
        boolean result = quietHourService.isDndTime(start, end, now);

        // then
        assertFalse(result, "시작 시간 직전은 금칙시간이 아님");
    }

    @Test
    @DisplayName("날짜 경계 범위 - 시작 시간은 금칙시간")
    void crossDayRange_atStartTime_shouldReturnTrue() {
        // given
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(2, 0);
        LocalTime now = LocalTime.of(22, 0);

        // when
        boolean result = quietHourService.isDndTime(start, end, now);

        // then
        assertTrue(result, "시작 시간은 금칙시간임");
    }

    @Test
    @DisplayName("날짜 경계 범위 - 종료 시간은 금칙시간 아님")
    void crossDayRange_atEndTime_shouldReturnFalse() {
        // given
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(2, 0);
        LocalTime now = LocalTime.of(2, 0);

        // when
        boolean result = quietHourService.isDndTime(start, end, now);

        // then
        assertFalse(result, "종료 시간은 금칙시간이 아님");
    }

    @Test
    @DisplayName("날짜 경계 범위 - 종료 시간 이후는 금칙시간 아님")
    void crossDayRange_afterEndTime_shouldReturnFalse() {
        // given
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(2, 0);
        LocalTime now = LocalTime.of(2, 1);

        // when
        boolean result = quietHourService.isDndTime(start, end, now);

        // then
        assertFalse(result, "종료 시간 이후는 금칙시간이 아님");
    }

    @Test
    @DisplayName("현재 시간 자동 조회 메서드 테스트")
    void isInQuietHours_withoutNowParameter_shouldUseCurrentTime() {
        // given
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(12, 0);

        // 현재 시간이 11시라고 가정하면 금칙시간이어야 함
        // 하지만 실제 현재 시간에 따라 결과가 달라지므로
        // 이 테스트는 메서드가 예외 없이 실행되는지만 확인

        // when & then
        assertDoesNotThrow(() -> {
            quietHourService.isDndTime(start, end);
        }, "현재 시간 자동 조회 메서드는 예외를 발생시키지 않아야 함");
    }
}