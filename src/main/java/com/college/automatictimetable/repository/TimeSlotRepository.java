package com.college.automatictimetable.repository;

import com.college.automatictimetable.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {}
