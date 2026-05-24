package com.yala.image;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.image.dto.ImageResponse;
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
public class ImageController {

    private final ImageService imageService;

    @PostMapping(value = "/listings/{listingId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> upload(
            @PathVariable Long listingId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageService.upload(listingId, file, sortOrder, auth.getName()));
    }

    @GetMapping("/listings/{listingId}/images")
    public ResponseEntity<List<ImageResponse>> findByListing(@PathVariable Long listingId) {
        return ResponseEntity.ok(imageService.findByListing(listingId));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long imageId, Authentication auth) {
        imageService.delete(imageId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
