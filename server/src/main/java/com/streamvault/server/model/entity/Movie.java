package com.streamvault.server.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a movie in the catalog.
 *
 * genres, castMembers, directors are stored as comma-separated strings
 * for H2 compatibility. When migrating to Postgres, these can become
 * native TEXT[] arrays or a join table.
 */
@Entity
@Table(name = "movies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private Integer tmdbId;

    @Column(nullable = false)
    private String title;

    private String originalTitle;
    private Integer releaseYear;

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    private String posterUrl;
    private String backdropUrl;
    private Integer runtimeMinutes;
    private String ageRating;

    /** Comma-separated: "Action,Drama,Sci-Fi" */
    private String genres;

    /** Comma-separated actor names */
    private String castMembers;

    /** Comma-separated director names */
    private String directors;

    /** Absolute path to the video file on disk */
    @Column(nullable = false, unique = true)
    private String localPath;

    /** Path to the movie's folder (for locating subs, posters, etc.) */
    private String folderPath;

    private Long fileSizeBytes;

    @Builder.Default
    private Boolean isTranscoded = false;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
