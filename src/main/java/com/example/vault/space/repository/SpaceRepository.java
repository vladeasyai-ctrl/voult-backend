package com.example.vault.space.repository;

import com.example.vault.space.entity.Space;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID> {

    List<Space> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
