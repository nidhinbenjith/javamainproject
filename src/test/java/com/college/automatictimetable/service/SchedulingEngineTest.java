package com.college.automatictimetable.service;

import org.junit.jupiter.api.Test;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingEngineTest {
    private final SchedulingEngine engine = new SchedulingEngine();
    private final SchedulingEngine.Placement mondayFirstPeriod =
            new SchedulingEngine.Placement(DayOfWeek.MONDAY, 1,
                    LocalTime.of(9, 0), LocalTime.of(10, 0), 10, 20);
    private final SchedulingEngine.Placement mondaySecondPeriod =
            new SchedulingEngine.Placement(DayOfWeek.MONDAY, 2,
                    LocalTime.of(10, 0), LocalTime.of(11, 0), 10, 20);

    @Test
    void schedulesEveryRequiredPeriodWithoutTeacherRoomOrClassClashes() {
        var first = candidate(1, 101, List.of(mondayFirstPeriod, mondaySecondPeriod));
        var second = candidate(2, 102, List.of(mondayFirstPeriod, mondaySecondPeriod));

        var result = engine.schedule(List.of(first, second));

        assertTrue(result.complete());
        assertEquals(2, result.scheduled().size());
        assertEquals(2, result.scheduled().stream().map(item -> item.placement().slotId()).distinct().count());
    }

    @Test
    void reportsAnUnscheduledPeriodWhenTeacherAndRoomAreUnavailable() {
        var result = engine.schedule(List.of(candidate(1, 101, List.of(mondayFirstPeriod)),
                candidate(2, 102, List.of(mondayFirstPeriod))));

        assertFalse(result.complete());
        assertEquals(1, result.unscheduled().size());
        assertEquals(1, result.scheduled().size());
    }

    @Test
    void preventsOverlappingPeriodsEvenWhenTheirSlotIdsDiffer() {
        var overlappingSlot = new SchedulingEngine.Placement(DayOfWeek.MONDAY, 3,
                LocalTime.of(9, 30), LocalTime.of(10, 30), 11, 21);
        var result = engine.schedule(List.of(candidate(1, 101, List.of(mondayFirstPeriod)),
                candidate(2, 102, List.of(overlappingSlot))));

        assertFalse(result.complete());
        assertEquals(1, result.scheduled().size());
    }

    @Test
    void reportsImpossibleScheduleWhenThereAreNoCandidates() {
        var result = engine.schedule(List.of(candidate(1, 101, List.of())));

        assertFalse(result.complete());
        assertEquals(1, result.unscheduled().size());
        assertTrue(result.scheduled().isEmpty());
    }

    private static SchedulingEngine.Candidate candidate(long requirement, long subject,
            List<SchedulingEngine.Placement> options) {
        return new SchedulingEngine.Candidate(
                new SchedulingEngine.Lesson(requirement, subject, "Subject " + subject, 1), options);
    }
}
