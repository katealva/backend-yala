package com.yala.repository;
import com.yala.model.*;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiveStreamRepository extends JpaRepository<LiveStream, Long> {

    Page<LiveStream> findByStatus(LiveStatus status, Pageable pageable);

    List<LiveStream> findByStatus(LiveStatus status);

    Optional<LiveStream> findByRoomName(String roomName);
}
