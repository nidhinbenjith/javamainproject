package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "departments")
public class Department extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotBlank @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    public String name;
    @Size(max = 20)
    @Column(unique = true, length = 20)
    public String code;
}
