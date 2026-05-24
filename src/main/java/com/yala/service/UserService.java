package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.exceptions.ResourceNotFoundException;
import com.yala.repository.ListingRepository;
import com.yala.dto.listing.ResponseListingDTO;
import com.yala.dto.user.RequestUpdateUserDTO;
import com.yala.dto.user.ResponseUserDTO;
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
    public ResponseUserDTO getCurrentUser(String email) {
        return modelMapper.map(findByEmailOrThrow(email), ResponseUserDTO.class);
    }

    @Transactional(readOnly = true)
    public ResponseUserDTO getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));
        return modelMapper.map(user, ResponseUserDTO.class);
    }

    @Transactional
    public ResponseUserDTO updateCurrentUser(String email, RequestUpdateUserDTO request) {
        User user = findByEmailOrThrow(email);
        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        return modelMapper.map(userRepository.save(user), ResponseUserDTO.class);
    }

    @Transactional(readOnly = true)
    public Page<ResponseListingDTO> getListingsByUser(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return listingRepository.findBySellerId(userId, pageable)
                .map(listing -> modelMapper.map(listing, ResponseListingDTO.class));
    }

    private User findByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + email));
    }
}
