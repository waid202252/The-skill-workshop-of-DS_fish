package com.weliao.im.repository;

import com.weliao.im.entity.CommunityQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityQuestionRepository extends JpaRepository<CommunityQuestion, Long> {

    Optional<CommunityQuestion> findByIdAndUserId(Long id, Long userId);
}
