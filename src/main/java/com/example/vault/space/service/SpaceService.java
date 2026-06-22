package com.example.vault.space.service;

import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.node.service.NodeService;
import com.example.vault.space.dto.CreateSpaceRequest;
import com.example.vault.space.dto.SpaceDto;
import com.example.vault.space.dto.UpdateSpaceRequest;
import com.example.vault.space.entity.Space;
import com.example.vault.space.mapper.SpaceMapper;
import com.example.vault.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final SpaceMapper spaceMapper;
    private final NodeService nodeService;

    @Transactional(readOnly = true)
    public List<SpaceDto> list() {
        return spaceRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(spaceMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SpaceDto get(UUID id) {
        return spaceMapper.toDto(findSpaceOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TreeNodeDto> getTree(UUID spaceId) {
        findSpaceOrThrow(spaceId);
        return nodeService.getTree(spaceId);
    }

    @Transactional
    public SpaceDto create(CreateSpaceRequest request) {
        String name = request.name().trim();
        if (spaceRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "SPACE_NAME_EXISTS",
                    "Space with this name already exists");
        }

        int sortOrder = spaceRepository.findAllByOrderBySortOrderAscNameAsc().size();
        Space space = Space.builder()
                .id(UUID.randomUUID())
                .name(name)
                .presetId(request.presetId())
                .sortOrder(sortOrder)
                .settings(request.settings())
                .build();

        return spaceMapper.toDto(spaceRepository.save(space));
    }

    @Transactional
    public SpaceDto update(UUID id, UpdateSpaceRequest request) {
        Space space = findSpaceOrThrow(id);
        String name = request.name().trim();
        if (!space.getName().equalsIgnoreCase(name)
                && spaceRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "SPACE_NAME_EXISTS",
                    "Space with this name already exists");
        }

        space.setName(name);
        space.setPresetId(request.presetId());
        space.setSettings(request.settings());
        return spaceMapper.toDto(spaceRepository.save(space));
    }

    @Transactional
    public void delete(UUID id) {
        if (!spaceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Space", id);
        }
        spaceRepository.deleteById(id);
    }

    public Space findSpaceOrThrow(UUID id) {
        return spaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Space", id));
    }
}
