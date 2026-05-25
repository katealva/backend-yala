package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.dto.tag.RequestTagDTO;
import com.yala.dto.tag.ResponseTagDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tags", description = "Catálogo de etiquetas reutilizables (mint, holo, vintage…) que se asocian a los listings. Mutación restringida a ADMIN.")
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "Lista todos los tags",
            description = "Devuelve el catálogo completo de tags disponibles. Endpoint público.")
    public ResponseEntity<List<ResponseTagDTO>> findAll() {
        return ResponseEntity.ok(tagService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un tag", description = "Devuelve un tag por su id. Endpoint público.")
    public ResponseEntity<ResponseTagDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crea un nuevo tag",
            description = "Requiere rol ADMIN. El nombre debe ser único en el catálogo.")
    public ResponseEntity<ResponseTagDTO> create(@Valid @RequestBody RequestTagDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualiza un tag existente",
            description = "Requiere rol ADMIN. Modifica el nombre/descripción de un tag.")
    public ResponseEntity<ResponseTagDTO> update(@PathVariable Long id,
            @Valid @RequestBody RequestTagDTO request) {
        return ResponseEntity.ok(tagService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Elimina un tag",
            description = "Requiere rol ADMIN. Falla si el tag está asociado a listings.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
