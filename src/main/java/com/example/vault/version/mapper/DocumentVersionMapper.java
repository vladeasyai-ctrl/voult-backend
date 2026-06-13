package com.example.vault.version.mapper;

import com.example.vault.version.dto.DocumentVersionDto;
import com.example.vault.version.entity.DocumentVersion;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentVersionMapper {

    DocumentVersionDto toDto(DocumentVersion version);

    List<DocumentVersionDto> toDtoList(List<DocumentVersion> versions);
}
