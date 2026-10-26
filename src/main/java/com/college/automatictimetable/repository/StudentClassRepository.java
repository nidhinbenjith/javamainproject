package com.college.automatictimetable.repository;

import com.college.automatictimetable.model.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StudentClassRepository extends JpaRepository<StudentClass, Long> {
    List<StudentClass> findByCourseId(Long courseId);
}
