package com.example.vault.importing.mapper;

import com.example.vault.importing.dto.ImportSessionDto;
import com.example.vault.importing.entity.ImportSession;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ImportSessionMapper {

    ImportSessionDto toDto(ImportSession session);
}
