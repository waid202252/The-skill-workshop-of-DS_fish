package com.weliao.im.repository;

import com.weliao.im.entity.SkillInstall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SkillInstallRepository extends JpaRepository<SkillInstall, Long> {

    List<SkillInstall> findByUserId(Long userId);

    /** 批量查「这些技能里我装了哪些」，避免列表渲染时逐个查询 */
    List<SkillInstall> findByUserIdAndSkillIdIn(Long userId, Collection<Long> skillIds);

    Optional<SkillInstall> findByUserIdAndSkillId(Long userId, Long skillId);

    boolean existsByUserIdAndSkillId(Long userId, Long skillId);

    long countBySkillId(Long skillId);

    void deleteByUserIdAndSkillId(Long userId, Long skillId);
}
