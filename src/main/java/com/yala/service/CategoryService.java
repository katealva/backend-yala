package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.dto.category.ResponseCategoryDTO;
import com.yala.dto.category.RequestCategoryDTO;
import com.yala.exceptions.DuplicateResourceException;
import java.util.List;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and create operations for product categories. */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ModelMapper modelMapper;

    public CategoryService(CategoryRepository categoryRepository, ModelMapper modelMapper) {
        this.categoryRepository = categoryRepository;
        this.modelMapper = modelMapper;
    }

    @Transactional(readOnly = true)
    public List<ResponseCategoryDTO> findAll() {
        return categoryRepository.findAll().stream()
                .map(category -> modelMapper.map(category, ResponseCategoryDTO.class))
                .toList();
    }

    @Transactional
    public ResponseCategoryDTO create(RequestCategoryDTO request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateResourceException(
                    "Category already exists with name: " + request.name());
        }
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return modelMapper.map(categoryRepository.save(category), ResponseCategoryDTO.class);
    }
}
