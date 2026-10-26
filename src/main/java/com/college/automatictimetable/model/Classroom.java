package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "classrooms")
public class Classroom extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotBlank @Size(max = 80)
    @Column(nullable = false, unique = true, length = 80)
    public String name;
    @NotNull @Positive
    @Column(nullable = false)
    public Integer capacity;
    @NotBlank @Size(max = 30)
    @Column(nullable = false, length = 30)
    public String roomType = "STANDARD";
    @Column(nullable = false)
    public boolean active = true;
}
