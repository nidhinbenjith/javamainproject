package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "student_classes", uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "semester", "section"}))
public class StudentClass extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotBlank @Size(max = 80)
    @Column(nullable = false, length = 80)
    public String name;
    @NotNull @Positive
    @Column(nullable = false)
    public Integer semester;
    @NotBlank @Size(max = 10)
    @Column(nullable = false, length = 10)
    public String section;
    @NotNull @Positive
    @Column(name = "student_count", nullable = false)
    public Integer studentCount;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    public Course course;
}
