package com.yala.image;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.exceptions.ImageLimitExceededException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.image.dto.ImageResponse;
import com.yala.model.Listing;
import com.yala.repository.ListingRepository;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class ImageService {

    private static final int MAX_IMAGES_PER_LISTING = 5;

    private final ImageRepository imageRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final S3Client s3Client;
    private final ModelMapper modelMapper;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Transactional
    public ImageResponse upload(Long listingId, MultipartFile file,
            Integer sortOrder, String sellerEmail) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing not found with id: " + listingId));

        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!listing.getSeller().getId().equals(seller.getId())) {
            throw new UnauthorizedException("Only the seller can upload images to this listing");
        }

        if (imageRepository.countByListingId(listingId) >= MAX_IMAGES_PER_LISTING) {
            throw new ImageLimitExceededException(
                    "A listing cannot have more than " + MAX_IMAGES_PER_LISTING + " images");
        }

        String key = buildKey(listingId, file.getOriginalFilename());
        String url = uploadToS3(file, key);

        Image saved = imageRepository.save(Image.builder()
                .url(url)
                .sortOrder(sortOrder)
                .listing(listing)
                .build());

        return modelMapper.map(saved, ImageResponse.class);
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> findByListing(Long listingId) {
        return imageRepository.findByListingIdOrderBySortOrderAsc(listingId).stream()
                .map(image -> modelMapper.map(image, ImageResponse.class))
                .toList();
    }

    @Transactional
    public void delete(Long imageId, String requesterEmail) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId));

        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!image.getListing().getSeller().getId().equals(requester.getId())) {
            throw new UnauthorizedException("Only the seller can delete this image");
        }

        deleteFromS3(extractKey(image.getUrl()));
        imageRepository.delete(image);
    }

    private String buildKey(Long listingId, String originalFilename) {
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        return "listings/" + listingId + "/" + UUID.randomUUID() + ext;
    }

    private String uploadToS3(MultipartFile file, String key) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    private void deleteFromS3(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private String extractKey(String url) {
        return url.substring(url.indexOf(".amazonaws.com/") + ".amazonaws.com/".length());
    }
}
