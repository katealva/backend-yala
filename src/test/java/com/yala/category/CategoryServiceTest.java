package com.yala.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.category.dto.CategoryResponse;
import com.yala.category.dto.CreateCategoryRequest;
import com.yala.config.ModelMapperConfig;
import com.yala.exceptions.DuplicateResourceException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void shouldReturnAllCategoriesWhenInvoked() {
        Category c1 = Category.builder()
                .id(1L).name("Pokémon TCG").description("Cards").build();
        Category c2 = Category.builder()
                .id(2L).name("Funko Pop").description("Figures").build();
        when(categoryRepository.findAll()).thenReturn(List.of(c1, c2));

        List<CategoryResponse> result = categoryService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Pokémon TCG");
        assertThat(result.get(1).name()).isEqualTo("Funko Pop");
    }

    @Test
    void shouldCreateCategoryWhenNameIsUnique() {
        CreateCategoryRequest request = new CreateCategoryRequest("Comics", "Marvel/DC");
        when(categoryRepository.existsByName("Comics")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        CategoryResponse response = categoryService.create(request);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.name()).isEqualTo("Comics");
        assertThat(response.description()).isEqualTo("Marvel/DC");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void shouldThrowDuplicateResourceExceptionWhenNameAlreadyExists() {
        CreateCategoryRequest request = new CreateCategoryRequest("Pokémon TCG", "Cards");
        when(categoryRepository.existsByName("Pokémon TCG")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(categoryRepository, never()).save(any());
    }
}
