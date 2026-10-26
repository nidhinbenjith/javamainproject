package com.college.automatictimetable.repository;

import com.college.automatictimetable.model.TeacherAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeacherAvailabilityRepository extends JpaRepository<TeacherAvailability, Long> {
    List<TeacherAvailability> findByTeacherId(Long teacherId);
    void deleteByTeacherId(Long teacherId);
    void deleteByTimeSlotId(Long timeSlotId);
}
