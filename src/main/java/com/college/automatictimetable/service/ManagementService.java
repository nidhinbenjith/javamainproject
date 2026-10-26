package com.college.automatictimetable.service;

import com.college.automatictimetable.model.*;
import com.college.automatictimetable.repository.*;
import org.springframework.http.HttpStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

@Service
@Transactional
public class ManagementService {
    private final DepartmentRepository departments;
    private final CourseRepository courses;
    private final StudentClassRepository classes;
    private final TeacherRepository teachers;
    private final SubjectRepository subjects;
    private final ClassroomRepository classrooms;
    private final TimeSlotRepository slots;
    private final TeacherSubjectAssignmentRepository assignments;
    private final SubjectRequirementRepository requirements;
    private final TeacherAvailabilityRepository availabilities;
    private final TimetableEntryRepository entries;
    private final Validator validator;

    public ManagementService(DepartmentRepository departments, CourseRepository courses,
            StudentClassRepository classes, TeacherRepository teachers, SubjectRepository subjects,
            ClassroomRepository classrooms, TimeSlotRepository slots,
            TeacherSubjectAssignmentRepository assignments, SubjectRequirementRepository requirements,
            TeacherAvailabilityRepository availabilities, TimetableEntryRepository entries, Validator validator) {
        this.departments = departments;
        this.courses = courses;
        this.classes = classes;
        this.teachers = teachers;
        this.subjects = subjects;
        this.classrooms = classrooms;
        this.slots = slots;
        this.assignments = assignments;
        this.requirements = requirements;
        this.availabilities = availabilities;
        this.entries = entries;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String resource) {
        return switch (resource) {
            case "departments" -> departments.findAll().stream().map(this::view).toList();
            case "courses" -> courses.findAll().stream().map(this::view).toList();
            case "classes" -> classes.findAll().stream().map(this::view).toList();
            case "teachers" -> teachers.findAll().stream().map(this::view).toList();
            case "subjects" -> subjects.findAll().stream().map(this::view).toList();
            case "classrooms" -> classrooms.findAll().stream().map(this::view).toList();
            case "time-slots" -> slots.findAll().stream().map(this::view).toList();
            case "assignments" -> assignments.findAll().stream().map(this::view).toList();
            case "requirements" -> requirements.findAll().stream().map(this::view).toList();
            case "teacher-availability" -> availabilities.findAll().stream().map(this::view).toList();
            case "timetable" -> entries.findAll().stream().map(this::view).toList();
            default -> throw notFound("Unknown resource: " + resource);
        };
    }

    public Map<String, Object> save(String resource, Long id, Map<String, Object> body) {
        return switch (resource) {
            case "departments" -> view(save(departments, withId(new Department(), id, departments::findById),
                    value -> { value.name = text(body, "name"); value.code = optionalText(body, "code"); }));
            case "courses" -> view(save(courses, withId(new Course(), id, courses::findById),
                    value -> { value.name = text(body, "name"); value.code = text(body, "code");
                        value.semesterCount = positive(body, "semesterCount");
                        value.department = requiredEntity(departments, body, "departmentId"); }));
            case "classes" -> view(save(classes, withId(new StudentClass(), id, classes::findById),
                    value -> { value.name = text(body, "name"); value.section = text(body, "section");
                        value.semester = positive(body, "semester");
                        value.studentCount = positive(body, "studentCount");
                        value.course = requiredEntity(courses, body, "courseId");
                        if (value.semester > value.course.semesterCount) {
                            throw badRequest("semester cannot exceed the course semester count");
                        } }));
            case "teachers" -> view(save(teachers, withId(new Teacher(), id, teachers::findById),
                    value -> { value.name = text(body, "name"); value.email = optionalText(body, "email");
                        value.phone = optionalText(body, "phone");
                        value.department = optionalId(body, "departmentId") == null ? null
                                : requiredEntity(departments, body, "departmentId");
                        if (value.email != null && !value.email.isBlank()
                                && !value.email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                            throw badRequest("email must be a valid email address");
                        }
                        value.active = optionalBoolean(body, "active", true); }));
            case "subjects" -> view(save(subjects, withId(new Subject(), id, subjects::findById),
                    value -> { value.name = text(body, "name"); value.code = text(body, "code");
                        value.practical = optionalBoolean(body, "practical", false);
                        value.active = optionalBoolean(body, "active", true);
                        value.department = optionalId(body, "departmentId") == null ? null
                                : requiredEntity(departments, body, "departmentId"); }));
            case "classrooms" -> view(save(classrooms, withId(new Classroom(), id, classrooms::findById),
                    value -> { value.name = text(body, "name"); value.capacity = positive(body, "capacity");
                        value.roomType = optionalText(body, "roomType");
                        if (value.roomType == null || value.roomType.isBlank()) value.roomType = "STANDARD";
                        value.active = optionalBoolean(body, "active", true); }));
            case "time-slots" -> view(save(slots, withId(new TimeSlot(), id, slots::findById),
                    value -> { value.label = text(body, "label"); value.startTime = time(body, "startTime");
                        value.endTime = time(body, "endTime");
                        if (!value.endTime.isAfter(value.startTime)) throw badRequest("endTime must be after startTime");
                        value.active = optionalBoolean(body, "active", true);
                        if (value.active && slots.findAll().stream().anyMatch(existing ->
                                !Objects.equals(existing.id, value.id) && existing.active
                                        && value.startTime.isBefore(existing.endTime)
                                        && existing.startTime.isBefore(value.endTime))) {
                            throw badRequest("Active time slots cannot overlap.");
                        } }));
            case "assignments" -> view(save(assignments, withId(new TeacherSubjectAssignment(), id, assignments::findById),
                    value -> { value.teacher = requiredEntity(teachers, body, "teacherId");
                        value.subject = requiredEntity(subjects, body, "subjectId"); }));
            case "requirements" -> view(save(requirements, withId(new SubjectRequirement(), id, requirements::findById),
                    value -> { value.studentClass = requiredEntity(classes, body, "classId");
                        value.subject = requiredEntity(subjects, body, "subjectId");
                        value.periodsPerWeek = positive(body, "periodsPerWeek"); }));
            case "teacher-availability" -> view(save(availabilities, withId(new TeacherAvailability(), id, availabilities::findById),
                    value -> { value.teacher = requiredEntity(teachers, body, "teacherId");
                        value.dayOfWeek = day(body, "dayOfWeek");
                        value.timeSlot = requiredEntity(slots, body, "timeSlotId");
                        value.available = optionalBoolean(body, "available", false); }));
            default -> throw notFound("Unknown resource: " + resource);
        };
    }

    public void delete(String resource, Long id) {
        switch (resource) {
            case "departments" -> deleteDepartment(id);
            case "courses" -> deleteCourse(id);
            case "classes" -> deleteStudentClass(id);
            case "teachers" -> deleteTeacher(id);
            case "subjects" -> deleteSubject(id);
            case "classrooms" -> deleteClassroom(id);
            case "time-slots" -> deleteTimeSlot(id);
            case "assignments" -> assignments.deleteById(id);
            case "requirements" -> requirements.deleteById(id);
            case "teacher-availability" -> availabilities.deleteById(id);
            default -> throw notFound("Unknown resource: " + resource);
        }
    }

    /**
     * Deletes a department by safely removing or unlinking all dependent records first.
     *
     * Dependency chain:
     *   Department → Teacher (optional FK — null it out, preserve the teacher)
     *               → Subject (optional FK — null it out, preserve the subject)
     *               → Course → StudentClass → SubjectRequirement, TimetableEntry
     *                                       → TeacherSubjectAssignment
     */
    private void deleteDepartment(Long departmentId) {
        if (!departments.existsById(departmentId)) throw notFound("Department " + departmentId + " was not found");

        // Null-out optional department FK on teachers (teachers are preserved)
        for (var teacher : teachers.findByDepartmentId(departmentId)) {
            teacher.department = null;
            teachers.save(teacher);
        }

        // Null-out optional department FK on subjects (subjects are preserved)
        for (var subject : subjects.findByDepartmentId(departmentId)) {
            subject.department = null;
            subjects.save(subject);
        }

        // Delete each course that belongs to this department (cascades through classes)
        for (var course : courses.findByDepartmentId(departmentId)) {
            deleteCourse(course.id);
        }

        departments.deleteById(departmentId);
    }

    /**
     * Deletes a course and all its dependent student classes (and their children).
     */
    private void deleteCourse(Long courseId) {
        if (!courses.existsById(courseId)) throw notFound("Course " + courseId + " was not found");

        for (var studentClass : classes.findByCourseId(courseId)) {
            deleteStudentClass(studentClass.id);
        }

        courses.deleteById(courseId);
    }

    /**
     * Deletes a student class and all its timetable entries and subject requirements.
     */
    private void deleteStudentClass(Long classId) {
        if (!classes.existsById(classId)) throw notFound("Class " + classId + " was not found");

        entries.deleteByStudentClassId(classId);
        requirements.deleteByStudentClassId(classId);
        classes.deleteById(classId);
    }

    /**
     * Deletes a teacher and all their timetable entries, assignments, and availability records.
     */
    private void deleteTeacher(Long teacherId) {
        if (!teachers.existsById(teacherId)) throw notFound("Teacher " + teacherId + " was not found");

        entries.deleteByTeacherId(teacherId);
        assignments.deleteByTeacherId(teacherId);
        availabilities.deleteByTeacherId(teacherId);
        teachers.deleteById(teacherId);
    }

    /**
     * Deletes a subject and all its timetable entries, assignments, and subject requirements.
     */
    private void deleteSubject(Long subjectId) {
        if (!subjects.existsById(subjectId)) throw notFound("Subject " + subjectId + " was not found");

        entries.deleteBySubjectId(subjectId);
        assignments.deleteBySubjectId(subjectId);
        requirements.deleteBySubjectId(subjectId);
        subjects.deleteById(subjectId);
    }

    /**
     * Deletes a classroom and all timetable entries that reference it.
     */
    private void deleteClassroom(Long classroomId) {
        if (!classrooms.existsById(classroomId)) throw notFound("Classroom " + classroomId + " was not found");

        entries.deleteByClassroomId(classroomId);
        classrooms.deleteById(classroomId);
    }

    /**
     * Deletes a time slot and all timetable entries and teacher availability records that reference it.
     */
    private void deleteTimeSlot(Long slotId) {
        if (!slots.existsById(slotId)) throw notFound("Time slot " + slotId + " was not found");

        entries.deleteByTimeSlotId(slotId);
        availabilities.deleteByTimeSlotId(slotId);
        slots.deleteById(slotId);
    }

    private Map<String, Object> view(Department v) { return map("id", v.id, "name", v.name, "code", v.code); }
    private Map<String, Object> view(Course v) { return map("id", v.id, "name", v.name, "code", v.code,
            "semesterCount", v.semesterCount, "departmentId", v.department.id, "departmentName", v.department.name); }
    private Map<String, Object> view(StudentClass v) { return map("id", v.id, "name", v.name, "semester", v.semester,
            "section", v.section, "studentCount", v.studentCount, "courseId", v.course.id, "courseName", v.course.name); }
    private Map<String, Object> view(Teacher v) { return map("id", v.id, "name", v.name, "email", v.email, "phone", v.phone,
            "departmentId", v.department == null ? null : v.department.id, "active", v.active); }
    private Map<String, Object> view(Subject v) { return map("id", v.id, "name", v.name, "code", v.code, "practical", v.practical,
            "active", v.active, "departmentId", v.department == null ? null : v.department.id); }
    private Map<String, Object> view(Classroom v) { return map("id", v.id, "name", v.name, "capacity", v.capacity,
            "roomType", v.roomType, "active", v.active); }
    private Map<String, Object> view(TimeSlot v) { return map("id", v.id, "label", v.label, "startTime", v.startTime,
            "endTime", v.endTime, "active", v.active); }
    private Map<String, Object> view(TeacherSubjectAssignment v) { return map("id", v.id, "teacherId", v.teacher.id,
            "teacherName", v.teacher.name, "subjectId", v.subject.id, "subjectName", v.subject.name); }
    private Map<String, Object> view(SubjectRequirement v) { return map("id", v.id, "classId", v.studentClass.id,
            "className", v.studentClass.name, "subjectId", v.subject.id, "subjectName", v.subject.name,
            "periodsPerWeek", v.periodsPerWeek); }
    private Map<String, Object> view(TeacherAvailability v) { return map("id", v.id, "teacherId", v.teacher.id,
            "teacherName", v.teacher.name, "dayOfWeek", v.dayOfWeek, "timeSlotId", v.timeSlot.id,
            "slotLabel", v.timeSlot.label, "available", v.available); }
    private Map<String, Object> view(TimetableEntry v) { return map("id", v.id, "classId", v.studentClass.id,
            "className", v.studentClass.name, "subjectId", v.subject.id, "subjectName", v.subject.name,
            "teacherId", v.teacher.id, "teacherName", v.teacher.name, "classroomId", v.classroom.id,
            "classroomName", v.classroom.name, "timeSlotId", v.timeSlot.id, "slotLabel", v.timeSlot.label,
            "startTime", v.timeSlot.startTime, "endTime", v.timeSlot.endTime, "dayOfWeek", v.dayOfWeek); }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof String string) || string.isBlank()) throw badRequest(key + " is required");
        return checkLength(key, string.trim());
    }
    private static String optionalText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : checkLength(key, text);
    }
    private static Long requiredId(Map<String, Object> body, String key) {
        Long value = optionalId(body, key);
        if (value == null || value < 1) throw badRequest(key + " must be a positive ID");
        return value;
    }
    private static Long optionalId(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) return null;
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException exception) { throw badRequest(key + " must be a number"); }
    }
    private static int positive(Map<String, Object> body, String key) {
        try {
            int value = Integer.parseInt(Objects.toString(body.get(key), ""));
            if (value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw badRequest(key + " must be a positive number");
    }
    private static boolean optionalBoolean(Map<String, Object> body, String key, boolean fallback) {
        Object value = body.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        throw badRequest(key + " must be true or false");
    }
    private static LocalTime time(Map<String, Object> body, String key) {
        try { return LocalTime.parse(text(body, key)); }
        catch (RuntimeException exception) { throw badRequest(key + " must use HH:mm format"); }
    }
    private static DayOfWeek day(Map<String, Object> body, String key) {
        try { return DayOfWeek.valueOf(text(body, key).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw badRequest(key + " must be a valid day of the week"); }
    }
    private static <T> T withId(T value, Long id, java.util.function.Function<Long, Optional<T>> finder) {
        if (id == null) return value;
        return finder.apply(id).orElseThrow(() -> notFound("Record " + id + " was not found"));
    }
    private static <T> T requiredEntity(org.springframework.data.jpa.repository.JpaRepository<T, Long> repository,
            Map<String, Object> body, String key) {
        long id = requiredId(body, key);
        return repository.findById(id).orElseThrow(() -> notFound(key + " record " + id + " was not found"));
    }
    private static String checkLength(String key, String value) {
        int max = switch (key) {
            case "name" -> 120;
            case "code" -> 20;
            case "section" -> 10;
            case "phone" -> 30;
            case "roomType" -> 30;
            case "email" -> 160;
            case "label" -> 80;
            default -> 160;
        };
        if (value.length() > max) throw badRequest(key + " must be at most " + max + " characters");
        return value;
    }
    private <T> T save(org.springframework.data.jpa.repository.JpaRepository<T, Long> repository,
            T value, java.util.function.Consumer<T> changes) {
        changes.accept(value);
        var violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String message = violations.stream().map(ConstraintViolation::getMessage).sorted()
                    .collect(java.util.stream.Collectors.joining("; "));
            throw badRequest(message);
        }
        return repository.save(value);
    }
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
