package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "teachers")
public class Teacher extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotBlank @Size(max = 120)
    @Column(nullable = false, length = 120)
    public String name;
    @Email @Size(max = 160)
    @Column(unique = true, length = 160)
    public String email;
    @Size(max = 30)
    @Column(length = 30)
    public String phone;
    @ManyToOne
    @JoinColumn(name = "department_id")
    public Department department;
    @Column(nullable = false)
    public boolean active = true;
}
