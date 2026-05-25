package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.dto.image.ResponseImageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Subida y gestión de imágenes asociadas a un listing (almacenadas en AWS S3, máximo 5 por listing)")
public class ImageController {

    private final ImageService imageService;

    @PostMapping(value = "/listings/{listingId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube una imagen a un listing",
            description = "Sube un archivo a AWS S3 y lo asocia al listing. Sólo el dueño del listing puede subir. Máximo 5 imágenes por listing.")
    public ResponseEntity<ResponseImageDTO> upload(
            @PathVariable Long listingId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageService.upload(listingId, file, sortOrder, auth.getName()));
    }

    @GetMapping("/listings/{listingId}/images")
    @Operation(summary = "Lista las imágenes de un listing",
            description = "Devuelve todas las imágenes asociadas a un listing, ordenadas por sortOrder ascendente. Endpoint público.")
    public ResponseEntity<List<ResponseImageDTO>> findByListing(@PathVariable Long listingId) {
        return ResponseEntity.ok(imageService.findByListing(listingId));
    }

    @DeleteMapping("/images/{imageId}")
    @Operation(summary = "Elimina una imagen",
            description = "Elimina la imagen del listing y del bucket S3. Sólo el dueño del listing puede eliminar.")
    public ResponseEntity<Void> delete(@PathVariable Long imageId, Authentication auth) {
        imageService.delete(imageId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
