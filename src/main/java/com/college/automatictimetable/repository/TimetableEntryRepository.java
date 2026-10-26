package com.college.automatictimetable.repository;

import com.college.automatictimetable.model.TimetableEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {
    List<TimetableEntry> findByStudentClassId(Long studentClassId);
    List<TimetableEntry> findByTeacherId(Long teacherId);
    List<TimetableEntry> findByClassroomId(Long classroomId);
    void deleteByStudentClassId(Long studentClassId);
    void deleteByTeacherId(Long teacherId);
    void deleteBySubjectId(Long subjectId);
    void deleteByClassroomId(Long classroomId);
    void deleteByTimeSlotId(Long timeSlotId);
}
