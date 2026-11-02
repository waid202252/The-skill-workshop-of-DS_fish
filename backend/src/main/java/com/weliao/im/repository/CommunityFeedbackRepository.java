package com.weliao.im.repository;

import com.weliao.im.entity.CommunityFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityFeedbackRepository extends JpaRepository<CommunityFeedback, Long> {

    Optional<CommunityFeedback> findByGuideIdAndUserId(Long guideId, Long userId);

    long countByGuideIdAndHelpfulTrue(Long guideId);

    long countByGuideIdAndHelpfulFalse(Long guideId);
}
