package com.yala.bid;

import com.yala.bid.dto.BidResponse;
import com.yala.bid.dto.CreateBidRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BidService {

    BidResponse placeBid(CreateBidRequest request, String bidderEmail);

    Page<BidResponse> findByAuction(Long auctionId, Pageable pageable);

    BidResponse findHighest(Long auctionId);
}
