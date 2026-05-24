package com.yala.user;
import com.yala.model.*;

import com.yala.exceptions.ResourceNotFoundException;
import com.yala.listing.ListingRepository;
import com.yala.listing.dto.ListingResponse;
import com.yala.user.dto.UpdateUserRequest;
import com.yala.user.dto.UserResponse;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and update operations for user profiles. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final ModelMapper modelMapper;

    public UserService(UserRepository userRepository, ListingRepository listingRepository,
            ModelMapper modelMapper) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.modelMapper = modelMapper;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return modelMapper.map(findByEmailOrThrow(email), UserResponse.class);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));
        return modelMapper.map(user, UserResponse.class);
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
        return modelMapper.map(userRepository.save(user), UserResponse.class);
    }

    @Transactional(readOnly = true)
    public Page<ListingResponse> getListingsByUser(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return listingRepository.findBySellerId(userId, pageable)
                .map(listing -> modelMapper.map(listing, ListingResponse.class));
    }

    private User findByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + email));
    }
}
