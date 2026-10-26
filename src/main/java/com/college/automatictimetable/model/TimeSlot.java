package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

@Entity
@Table(name = "time_slots")
public class TimeSlot extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotBlank @Size(max = 80)
    @Column(nullable = false, length = 80)
    public String label;
    @NotNull @Column(nullable = false)
    public LocalTime startTime;
    @NotNull @Column(nullable = false)
    public LocalTime endTime;
    @Column(nullable = false)
    public boolean active = true;
}
