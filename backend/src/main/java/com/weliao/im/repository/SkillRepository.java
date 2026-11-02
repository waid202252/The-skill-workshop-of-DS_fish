package com.weliao.im.repository;

import com.weliao.im.entity.Skill;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByGithubId(Long githubId);

    Optional<Skill> findByFullName(String fullName);

    List<Skill> findByIdIn(Collection<Long> ids);

    List<Skill> findTop20ByOrderByStarsDesc();

    /**
     * 本地降级搜索：GitHub 不可用（限流/网络故障）时，退回已缓存的技能里模糊匹配。
     * 有缓存总比直接报错强。
     */
    @Query("""
            select s from Skill s
            where lower(s.fullName) like lower(concat('%', :kw, '%'))
               or lower(coalesce(s.description, '')) like lower(concat('%', :kw, '%'))
               or lower(coalesce(s.topics, '')) like lower(concat('%', :kw, '%'))
            order by s.stars desc
            """)
    List<Skill> searchLocal(@Param("kw") String keyword, Pageable pageable);
}
