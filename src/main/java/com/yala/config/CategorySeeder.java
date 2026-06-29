package com.yala.config;

import com.yala.model.Category;
import com.yala.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ensures the base marketplace categories exist on startup in production (the prod DB has no
 * SQL seed). Idempotent: only inserts the ones missing, by name. Restricted to the {@code prod}
 * profile so it doesn't touch dev/test contexts (dev uses {@code data-dev.sql}).
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class CategorySeeder implements CommandLineRunner {

    /** Order matters: defines the order shown in the category dropdown on a fresh DB. */
    private static final String[][] BASE = {
            {"Cartas TCG", "Cartas coleccionables: Pokémon, Magic, Yu-Gi-Oh y más."},
            {"Figuras", "Figuras coleccionables: Funko Pop, estatuas, modelos y más."},
            {"Comics", "Cómics Marvel, DC, manga y novelas gráficas."},
    };

    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        for (String[] cat : BASE) {
            String name = cat[0];
            if (!categoryRepository.existsByName(name)) {
                categoryRepository.save(Category.builder().name(name).description(cat[1]).build());
                log.info("Seeded base category '{}'", name);
            }
        }
    }
}
