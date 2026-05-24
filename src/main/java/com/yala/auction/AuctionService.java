package com.yala.auction;
import com.yala.model.*;

import com.yala.auction.dto.AuctionResponse;
import com.yala.auction.dto.AuctionSummaryResponse;
import com.yala.auction.dto.CreateAuctionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionService {

    AuctionResponse create(CreateAuctionRequest request, String sellerEmail);

    AuctionResponse findById(Long id);

    Page<AuctionSummaryResponse> findAllActive(Pageable pageable);

    void closeExpiredAuctions();
}
