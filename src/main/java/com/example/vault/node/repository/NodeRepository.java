package com.example.vault.node.repository;

import com.example.vault.node.entity.Node;
import com.example.vault.node.entity.NodeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NodeRepository extends JpaRepository<Node, UUID> {

    List<Node> findAllByParentIdIsNullOrderByNameAsc();

    List<Node> findAllByParentIdOrderByNameAsc(UUID parentId);

    List<Node> findAllByOrderByNameAsc();

    boolean existsByParentId(UUID parentId);

    long countByParentIdAndType(UUID parentId, NodeType type);
}
