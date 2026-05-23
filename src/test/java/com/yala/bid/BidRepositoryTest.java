package com.yala.bid;

import static org.assertj.core.api.Assertions.assertThat;

import com.yala.auction.Auction;
import com.yala.auction.AuctionRepository;
import com.yala.auction.AuctionStatus;
import com.yala.category.Category;
import com.yala.category.CategoryRepository;
import com.yala.listing.Listing;
import com.yala.listing.ListingMode;
import com.yala.listing.ListingRepository;
import com.yala.listing.ListingStatus;
import com.yala.user.Role;
import com.yala.user.User;
import com.yala.user.UserRepository;
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
@Import(BidRepositoryTest.PostgresTestConfig.class)
class BidRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresTestConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
        }
    }

    @Autowired private BidRepository bidRepository;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Auction auction;
    private User bidder1;
    private User bidder2;

    @BeforeEach
    void setUp() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        listingRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        User seller = userRepository.save(User.builder()
                .name("Seller").email("seller@yala.pe").passwordHash("hash")
                .role(Role.SELLER).isVerifiedSeller(true).reputation(0f).build());
        bidder1 = userRepository.save(User.builder()
                .name("Ada").email("ada@yala.pe").passwordHash("hash")
                .role(Role.USER).isVerifiedSeller(false).reputation(0f).build());
        bidder2 = userRepository.save(User.builder()
                .name("Cleo").email("cleo@yala.pe").passwordHash("hash")
                .role(Role.USER).isVerifiedSeller(false).reputation(0f).build());

        Category category = categoryRepository.save(
                Category.builder().name("Pokémon TCG").description("Cards").build());

        Listing listing = listingRepository.save(Listing.builder()
                .title("Pikachu Illustrator").description("1998 promo")
                .mode(ListingMode.AUCTION).condition("NEW")
                .status(ListingStatus.ACTIVE)
                .seller(seller).category(category).build());

        auction = auctionRepository.save(Auction.builder()
                .listing(listing).startingPrice(100f).currentPrice(100f)
                .endsAt(LocalDateTime.now().plusDays(1))
                .status(AuctionStatus.ACTIVE).build());
    }

    @Test
    void shouldFindFirstByAuctionIdOrderByAmountDescWhenBidsExist() {
        bidRepository.save(Bid.builder().amount(150f).auction(auction).bidder(bidder1).build());
        bidRepository.save(Bid.builder().amount(300f).auction(auction).bidder(bidder2).build());
        bidRepository.save(Bid.builder().amount(200f).auction(auction).bidder(bidder1).build());

        Optional<Bid> highest = bidRepository.findFirstByAuctionIdOrderByAmountDesc(auction.getId());

        assertThat(highest).isPresent();
        assertThat(highest.get().getAmount()).isEqualTo(300f);
    }

    @Test
    void shouldReturnEmptyWhenAuctionHasNoBids() {
        Optional<Bid> highest = bidRepository.findFirstByAuctionIdOrderByAmountDesc(auction.getId());

        assertThat(highest).isEmpty();
    }

    @Test
    void shouldFindByAuctionIdOrderByAmountDescWhenBidsExist() {
        bidRepository.save(Bid.builder().amount(150f).auction(auction).bidder(bidder1).build());
        bidRepository.save(Bid.builder().amount(300f).auction(auction).bidder(bidder2).build());
        bidRepository.save(Bid.builder().amount(200f).auction(auction).bidder(bidder1).build());

        List<Bid> ordered = bidRepository.findByAuctionIdOrderByAmountDesc(auction.getId());

        assertThat(ordered).hasSize(3);
        assertThat(ordered.get(0).getAmount()).isEqualTo(300f);
        assertThat(ordered.get(1).getAmount()).isEqualTo(200f);
        assertThat(ordered.get(2).getAmount()).isEqualTo(150f);
    }

    @Test
    void shouldCountBidsByAuctionIdWhenBidsExist() {
        bidRepository.save(Bid.builder().amount(150f).auction(auction).bidder(bidder1).build());
        bidRepository.save(Bid.builder().amount(200f).auction(auction).bidder(bidder2).build());

        long count = bidRepository.countByAuctionId(auction.getId());

        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldPageBidsByAuctionIdWhenBidsExist() {
        for (int i = 1; i <= 5; i++) {
            bidRepository.save(Bid.builder()
                    .amount(100f + i * 10).auction(auction).bidder(bidder1).build());
        }

        Page<Bid> page = bidRepository.findByAuctionId(auction.getId(), PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
    }
}
