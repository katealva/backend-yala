package com.yala.auction;

import com.yala.auction.dto.AuctionResponse;
import com.yala.auction.dto.AuctionSummaryResponse;
import com.yala.auction.dto.CreateAuctionRequest;
import com.yala.bid.dto.BidResponse;
import com.yala.bid.dto.CreateBidRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionService {

    AuctionResponse create(CreateAuctionRequest request, String sellerEmail);

    AuctionResponse findById(Long id);

    Page<AuctionSummaryResponse> findAllActive(Pageable pageable);

    BidResponse placeBid(CreateBidRequest request, String bidderEmail);

    void closeExpiredAuctions();
}
