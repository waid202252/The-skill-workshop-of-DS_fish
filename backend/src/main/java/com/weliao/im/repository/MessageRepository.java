package com.weliao.im.repository;

import com.weliao.im.entity.MessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    /** 游标分页：取 seq < beforeSeq 的最近 N 条（倒序取，服务层反转成正序）。 */
    List<MessageEntity> findByConversationIdAndSeqLessThanOrderBySeqDesc(Long conversationId,
                                                                       Long beforeSeq,
                                                                       Pageable pageable);

    /** 增量补齐：取 seq > afterSeq 的最早 N 条（升序），用于断线重连后追上积压消息。 */
    List<MessageEntity> findByConversationIdAndSeqGreaterThanOrderBySeqAsc(Long conversationId,
                                                                          Long afterSeq,
                                                                          Pageable pageable);

    /** 未读数：seq 大于已读位置、且不是自己发的消息条数。 */
    long countByConversationIdAndSeqGreaterThanAndSenderIdNot(Long conversationId, Long seq, Long senderId);

    long countByConversationId(Long conversationId);
}
