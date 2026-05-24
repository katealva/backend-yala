package com.yala.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.config.ModelMapperConfig;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.listing.Listing;
import com.yala.listing.ListingMode;
import com.yala.listing.ListingRepository;
import com.yala.listing.dto.ListingResponse;
import com.yala.user.dto.UpdateUserRequest;
import com.yala.user.dto.UserResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ListingRepository listingRepository;

    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @InjectMocks
    private UserService userService;

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .name("Ada Lovelace")
                .email("ada@yala.pe")
                .passwordHash("hashed-password")
                .role(Role.USER)
                .reputation(4.5f)
                .isVerifiedSeller(false)
                .build();
    }

    @Test
    void shouldReturnUserWhenIdExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));

        UserResponse response = userService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("ada@yala.pe");
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenUserIdDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReturnCurrentUserWhenEmailExists() {
        when(userRepository.findByEmail("ada@yala.pe"))
                .thenReturn(Optional.of(sampleUser()));

        UserResponse response = userService.getCurrentUser("ada@yala.pe");

        assertThat(response.name()).isEqualTo("Ada Lovelace");
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenCurrentUserEmailDoesNotExist() {
        when(userRepository.findByEmail("ghost@yala.pe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser("ghost@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldUpdateNameAndAvatarWhenRequestIsValid() {
        User user = sampleUser();
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        UpdateUserRequest request =
                new UpdateUserRequest("Ada L.", "https://img.yala.pe/avatar.png");

        UserResponse response = userService.updateCurrentUser("ada@yala.pe", request);

        assertThat(response.name()).isEqualTo("Ada L.");
        assertThat(response.avatarUrl()).isEqualTo("https://img.yala.pe/avatar.png");
        verify(userRepository).save(user);
    }

    @Test
    void shouldReturnListingsWhenUserExists() {
        Listing listing = Listing.builder()
                .id(10L)
                .title("Charizard PSA 10")
                .mode(ListingMode.AUCTION)
                .condition("PSA 10 — Gem Mint")
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(listingRepository.findBySellerId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(listing)));

        Page<ListingResponse> result = userService.getListingsByUser(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Charizard PSA 10");
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenListingsRequestedForMissingUser() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> userService.getListingsByUser(99L, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(listingRepository, never()).findBySellerId(any(), any());
    }
}
