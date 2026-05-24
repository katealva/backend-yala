package com.yala.user;

import com.yala.exceptions.ResourceNotFoundException;
import com.yala.listing.ListingRepository;
import com.yala.listing.dto.ListingResponse;
import com.yala.user.dto.UpdateUserRequest;
import com.yala.user.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and update operations for user profiles. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;

    public UserService(UserRepository userRepository, ListingRepository listingRepository) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return UserResponse.from(findByEmailOrThrow(email));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateCurrentUser(String email, UpdateUserRequest request) {
        User user = findByEmailOrThrow(email);
        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<ListingResponse> getListingsByUser(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return listingRepository.findBySellerId(userId, pageable)
                .map(ListingResponse::from);
    }

    private User findByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + email));
    }
}
