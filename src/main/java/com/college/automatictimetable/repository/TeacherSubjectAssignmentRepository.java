package com.college.automatictimetable.repository;

import com.college.automatictimetable.model.TeacherSubjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeacherSubjectAssignmentRepository extends JpaRepository<TeacherSubjectAssignment, Long> {
    List<TeacherSubjectAssignment> findBySubjectId(Long subjectId);
    List<TeacherSubjectAssignment> findByTeacherId(Long teacherId);
    void deleteByTeacherId(Long teacherId);
    void deleteBySubjectId(Long subjectId);
}
