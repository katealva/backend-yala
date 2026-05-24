package com.yala.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.category.Category;
import com.yala.category.CategoryRepository;
import com.yala.config.ModelMapperConfig;
import com.yala.exceptions.InvalidBidException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.listing.dto.CreateListingRequest;
import com.yala.listing.dto.ListingResponse;
import com.yala.tag.TagRepository;
import com.yala.user.Role;
import com.yala.user.User;
import com.yala.user.UserRepository;
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
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TagRepository tagRepository;
    @Mock private UserRepository userRepository;

    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @InjectMocks
    private ListingServiceImpl listingService;

    private User verifiedSeller() {
        return User.builder()
                .id(1L).name("Ada").email("ada@yala.pe")
                .role(Role.SELLER).isVerifiedSeller(true).build();
    }

    private User unverifiedSeller() {
        return User.builder()
                .id(2L).name("Bob").email("bob@yala.pe")
                .role(Role.SELLER).isVerifiedSeller(false).build();
    }

    private User regularUser() {
        return User.builder()
                .id(3L).name("Cleo").email("cleo@yala.pe")
                .role(Role.USER).isVerifiedSeller(false).build();
    }

    private Category sampleCategory() {
        return Category.builder().id(10L).name("Pokémon TCG").build();
    }

    private CreateListingRequest fixedRequest() {
        return new CreateListingRequest(
                "Charizard Holo", "Near mint", "FIXED", 250.0f, "USED", 10L, List.of("rare"));
    }

    private CreateListingRequest auctionRequest() {
        return new CreateListingRequest(
                "Pikachu Illustrator", "1998 promo", "AUCTION", null, "NEW", 10L, List.of());
    }

    @Test
    void shouldCreateListingWhenSellerIsVerified() {
        User seller = verifiedSeller();
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(seller));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(sampleCategory()));
        when(tagRepository.findByName("rare")).thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> {
            Listing l = inv.getArgument(0);
            l.setId(99L);
            return l;
        });

        ListingResponse response = listingService.create(fixedRequest(), "ada@yala.pe");

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.mode()).isEqualTo("FIXED");
        assertThat(response.fixedPrice()).isEqualTo(250.0f);
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(listingRepository).save(any(Listing.class));
    }

    @Test
    void shouldCreateAuctionListingWhenFixedPriceIsNull() {
        User seller = verifiedSeller();
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(seller));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(sampleCategory()));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> {
            Listing l = inv.getArgument(0);
            l.setId(100L);
            return l;
        });

        ListingResponse response = listingService.create(auctionRequest(), "ada@yala.pe");

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.mode()).isEqualTo("AUCTION");
        assertThat(response.fixedPrice()).isNull();
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenRegularUserCreatesListing() {
        when(userRepository.findByEmail("cleo@yala.pe")).thenReturn(Optional.of(regularUser()));

        assertThatThrownBy(() -> listingService.create(fixedRequest(), "cleo@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenSellerIsNotVerified() {
        when(userRepository.findByEmail("bob@yala.pe")).thenReturn(Optional.of(unverifiedSeller()));

        assertThatThrownBy(() -> listingService.create(fixedRequest(), "bob@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void shouldThrowInvalidBidExceptionWhenFixedModeHasNullPrice() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(verifiedSeller()));
        CreateListingRequest req = new CreateListingRequest(
                "Card", "desc", "FIXED", null, "USED", 10L, List.of());

        assertThatThrownBy(() -> listingService.create(req, "ada@yala.pe"))
                .isInstanceOf(InvalidBidException.class)
                .hasMessageContaining("positive fixedPrice");
        verify(listingRepository, never()).save(any());
    }

    @Test
    void shouldThrowInvalidBidExceptionWhenAuctionModeDeclaresFixedPrice() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(verifiedSeller()));
        CreateListingRequest req = new CreateListingRequest(
                "Card", "desc", "AUCTION", 100.0f, "USED", 10L, List.of());

        assertThatThrownBy(() -> listingService.create(req, "ada@yala.pe"))
                .isInstanceOf(InvalidBidException.class)
                .hasMessageContaining("must not declare a fixedPrice");
    }

    @Test
    void shouldThrowInvalidBidExceptionWhenModeIsUnknown() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(verifiedSeller()));
        CreateListingRequest req = new CreateListingRequest(
                "Card", "desc", "BARTER", null, "USED", 10L, List.of());

        assertThatThrownBy(() -> listingService.create(req, "ada@yala.pe"))
                .isInstanceOf(InvalidBidException.class);
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenCategoryDoesNotExist() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(verifiedSeller()));
        when(categoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.create(fixedRequest(), "ada@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    @Test
    void shouldReturnListingWhenFindByIdInvokedWithExistingId() {
        Listing listing = Listing.builder()
                .id(5L).title("Charizard").mode(ListingMode.FIXED).fixedPrice(300f)
                .condition("USED").status(ListingStatus.ACTIVE)
                .seller(verifiedSeller()).category(sampleCategory()).build();
        when(listingRepository.findById(5L)).thenReturn(Optional.of(listing));

        ListingResponse response = listingService.findById(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.title()).isEqualTo("Charizard");
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenFindByIdMissing() {
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.findById(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReturnPagedListingsWhenFindAllInvoked() {
        Listing l = Listing.builder()
                .id(1L).title("Card").mode(ListingMode.FIXED).fixedPrice(100f)
                .condition("USED").status(ListingStatus.ACTIVE)
                .seller(verifiedSeller()).category(sampleCategory()).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(l)));

        Page<ListingResponse> page = listingService.findAll(
                pageable, "Pokémon TCG", "FIXED", "USED", 50f, 200f, "card");

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(1L);
    }

    @Test
    void shouldUpdateListingWhenRequesterIsOwner() {
        User seller = verifiedSeller();
        Listing existing = Listing.builder()
                .id(7L).title("Old").mode(ListingMode.FIXED).fixedPrice(100f)
                .condition("USED").status(ListingStatus.ACTIVE)
                .seller(seller).category(sampleCategory()).build();
        when(listingRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(sampleCategory()));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        ListingResponse response = listingService.update(
                7L,
                new CreateListingRequest(
                        "New Title", "desc", "FIXED", 150f, "USED", 10L, List.of()),
                "ada@yala.pe");

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.fixedPrice()).isEqualTo(150f);
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenUpdaterIsNotOwner() {
        Listing existing = Listing.builder()
                .id(7L).title("Old").mode(ListingMode.FIXED).fixedPrice(100f)
                .condition("USED").status(ListingStatus.ACTIVE)
                .seller(verifiedSeller()).category(sampleCategory()).build();
        when(listingRepository.findById(7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> listingService.update(
                7L, fixedRequest(), "intruder@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void shouldCancelListingWhenRequesterIsOwner() {
        User seller = verifiedSeller();
        Listing existing = Listing.builder()
                .id(8L).title("To cancel").mode(ListingMode.FIXED).fixedPrice(100f)
                .condition("USED").status(ListingStatus.ACTIVE)
                .seller(seller).category(sampleCategory()).build();
        when(listingRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        listingService.cancel(8L, "ada@yala.pe");

        assertThat(existing.getStatus()).isEqualTo(ListingStatus.CANCELLED);
        verify(listingRepository).save(existing);
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenCancelerIsNotOwner() {
        Listing existing = Listing.builder()
                .id(8L).title("To cancel").mode(ListingMode.FIXED).fixedPrice(100f)
                .condition("USED").status(ListingStatus.ACTIVE)
                .seller(verifiedSeller()).category(sampleCategory()).build();
        when(listingRepository.findById(8L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> listingService.cancel(8L, "intruder@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void shouldAllowAdminToCreateListingEvenWhenNotVerifiedSeller() {
        User admin = User.builder()
                .id(99L).email("admin@yala.pe").role(Role.ADMIN).isVerifiedSeller(false).build();
        when(userRepository.findByEmail("admin@yala.pe")).thenReturn(Optional.of(admin));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(sampleCategory()));
        when(tagRepository.findByName("rare")).thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> {
            Listing l = inv.getArgument(0);
            l.setId(101L);
            return l;
        });

        ListingResponse response = listingService.create(fixedRequest(), "admin@yala.pe");

        assertThat(response.id()).isEqualTo(101L);
    }
}
