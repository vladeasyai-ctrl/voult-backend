package com.example.vault.space.mapper;

import com.example.vault.space.dto.SpaceDto;
import com.example.vault.space.entity.Space;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SpaceMapper {

    SpaceDto toDto(Space space);
}
