package com.streamvault.server.repository;

import com.streamvault.server.model.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MovieRepository extends JpaRepository<Movie, UUID> {
    Optional<Movie> findByTmdbId(Integer tmdbId);
    Optional<Movie> findByLocalPath(String localPath);
    boolean existsByLocalPath(String localPath);
    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
