package com.yala.auction;

import static org.assertj.core.api.Assertions.assertThat;

import com.yala.model.Category;
import com.yala.repository.CategoryRepository;
import com.yala.model.Listing;
import com.yala.model.ListingMode;
import com.yala.repository.ListingRepository;
import com.yala.model.ListingStatus;
import com.yala.model.Role;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuctionRepositoryTest.PostgresTestConfig.class)
class AuctionRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresTestConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
        }
    }

    @Autowired private AuctionRepository auctionRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Listing listing;
    private User seller;

    @BeforeEach
    void setUp() {
        auctionRepository.deleteAll();
        listingRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        seller = userRepository.save(User.builder()
                .name("Seller").email("seller@yala.pe").passwordHash("hash")
                .role(Role.SELLER).isVerifiedSeller(true).reputation(0f).build());

        Category category = categoryRepository.save(
                Category.builder().name("Pokemon TCG").description("Cards").build());

        listing = listingRepository.save(Listing.builder()
                .title("Charizard PSA 9").description("First edition holographic")
                .mode(ListingMode.AUCTION).condition("PSA 9")
                .status(ListingStatus.ACTIVE)
                .seller(seller).category(category).build());
    }

    @Test
    void shouldFindByStatusWhenAuctionWithThatStatusExists() {
        auctionRepository.save(Auction.builder()
                .listing(listing).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().plusDays(1))
                .status(AuctionStatus.ACTIVE).build());

        List<Auction> result = auctionRepository.findByStatus(AuctionStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(AuctionStatus.ACTIVE);
    }

    @Test
    void shouldReturnEmptyListWhenNoAuctionMatchesStatus() {
        auctionRepository.save(Auction.builder()
                .listing(listing).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().plusDays(1))
                .status(AuctionStatus.ACTIVE).build());

        List<Auction> result = auctionRepository.findByStatus(AuctionStatus.FINISHED);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnPagedResultsWhenFindByStatusWithPageable() {
        Category cat2 = categoryRepository.save(
                Category.builder().name("Funko Pop").description("Figures").build());
        Listing listing2 = listingRepository.save(Listing.builder()
                .title("Funko Venom Ed Especial").description("Limited edition figure")
                .mode(ListingMode.AUCTION).condition("Mint")
                .status(ListingStatus.ACTIVE)
                .seller(seller).category(cat2).build());

        auctionRepository.save(Auction.builder()
                .listing(listing).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().plusDays(1))
                .status(AuctionStatus.ACTIVE).build());
        auctionRepository.save(Auction.builder()
                .listing(listing2).startingPrice(50f).currentPrice(50f)
                .endsAt(LocalDateTime.now().plusDays(2))
                .status(AuctionStatus.ACTIVE).build());

        Page<Auction> page = auctionRepository.findByStatus(AuctionStatus.ACTIVE, PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void shouldFindExpiredActiveAuctionsWhenEndsAtIsInThePast() {
        auctionRepository.save(Auction.builder()
                .listing(listing).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().minusMinutes(5))
                .status(AuctionStatus.ACTIVE).build());

        List<Auction> expired = auctionRepository
                .findByStatusAndEndsAtBefore(AuctionStatus.ACTIVE, LocalDateTime.now());

        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getStatus()).isEqualTo(AuctionStatus.ACTIVE);
    }

    @Test
    void shouldNotReturnFutureAuctionsWhenQueryingForExpired() {
        auctionRepository.save(Auction.builder()
                .listing(listing).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().plusHours(1))
                .status(AuctionStatus.ACTIVE).build());

        List<Auction> expired = auctionRepository
                .findByStatusAndEndsAtBefore(AuctionStatus.ACTIVE, LocalDateTime.now());

        assertThat(expired).isEmpty();
    }

    @Test
    void shouldNotReturnFinishedAuctionsWhenQueryingForExpiredActive() {
        auctionRepository.save(Auction.builder()
                .listing(listing).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().minusMinutes(1))
                .status(AuctionStatus.FINISHED).build());

        List<Auction> expired = auctionRepository
                .findByStatusAndEndsAtBefore(AuctionStatus.ACTIVE, LocalDateTime.now());

        assertThat(expired).isEmpty();
    }

    @Test
    void shouldFindAuctionByListingIdWhenAuctionExists() {
        Auction saved = auctionRepository.save(Auction.builder()
                .listing(listing).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().plusDays(1))
                .status(AuctionStatus.ACTIVE).build());

        Optional<Auction> found = auctionRepository.findByListingId(listing.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldReturnEmptyWhenNoAuctionExistsForListingId() {
        Optional<Auction> found = auctionRepository.findByListingId(9999L);

        assertThat(found).isEmpty();
    }

    @Test
    void shouldPersistVersionFieldForOptimisticLockingWhenSaved() {
        Auction saved = auctionRepository.save(Auction.builder()
                .listing(listing).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().plusDays(1))
                .status(AuctionStatus.ACTIVE).build());

        assertThat(saved.getVersion()).isNotNull();
    }
}
