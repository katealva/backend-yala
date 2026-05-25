package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.exceptions.DuplicateResourceException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.dto.tag.RequestTagDTO;
import com.yala.dto.tag.ResponseTagDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<ResponseTagDTO> findAll() {
        return tagRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseTagDTO findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public ResponseTagDTO create(RequestTagDTO request) {
        if (tagRepository.existsByName(request.name())) {
            throw new DuplicateResourceException(
                    "Tag with name '" + request.name() + "' already exists");
        }
        Tag saved = tagRepository.save(Tag.builder().name(request.name()).build());
        return toResponse(saved);
    }

    @Transactional
    public ResponseTagDTO update(Long id, RequestTagDTO request) {
        Tag tag = findOrThrow(id);
        if (!tag.getName().equals(request.name()) && tagRepository.existsByName(request.name())) {
            throw new DuplicateResourceException(
                    "Tag with name '" + request.name() + "' already exists");
        }
        tag.setName(request.name());
        return toResponse(tagRepository.save(tag));
    }

    @Transactional
    public void delete(Long id) {
        tagRepository.delete(findOrThrow(id));
    }

    private Tag findOrThrow(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found with id: " + id));
    }

    private ResponseTagDTO toResponse(Tag tag) {
        return new ResponseTagDTO(tag.getId(), tag.getName());
    }
}
