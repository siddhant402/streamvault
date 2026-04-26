package com.streamvault.server.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "watch_events",
        indexes = {
            @Index(name = "idx_we_profile_movie", columnList = "profile_id, movie_id"),
            @Index(name = "idx_we_profile_id",    columnList = "profile_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchEvent {

    public enum EventType { PROGRESS, COMPLETE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    /** Playhead position in seconds at the time of the event */
    private Integer positionSeconds;

    @CreationTimestamp
    private Instant occurredAt;
}
