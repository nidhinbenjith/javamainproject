package com.college.automatictimetable.repository;

import com.college.automatictimetable.model.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {}
