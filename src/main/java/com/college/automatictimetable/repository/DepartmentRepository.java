package com.college.automatictimetable.repository;

import com.college.automatictimetable.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DepartmentRepository extends JpaRepository<Department, Long> {}
