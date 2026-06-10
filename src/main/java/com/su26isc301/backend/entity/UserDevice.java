package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "user_devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Profile profile;

    @Column(name = "device_token", nullable = false)
    private String deviceToken;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "last_ip")
    private String lastIp;

    @CreationTimestamp
    @Column(name = "verified_at")
    private ZonedDateTime verifiedAt;
}
