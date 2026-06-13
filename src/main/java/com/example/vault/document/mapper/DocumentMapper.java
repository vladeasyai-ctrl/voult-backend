package com.example.vault.document.mapper;

import com.example.vault.document.dto.DocumentDto;
import com.example.vault.document.entity.Document;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    DocumentDto toDto(Document document);
}
