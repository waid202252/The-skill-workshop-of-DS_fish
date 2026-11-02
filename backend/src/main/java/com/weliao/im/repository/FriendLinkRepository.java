package com.weliao.im.repository;

import com.weliao.im.entity.FriendLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendLinkRepository extends JpaRepository<FriendLink, Long> {

    List<FriendLink> findByUserId(Long userId);

    boolean existsByUserIdAndFriendId(Long userId, Long friendId);

    void deleteByUserIdAndFriendId(Long userId, Long friendId);
}
