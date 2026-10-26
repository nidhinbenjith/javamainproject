package com.college.automatictimetable.controller;

import com.college.automatictimetable.model.*;
import com.college.automatictimetable.repository.*;
import com.college.automatictimetable.service.TimetableService;
import com.college.automatictimetable.repository.DepartmentRepository;
import com.college.automatictimetable.repository.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TimetableController {
    private final TimetableService timetable;
    private final TeacherRepository teachers;
    private final SubjectRepository subjects;
    private final ClassroomRepository classrooms;
    private final StudentClassRepository classes;
    private final TimetableEntryRepository entries;
    private final DepartmentRepository departments;
    private final CourseRepository courses;
    private final SubjectRequirementRepository requirements;
    private final TeacherSubjectAssignmentRepository assignments;
    private final TeacherAvailabilityRepository availabilities;

    public TimetableController(TimetableService timetable, TeacherRepository teachers,
            SubjectRepository subjects, ClassroomRepository classrooms,
            StudentClassRepository classes, TimetableEntryRepository entries,
            DepartmentRepository departments, CourseRepository courses,
            SubjectRequirementRepository requirements, TeacherSubjectAssignmentRepository assignments,
            TeacherAvailabilityRepository availabilities) {
        this.timetable = timetable;
        this.teachers = teachers;
        this.subjects = subjects;
        this.classrooms = classrooms;
        this.classes = classes;
        this.entries = entries;
        this.departments = departments;
        this.courses = courses;
        this.requirements = requirements;
        this.assignments = assignments;
        this.availabilities = availabilities;
    }

    @PostMapping("/timetable/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> generate(@RequestBody Map<String, Object> request) {
        return timetable.generate(request);
    }

    @GetMapping("/timetable")
    public Object timetable(@RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long classroomId) {
        return timetable.timetable(classId, teacherId, classroomId);
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("departments", departments.count());
        counts.put("courses", courses.count());
        counts.put("classes", classes.count());
        counts.put("teachers", teachers.count());
        counts.put("subjects", subjects.count());
        counts.put("classrooms", classrooms.count());
        counts.put("periods", entries.count());
        counts.put("timetableEntries", entries.count());
        counts.put("conflicts", conflictItems().size());
        return counts;
    }

    @GetMapping("/conflicts")
    @Transactional(readOnly = true)
    public Map<String, Object> conflicts() {
        List<Map<String, Object>> issues = conflictItems();
        String message = !issues.isEmpty() ? issues.size() + " timetable validation issue(s) found."
                : entries.count() == 0 && requirements.count() == 0
                        ? "No timetable entries or subject requirements are configured to check."
                        : "No timetable validation issues were found.";
        return Map.of("items", issues, "message", message);
    }

    private List<Map<String, Object>> conflictItems() {
        var all = entries.findAll();
        var issues = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                TimetableEntry first = all.get(i);
                TimetableEntry second = all.get(j);
                if (first.dayOfWeek != second.dayOfWeek
                        || !first.timeSlot.startTime.isBefore(second.timeSlot.endTime)
                        || !second.timeSlot.startTime.isBefore(first.timeSlot.endTime)) continue;
                if (first.studentClass.id.equals(second.studentClass.id))
                    issues.add(conflict("Class overlap", first, second,
                            "Move one class period to another available time slot."));
                if (first.teacher.id.equals(second.teacher.id))
                    issues.add(conflict("Teacher clash", first, second,
                            "Assign a different eligible teacher or move one period."));
                if (first.classroom.id.equals(second.classroom.id))
                    issues.add(conflict("Classroom clash", first, second,
                            "Assign a different suitable classroom or move one period."));
            }
        }
        var assignedPairs = assignments.findAll().stream()
                .map(assignment -> assignment.teacher.id + ":" + assignment.subject.id)
                .collect(java.util.stream.Collectors.toSet());
        var unavailable = availabilities.findAll().stream().filter(item -> !item.available).toList();
        for (TimetableEntry entry : all) {
            if (!assignedPairs.contains(entry.teacher.id + ":" + entry.subject.id)) {
                issues.add(conflict("Missing teacher assignment", entry,
                        "Assign this teacher to the scheduled subject or choose an assigned teacher."));
            }
            if (unavailable.stream().anyMatch(item -> item.teacher.id.equals(entry.teacher.id)
                    && item.dayOfWeek == entry.dayOfWeek && item.timeSlot.id.equals(entry.timeSlot.id))) {
                issues.add(conflict("Teacher unavailable", entry,
                        "Move this period or update the teacher's availability."));
            }
        }
        for (var requirement : requirements.findAll()) {
            long scheduled = all.stream().filter(entry ->
                    entry.studentClass.id.equals(requirement.studentClass.id)
                            && entry.subject.id.equals(requirement.subject.id)).count();
            if (scheduled != requirement.periodsPerWeek) {
                issues.add(weeklyRequirementConflict(requirement.studentClass.name, requirement.subject.name,
                        scheduled, requirement.periodsPerWeek));
            }
        }
        for (StudentClass studentClass : classes.findAll()) {
            boolean hasRequirements = requirements.findByStudentClassId(studentClass.id).size() > 0;
            if (!hasRequirements) {
                issues.add(weeklyRequirementConflict(studentClass.name, "No subjects configured", 0, 0));
            }
        }
        return issues;
    }

    private static Map<String, Object> conflict(String type, TimetableEntry entry, String suggestion) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("day", entry.dayOfWeek);
        item.put("time", entry.timeSlot.startTime + " - " + entry.timeSlot.endTime);
        item.put("firstEntryId", entry.id);
        item.put("firstClass", entry.studentClass.name);
        item.put("firstSubject", entry.subject.name);
        item.put("firstTeacher", entry.teacher.name);
        item.put("firstClassroom", entry.classroom.name);
        item.put("secondClass", "");
        item.put("secondSubject", "");
        item.put("secondTeacher", "");
        item.put("secondClassroom", "");
        item.put("suggestion", suggestion);
        return item;
    }

    private static Map<String, Object> weeklyRequirementConflict(String className, String subjectName,
            long scheduled, int expected) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "Subject requirement mismatch");
        item.put("day", "Weekly");
        item.put("time", scheduled + " / " + expected + " periods");
        item.put("firstEntryId", "");
        item.put("firstClass", className);
        item.put("firstSubject", subjectName);
        item.put("firstTeacher", "");
        item.put("firstClassroom", "");
        item.put("secondClass", "");
        item.put("secondSubject", "");
        item.put("secondTeacher", "");
        item.put("secondClassroom", "");
        item.put("suggestion", expected == 0
                ? "Configure at least one subject requirement for this class."
                : "Generate or regenerate this class timetable to match the required weekly periods.");
        return item;
    }

    private static Map<String, Object> conflict(String type, TimetableEntry first,
            TimetableEntry second, String suggestion) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("day", first.dayOfWeek);
        item.put("time", first.timeSlot.startTime + " - " + first.timeSlot.endTime);
        item.put("firstEntryId", first.id);
        item.put("firstClass", first.studentClass.name);
        item.put("firstSubject", first.subject.name);
        item.put("firstTeacher", first.teacher.name);
        item.put("firstClassroom", first.classroom.name);
        item.put("secondEntryId", second.id);
        item.put("secondClass", second.studentClass.name);
        item.put("secondSubject", second.subject.name);
        item.put("secondTeacher", second.teacher.name);
        item.put("secondClassroom", second.classroom.name);
        item.put("suggestion", suggestion);
        return item;
    }
}
