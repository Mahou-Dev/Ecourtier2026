package com.ecourtier.common.scheduler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "scheduler_locks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerLockEntity {

    @Id
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "lock_until", nullable = false)
    private LocalDateTime lockUntil;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "locked_by", nullable = false, length = 255)
    private String lockedBy;
}
