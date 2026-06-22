package com.example.vault.document.mapper;

import com.example.vault.document.dto.DocumentDto;
import com.example.vault.document.entity.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "aiSummary", ignore = true)
    @Mapping(target = "mimeType", ignore = true)
    DocumentDto toDto(Document document);
}
