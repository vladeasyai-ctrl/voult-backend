package com.example.vault.asset.mapper;

import com.example.vault.asset.dto.AssetDto;
import com.example.vault.asset.entity.Asset;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AssetMapper {

    AssetDto toDto(Asset asset);
}
