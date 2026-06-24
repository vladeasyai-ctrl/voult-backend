package com.example.vault.node.repository;

import com.example.vault.node.entity.Node;
import com.example.vault.node.entity.NodeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeRepository extends JpaRepository<Node, UUID> {

    List<Node> findAllBySpaceIdAndParentIdIsNullOrderByNameAsc(UUID spaceId);

    List<Node> findAllBySpaceIdAndParentIdOrderByNameAsc(UUID spaceId, UUID parentId);

    List<Node> findAllBySpaceIdOrderByNameAsc(UUID spaceId);

    List<Node> findAllByParentIdOrderByNameAsc(UUID parentId);

    boolean existsByParentId(UUID parentId);

    long countByParentIdAndType(UUID parentId, NodeType type);

    @Query("""
            SELECT n FROM Node n
            WHERE n.spaceId = :spaceId AND n.type = :type AND LOWER(n.name) = LOWER(:name)
            AND ((:parentId IS NULL AND n.parentId IS NULL) OR n.parentId = :parentId)
            """)
    Optional<Node> findFolderBySpaceParentAndName(
            @Param("spaceId") UUID spaceId,
            @Param("parentId") UUID parentId,
            @Param("name") String name,
            @Param("type") NodeType type
    );

    @Query("""
            SELECT DISTINCT n.spaceId FROM Node n
            WHERE n.parentId IS NULL AND n.type = com.example.vault.node.entity.NodeType.FOLDER
            AND LOWER(n.name) = LOWER(:name)
            """)
    List<UUID> findDistinctSpaceIdsByRootBranchName(@Param("name") String name);
}
