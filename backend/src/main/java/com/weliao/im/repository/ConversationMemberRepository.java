package com.weliao.im.repository;

import com.weliao.im.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    List<ConversationMember> findByConversationId(Long conversationId);

    List<ConversationMember> findByConversationIdIn(Collection<Long> conversationIds);

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    long countByConversationId(Long conversationId);

    void deleteByConversationIdAndUserId(Long conversationId, Long userId);

    /** 与我处在同一会话里的其他用户 id（去重），用于推送在线状态。 */
    @Query("""
            select distinct m2.userId from ConversationMember m1, ConversationMember m2
            where m1.conversationId = m2.conversationId
              and m1.userId = :userId
              and m2.userId <> :userId
            """)
    List<Long> findPeerUserIds(@Param("userId") Long userId);
}
