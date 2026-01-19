package org.example.moono_backend.service.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

/**
 * 금칙 시간 판단
 *
 * 사용자의 금칙 시간 설정에 따라 현재 시간이 금칙시간인지 판단한다.
 */
@Service
@Slf4j
public class QuietHourService {

    /**
     * 현재 시간이 금칙 시간인지판단한다.
     *
     * 날짜 경계 처리:
     * 1. startDndTime < endDndTime (예: 10:00-12:00)
     *      *    → 같은 날 내에서 시간 범위 체크
     *      *    → now >= start && now < end
     *
     * 2. startDndTime > endDndTime (예: 22:00-02:00)
     *      *    → 자정을 넘어가는 시간 범위 체크
     *      *    → now >= start || now < end
     *
     * 전제 조건;
     * - isDndActive = true일 경우에만 호출
     * startDnd와 endDnd는 항상 다름
     *
     * @param startDndTime
     * @param endDndTime
     * @param now
     * @return true: 금칙 시간임 , false:금칙시간이 아님
     *
     * @throws IllegalArgumentException startDndTime 또는 endDndTime이 null인 경우
     **/
    public boolean isDndTime(LocalTime startDndTime, LocalTime endDndTime, LocalTime now) {
        //null 체크
        if (startDndTime ==null || endDndTime == null|| now == null) {
            log.warn("금지 시간 check 오류: null parameter. startDndTime={}, endDndTime={}, now={}",
                     startDndTime, endDndTime, now);
            throw new IllegalArgumentException("금지 시간은 null이 될 수 없습니다.");
        }

        //날짜 경계를 넘지 않는 경우 (10:00-12:00)
        if(startDndTime.isBefore(endDndTime)){
            boolean inQuietHours = !now.isBefore(startDndTime) && now.isBefore(endDndTime);

            log.debug("Quiet hours check (same day): start={}, end={}, now={}, result={}",
            startDndTime, endDndTime, now, inQuietHours);
            return inQuietHours;
        }
        //날짜 경계를 넘는 경우 ( 22:00-02:00)
        boolean inQuietHours = !now.isBefore(startDndTime) || now.isBefore(endDndTime);
        log.debug("Quiet hours check (cross-day): start={}, end={}, now={}, result={}",
                  startDndTime, endDndTime, now, inQuietHours);
        return inQuietHours;
    }
    /**
     * 현재 시간이 금칙시간인지 판단합니다 (현재 시간 자동 조회).
     *
     * @param startDndTime 금칙시간 시작 시간 (포함)
     * @param endDndTime 금칙시간 종료 시간 (제외)
     * @return true: 금칙시간임, false: 금칙시간 아님
     */
    public boolean isDndTime(LocalTime startDndTime, LocalTime endDndTime) {
        return isDndTime(startDndTime, endDndTime, LocalTime.now());
    }


}
