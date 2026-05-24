package com.yala.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.yala.model.Category;
import com.yala.repository.CategoryRepository;
import com.yala.model.Role;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ListingRepositoryTest.PostgresTestConfig.class)
class ListingRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresTestConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
        }
    }

    @Autowired private ListingRepository listingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;

    private User seller;
    private User otherSeller;
    private Category pokemon;
    private Category funko;

    @BeforeEach
    void setUp() {
        listingRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        seller = userRepository.save(User.builder()
                .name("Ada").email("ada@yala.pe").passwordHash("hash")
                .role(Role.SELLER).isVerifiedSeller(true).reputation(0f).build());
        otherSeller = userRepository.save(User.builder()
                .name("Bob").email("bob@yala.pe").passwordHash("hash")
                .role(Role.SELLER).isVerifiedSeller(true).reputation(0f).build());
        pokemon = categoryRepository.save(Category.builder()
                .name("Pokémon TCG").description("Cards").build());
        funko = categoryRepository.save(Category.builder()
                .name("Funko Pop").description("Figures").build());
    }

    private Listing buildListing(
            User owner, Category category, String title,
            ListingMode mode, Float fixedPrice, String condition, ListingStatus status) {
        return Listing.builder()
                .title(title)
                .description("desc")
                .mode(mode)
                .fixedPrice(fixedPrice)
                .condition(condition)
                .status(status)
                .seller(owner)
                .category(category)
                .build();
    }

    @Test
    void shouldFindListingsBySellerIdWhenSellerHasListings() {
        listingRepository.save(buildListing(
                seller, pokemon, "Charizard", ListingMode.FIXED, 200f, "USED", ListingStatus.ACTIVE));
        listingRepository.save(buildListing(
                otherSeller, funko, "Pikachu Funko", ListingMode.FIXED, 50f, "NEW", ListingStatus.ACTIVE));

        Page<Listing> result = listingRepository.findBySellerId(seller.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Charizard");
    }

    @Test
    void shouldFindListingsByCategoryNameWhenCategoryMatches() {
        listingRepository.save(buildListing(
                seller, pokemon, "Charizard", ListingMode.FIXED, 200f, "USED", ListingStatus.ACTIVE));
        listingRepository.save(buildListing(
                seller, funko, "Goku Funko", ListingMode.FIXED, 80f, "NEW", ListingStatus.ACTIVE));

        Page<Listing> result = listingRepository.findByCategoryName("Pokémon TCG", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Charizard");
    }

    @Test
    void shouldFindListingsByModeWhenModeMatches() {
        listingRepository.save(buildListing(
                seller, pokemon, "Fixed card", ListingMode.FIXED, 100f, "USED", ListingStatus.ACTIVE));
        listingRepository.save(buildListing(
                seller, pokemon, "Auction card", ListingMode.AUCTION, null, "USED", ListingStatus.ACTIVE));

        Page<Listing> result = listingRepository.findByMode(ListingMode.AUCTION, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Auction card");
    }

    @Test
    void shouldFindListingsByStatusWhenStatusMatches() {
        listingRepository.save(buildListing(
                seller, pokemon, "Active", ListingMode.FIXED, 100f, "USED", ListingStatus.ACTIVE));
        listingRepository.save(buildListing(
                seller, pokemon, "Cancelled", ListingMode.FIXED, 100f, "USED", ListingStatus.CANCELLED));

        Page<Listing> result = listingRepository.findByStatus(ListingStatus.CANCELLED, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Cancelled");
    }

    @Test
    void shouldFindListingsByTitleContainingIgnoreCaseWhenKeywordMatches() {
        listingRepository.save(buildListing(
                seller, pokemon, "Charizard Holo", ListingMode.FIXED, 200f, "USED", ListingStatus.ACTIVE));
        listingRepository.save(buildListing(
                seller, pokemon, "Pikachu Promo", ListingMode.FIXED, 80f, "USED", ListingStatus.ACTIVE));

        Page<Listing> result = listingRepository.findByTitleContainingIgnoreCase(
                "charizard", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Charizard Holo");
    }

    @Test
    void shouldFilterListingsWhenSpecificationCombinesCategoryModeAndPrice() {
        listingRepository.save(buildListing(
                seller, pokemon, "Match", ListingMode.FIXED, 150f, "USED", ListingStatus.ACTIVE));
        listingRepository.save(buildListing(
                seller, pokemon, "Wrong mode", ListingMode.AUCTION, null, "USED", ListingStatus.ACTIVE));
        listingRepository.save(buildListing(
                seller, funko, "Wrong category", ListingMode.FIXED, 150f, "USED", ListingStatus.ACTIVE));
        listingRepository.save(buildListing(
                seller, pokemon, "Wrong price", ListingMode.FIXED, 600f, "USED", ListingStatus.ACTIVE));
        listingRepository.save(buildListing(
                seller, pokemon, "Cancelled", ListingMode.FIXED, 150f, "USED", ListingStatus.CANCELLED));

        Specification<Listing> spec = Specification
                .<Listing>where((root, query, cb) ->
                        cb.equal(root.get("status"), ListingStatus.ACTIVE))
                .and((root, query, cb) ->
                        cb.equal(root.get("category").get("name"), "Pokémon TCG"))
                .and((root, query, cb) ->
                        cb.equal(root.get("mode"), ListingMode.FIXED))
                .and((root, query, cb) ->
                        cb.lessThanOrEqualTo(root.get("fixedPrice"), 500f));

        Page<Listing> result = listingRepository.findAll(spec, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Match");
    }
}
