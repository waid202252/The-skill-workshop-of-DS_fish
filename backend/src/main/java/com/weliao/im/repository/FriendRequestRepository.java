package com.weliao.im.repository;

import com.weliao.im.entity.FriendRequest;
import com.weliao.im.entity.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    List<FriendRequest> findByToUserIdAndStatusOrderByCreatedAtDesc(Long toUserId, RequestStatus status);

    List<FriendRequest> findByFromUserIdOrderByCreatedAtDesc(Long fromUserId);

    Optional<FriendRequest> findByIdAndToUserId(Long id, Long toUserId);

    boolean existsByFromUserIdAndToUserIdAndStatus(Long fromUserId, Long toUserId, RequestStatus status);
}
