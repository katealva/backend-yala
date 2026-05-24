package com.yala.image;
import com.yala.model.*;

import com.yala.image.dto.ImageResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService {

    ImageResponse upload(Long listingId, MultipartFile file, Integer sortOrder, String sellerEmail);

    List<ImageResponse> findByListing(Long listingId);

    void delete(Long imageId, String requesterEmail);
}
