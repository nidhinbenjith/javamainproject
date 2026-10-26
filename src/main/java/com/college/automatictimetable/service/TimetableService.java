package com.college.automatictimetable.service;

import com.college.automatictimetable.model.*;
import com.college.automatictimetable.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

@Service
public class TimetableService {
    private final StudentClassRepository classes;
    private final SubjectRequirementRepository requirements;
    private final TeacherSubjectAssignmentRepository assignments;
    private final TeacherAvailabilityRepository availabilities;
    private final TeacherRepository teachers;
    private final ClassroomRepository classrooms;
    private final TimeSlotRepository slots;
    private final TimetableEntryRepository entries;
    private final SchedulingEngine engine = new SchedulingEngine();

    public TimetableService(StudentClassRepository classes, SubjectRequirementRepository requirements,
            TeacherSubjectAssignmentRepository assignments, TeacherAvailabilityRepository availabilities,
            TeacherRepository teachers, ClassroomRepository classrooms, TimeSlotRepository slots,
            TimetableEntryRepository entries) {
        this.classes = classes;
        this.requirements = requirements;
        this.assignments = assignments;
        this.availabilities = availabilities;
        this.teachers = teachers;
        this.classrooms = classrooms;
        this.slots = slots;
        this.entries = entries;
    }

    @Transactional
    public Map<String, Object> generate(Map<String, Object> request) {
        long classId = positiveId(request.get("classId"), "classId");
        StudentClass studentClass = classes.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        List<DayOfWeek> days = days(request.get("days"));
        List<TimeSlot> selectedSlots = requestedSlots(request.get("timeSlotIds"));
        boolean replaceExisting = Boolean.TRUE.equals(request.get("replaceExisting"));
        List<SubjectRequirement> classRequirements = requirements.findByStudentClassId(classId);
        if (classRequirements.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Add subject requirements for this class before generating.");
        }
        long weeklyPeriods = classRequirements.stream().mapToLong(item -> item.periodsPerWeek).sum();
        if (weeklyPeriods > (long) days.size() * selectedSlots.size()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The class needs " + weeklyPeriods + " periods, but only " + ((long) days.size() * selectedSlots.size()) + " class periods were selected.");
        }

        List<TimetableEntry> existing = entries.findAll();
        List<TeacherAvailability> availability = availabilities.findAll();
        List<TimetableEntry> otherClasses = existing.stream()
                .filter(item -> !item.studentClass.id.equals(classId)).toList();
        if (!replaceExisting && existing.stream().anyMatch(item -> item.studentClass.id.equals(classId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This class already has timetable entries. Confirm replacement to generate a new timetable.");
        }

        List<Classroom> activeRooms = classrooms.findAll().stream()
                .filter(room -> room.active && room.capacity >= studentClass.studentCount).toList();
        List<SchedulingEngine.Candidate> candidates = new ArrayList<>();
        List<String> noEligibleResources = new ArrayList<>();
        for (SubjectRequirement requirement : classRequirements) {
            List<Teacher> eligibleTeachers = assignments.findBySubjectId(requirement.subject.id).stream()
                    .map(assignment -> assignment.teacher)
                    .filter(teacher -> teacher.active).distinct().toList();
            List<Classroom> suitableRooms = activeRooms.stream().filter(room ->
                    requirement.subject.practical
                            ? room.roomType.equalsIgnoreCase("LAB")
                            : !room.roomType.equalsIgnoreCase("LAB")).toList();
            if (eligibleTeachers.isEmpty()) noEligibleResources.add(requirement.subject.name + " has no active assigned teacher");
            if (suitableRooms.isEmpty()) noEligibleResources.add(requirement.subject.name
                    + (requirement.subject.practical ? " needs an active LAB classroom" : " needs an active non-LAB classroom")
                    + " with capacity for " + studentClass.studentCount + " students");
            for (int period = 1; period <= requirement.periodsPerWeek; period++) {
                SchedulingEngine.Lesson lesson = new SchedulingEngine.Lesson(requirement.id,
                        requirement.subject.id, requirement.subject.name, period);
                List<SchedulingEngine.Placement> options = new ArrayList<>();
                for (DayOfWeek day : days) {
                    for (TimeSlot slot : selectedSlots) {
                        for (Teacher teacher : eligibleTeachers) {
                            if (!teacherAvailable(availability, teacher.id, day, slot.id)) continue;
                            for (Classroom room : suitableRooms) {
                                if (busyElsewhere(otherClasses, teacher.id, room.id, day, slot)) continue;
                                options.add(new SchedulingEngine.Placement(day, slot.id,
                                        slot.startTime, slot.endTime, teacher.id, room.id));
                            }
                        }
                    }
                }
                candidates.add(new SchedulingEngine.Candidate(lesson, options));
            }
        }
        if (!noEligibleResources.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, String.join("; ", noEligibleResources));
        }

        SchedulingEngine.Result result = engine.schedule(candidates);
        if (!result.complete()) {
            List<String> unscheduled = result.unscheduled().stream()
                    .map(lesson -> lesson.subjectName() + " (period " + lesson.periodNumber() + ")").toList();
            String message = "Could not place " + unscheduled.size() + " required period(s): "
                    + String.join(", ", unscheduled)
                    + (result.searchLimitReached() ? ". Search limit reached; reduce constraints or split the generation." : ". Check teacher availability, rooms, and selected slots.");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
        }

        if (replaceExisting) entries.deleteByStudentClassId(classId);
        Map<Long, Subject> subjectMap = new HashMap<>();
        classRequirements.forEach(item -> subjectMap.put(item.subject.id, item.subject));
        Map<Long, Teacher> teacherMap = new HashMap<>();
        teachers.findAll().forEach(item -> teacherMap.put(item.id, item));
        Map<Long, Classroom> roomMap = new HashMap<>();
        activeRooms.forEach(item -> roomMap.put(item.id, item));
        Map<Long, TimeSlot> slotMap = new HashMap<>();
        selectedSlots.forEach(item -> slotMap.put(item.id, item));
        List<TimetableEntry> scheduled = result.scheduled().stream().map(item -> {
            SchedulingEngine.Placement placement = item.placement();
            TimetableEntry entry = new TimetableEntry();
            entry.studentClass = studentClass;
            entry.subject = subjectMap.get(item.lesson().subjectId());
            entry.teacher = teacherMap.get(placement.teacherId());
            entry.classroom = roomMap.get(placement.classroomId());
            entry.timeSlot = slotMap.get(placement.slotId());
            entry.dayOfWeek = placement.day();
            return entry;
        }).toList();
        entries.saveAll(scheduled);
        return Map.of("classId", classId, "scheduledPeriods", scheduled.size(),
                "message", "Successfully generated " + scheduled.size() + " timetable periods.",
                "timetable", scheduled.stream().map(this::view).toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> timetable(Long classId, Long teacherId, Long classroomId) {
        List<TimetableEntry> all = classId != null ? entries.findByStudentClassId(classId)
                : teacherId != null ? entries.findByTeacherId(teacherId) : entries.findAll();
        if (classroomId != null) {
            all = all.stream().filter(item -> item.classroom.id.equals(classroomId)).toList();
        }
        return all.stream().sorted(Comparator.comparing((TimetableEntry item) -> item.dayOfWeek)
                .thenComparing(item -> item.timeSlot.startTime)).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> timetable(Long classId, Long teacherId) {
        return timetable(classId, teacherId, null);
    }

    private static boolean teacherAvailable(List<TeacherAvailability> availability, Long teacherId, DayOfWeek day, Long slotId) {
        return availability.stream()
                .filter(item -> item.teacher.id.equals(teacherId) && item.dayOfWeek == day && item.timeSlot.id.equals(slotId))
                .allMatch(item -> item.available);
    }

    private static boolean busyElsewhere(List<TimetableEntry> entries, Long teacherId, Long roomId,
            DayOfWeek day, TimeSlot slot) {
        return entries.stream().anyMatch(entry -> entry.dayOfWeek == day
                && overlaps(entry.timeSlot, slot)
                && (entry.teacher.id.equals(teacherId) || entry.classroom.id.equals(roomId)));
    }

    private static boolean overlaps(TimeSlot first, TimeSlot second) {
        return first.startTime.isBefore(second.endTime) && second.startTime.isBefore(first.endTime);
    }

    private List<TimeSlot> requestedSlots(Object value) {
        if (!(value instanceof List<?> ids) || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one time slot.");
        }
        List<TimeSlot> selected = ids.stream().map(id -> slots.findById(positiveId(id, "timeSlotIds"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected time slot not found.")))
                .filter(slot -> slot.active).distinct().toList();
        if (selected.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select active time slots.");
        for (int i = 0; i < selected.size(); i++) {
            for (int j = i + 1; j < selected.size(); j++) {
                if (overlaps(selected.get(i), selected.get(j))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Selected time slots overlap. Configure non-overlapping slots before generation.");
                }
            }
        }
        return selected;
    }

    private static List<DayOfWeek> days(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one working day.");
        }
        try {
            return items.stream().map(item -> DayOfWeek.valueOf(item.toString().toUpperCase(Locale.ROOT)))
                    .distinct().toList();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Working days must be valid days of the week.");
        }
    }

    private static long positiveId(Object value, String name) {
        try {
            long id = Long.parseLong(Objects.toString(value));
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be a positive ID.");
    }

    private Map<String, Object> view(TimetableEntry item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.id);
        row.put("classId", item.studentClass.id);
        row.put("className", item.studentClass.name);
        row.put("subjectId", item.subject.id);
        row.put("subjectName", item.subject.name);
        row.put("teacherId", item.teacher.id);
        row.put("teacherName", item.teacher.name);
        row.put("classroomId", item.classroom.id);
        row.put("classroomName", item.classroom.name);
        row.put("timeSlotId", item.timeSlot.id);
        row.put("slotLabel", item.timeSlot.label);
        row.put("startTime", item.timeSlot.startTime);
        row.put("endTime", item.timeSlot.endTime);
        row.put("dayOfWeek", item.dayOfWeek);
        return row;
    }
}
