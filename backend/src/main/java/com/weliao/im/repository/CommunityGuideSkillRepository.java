package com.weliao.im.repository;

import com.weliao.im.entity.CommunityGuideSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommunityGuideSkillRepository extends JpaRepository<CommunityGuideSkill, Long> {

    List<CommunityGuideSkill> findByGuideIdOrderByDisplayOrderAsc(Long guideId);

    List<CommunityGuideSkill> findByGuideIdIn(Collection<Long> guideIds);

    List<CommunityGuideSkill> findByFullName(String fullName);
}
