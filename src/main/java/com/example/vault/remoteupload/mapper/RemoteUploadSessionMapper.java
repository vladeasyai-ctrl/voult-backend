package com.example.vault.remoteupload.mapper;

import com.example.vault.remoteupload.dto.RemoteUploadSessionDto;
import com.example.vault.remoteupload.entity.RemoteUploadSession;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RemoteUploadSessionMapper {

    RemoteUploadSessionDto toDto(RemoteUploadSession session);
}
