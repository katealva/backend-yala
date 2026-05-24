package com.yala.tag;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.exceptions.DuplicateResourceException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.tag.dto.TagRequest;
import com.yala.tag.dto.TagResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> findAll() {
        return tagRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TagResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public TagResponse create(TagRequest request) {
        if (tagRepository.existsByName(request.name())) {
            throw new DuplicateResourceException(
                    "Tag with name '" + request.name() + "' already exists");
        }
        Tag saved = tagRepository.save(Tag.builder().name(request.name()).build());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public TagResponse update(Long id, TagRequest request) {
        Tag tag = findOrThrow(id);
        if (!tag.getName().equals(request.name()) && tagRepository.existsByName(request.name())) {
            throw new DuplicateResourceException(
                    "Tag with name '" + request.name() + "' already exists");
        }
        tag.setName(request.name());
        return toResponse(tagRepository.save(tag));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        tagRepository.delete(findOrThrow(id));
    }

    private Tag findOrThrow(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found with id: " + id));
    }

    private TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
