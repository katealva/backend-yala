package com.yala.repository;
import com.yala.model.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiveCommentRepository extends JpaRepository<LiveComment, Long> {

    Page<LiveComment> findByLiveStreamIdOrderByCreatedAtDesc(Long liveStreamId, Pageable pageable);
}
