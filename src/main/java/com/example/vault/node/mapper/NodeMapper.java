package com.example.vault.node.mapper;

import com.example.vault.node.dto.NodeDto;
import com.example.vault.node.entity.Node;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NodeMapper {

    NodeDto toDto(Node node);
}
