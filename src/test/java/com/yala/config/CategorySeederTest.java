package com.yala.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.model.Category;
import com.yala.repository.CategoryRepository;
import org.junit.jupiter.api.Test;

class CategorySeederTest {

    @Test
    void seedsTheThreeBaseCategoriesWhenMissing() {
        CategoryRepository repo = mock(CategoryRepository.class);
        when(repo.existsByName(any())).thenReturn(false);

        new CategorySeeder(repo).run();

        verify(repo, times(3)).save(any(Category.class));
        verify(repo).existsByName("Cartas TCG");
        verify(repo).existsByName("Figuras");
        verify(repo).existsByName("Comics");
    }

    @Test
    void isIdempotentWhenCategoriesAlreadyExist() {
        CategoryRepository repo = mock(CategoryRepository.class);
        when(repo.existsByName(any())).thenReturn(true);

        new CategorySeeder(repo).run();

        verify(repo, never()).save(any());
    }
}
