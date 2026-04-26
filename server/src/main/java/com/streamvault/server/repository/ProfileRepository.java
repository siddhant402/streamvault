package com.streamvault.server.repository;

import com.streamvault.server.model.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    List<Profile> findByAccountId(UUID accountId);
    long countByAccountId(UUID accountId);
}
