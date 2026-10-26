package com.college.automatictimetable.repository;

import com.college.automatictimetable.model.SubjectRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubjectRequirementRepository extends JpaRepository<SubjectRequirement, Long> {
    List<SubjectRequirement> findByStudentClassId(Long studentClassId);
    void deleteByStudentClassId(Long studentClassId);
    void deleteBySubjectId(Long subjectId);
}
