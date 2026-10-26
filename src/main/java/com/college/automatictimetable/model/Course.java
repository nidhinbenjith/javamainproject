package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "courses", uniqueConstraints = @UniqueConstraint(columnNames = {"department_id", "name"}))
public class Course extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotBlank @Size(max = 120)
    @Column(nullable = false, length = 120)
    public String name;
    @NotBlank @Size(max = 20)
    @Column(nullable = false, length = 20)
    public String code;
    @NotNull @Positive
    @Column(nullable = false)
    public Integer semesterCount;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    public Department department;
}
