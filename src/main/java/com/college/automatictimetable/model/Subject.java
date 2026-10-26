package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "subjects")
public class Subject extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotBlank @Size(max = 120)
    @Column(nullable = false, length = 120)
    public String name;
    @NotBlank @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    public String code;
    @Column(nullable = false)
    public boolean practical;
    @Column(nullable = false)
    public boolean active = true;
    @ManyToOne
    @JoinColumn(name = "department_id")
    public Department department;
}
