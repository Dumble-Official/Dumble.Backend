package com.dumble.service.gym.domain.entity;

import com.dumble.service.gym.domain.enumuration.GymImageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "gym_images")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GymImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @Column(length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type")
    private GymImageType type = GymImageType.NORMAL;

    @Column(name = "public_id")
    private String publicId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
