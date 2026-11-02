package com.weliao.im.repository;

import com.weliao.im.entity.Conversation;
import com.weliao.im.entity.ConversationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * 发消息前对会话行加悲观写锁，保证同会话内 seq 严格递增、不重号。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findWithLockById(@Param("id") Long id);

    /** 我参与的全部会话，按最后活跃时间倒序。 */
    @Query("""
            select c from Conversation c
            where exists (select 1 from ConversationMember m where m.conversationId = c.id and m.userId = :userId)
            order by c.updatedAt desc
            """)
    List<Conversation> findAllByMember(@Param("userId") Long userId);

    /** 找两个人之间已有的单聊会话。 */
    @Query("""
            select c.id from Conversation c
            where c.type = :type
              and exists (select 1 from ConversationMember m where m.conversationId = c.id and m.userId = :a)
              and exists (select 1 from ConversationMember m where m.conversationId = c.id and m.userId = :b)
            """)
    List<Long> findIdsBetweenUsers(@Param("type") ConversationType type,
                                   @Param("a") Long a,
                                   @Param("b") Long b);
}
