package com.college.automatictimetable.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

public class SchedulingEngine {
    private static final int MAX_SEARCH_STEPS = 250_000;

    public record Lesson(long requirementId, long subjectId, String subjectName, int periodNumber) {}
    public record Placement(DayOfWeek day, long slotId, LocalTime start, LocalTime end,
                            long teacherId, long classroomId) {}
    public record Candidate(Lesson lesson, List<Placement> placements) {}
    public record Scheduled(Lesson lesson, Placement placement) {}
    public record Result(List<Scheduled> scheduled, List<Lesson> unscheduled, boolean searchLimitReached) {
        public boolean complete() { return unscheduled.isEmpty(); }
    }

    public Result schedule(List<Candidate> candidates) {
        List<Candidate> ordered = candidates.stream()
                .sorted(Comparator.comparingInt((Candidate item) -> item.placements().size())
                        .thenComparingLong(item -> item.lesson().subjectId())
                        .thenComparingInt(item -> item.lesson().periodNumber()))
                .toList();
        List<Scheduled> chosen = new ArrayList<>();
        List<Scheduled> best = new ArrayList<>();
        long[] steps = {0};
        boolean complete = place(ordered, 0, chosen, best, steps);
        if (complete) return new Result(List.copyOf(chosen), List.of(), false);
        List<Lesson> unscheduled = ordered.subList(Math.min(best.size(), ordered.size()), ordered.size())
                .stream().map(Candidate::lesson).toList();
        return new Result(List.copyOf(best), unscheduled, steps[0] >= MAX_SEARCH_STEPS);
    }

    private boolean place(List<Candidate> candidates, int index, List<Scheduled> chosen,
            List<Scheduled> best, long[] steps) {
        if (index == candidates.size()) return true;
        if (steps[0] >= MAX_SEARCH_STEPS) return false;
        if (chosen.size() > best.size()) {
            best.clear();
            best.addAll(chosen);
        }
        Candidate candidate = candidates.get(index);
        for (Placement option : candidate.placements()) {
            steps[0]++;
            if (conflicts(option, chosen)) continue;
            chosen.add(new Scheduled(candidate.lesson(), option));
            if (place(candidates, index + 1, chosen, best, steps)) return true;
            chosen.remove(chosen.size() - 1);
        }
        return false;
    }

    private boolean conflicts(Placement candidate, List<Scheduled> chosen) {
        for (Scheduled scheduled : chosen) {
            Placement existing = scheduled.placement();
            if (!candidate.day().equals(existing.day()) || !overlaps(candidate, existing)) continue;
            return true;
        }
        return false;
    }

    private static boolean overlaps(Placement first, Placement second) {
        return first.start().isBefore(second.end()) && second.start().isBefore(first.end());
    }
}
