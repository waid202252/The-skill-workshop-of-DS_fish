package com.weliao.im.repository;

import com.weliao.im.entity.CommunityGuide;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityGuideRepository extends JpaRepository<CommunityGuide, Long> {

    List<CommunityGuide> findByReviewStatusOrderByPublishedAtDesc(String reviewStatus, Pageable pageable);

    List<CommunityGuide> findByReviewStatusAndTypeOrderByPublishedAtDesc(
            String reviewStatus, String type, Pageable pageable);

    List<CommunityGuide> findByReviewStatusAndIdIn(String reviewStatus, Collection<Long> ids);

    List<CommunityGuide> findByReviewStatusOrderByHelpfulCountDescPublishedAtDesc(
            String reviewStatus, Pageable pageable);

    Optional<CommunityGuide> findByIdAndReviewStatus(Long id, String reviewStatus);

    @Query("""
            select g from CommunityGuide g
            where g.reviewStatus = :status
              and (lower(g.title) like lower(concat('%', :keyword, '%'))
                or lower(g.summary) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(g.tags, '')) like lower(concat('%', :keyword, '%')))
            order by g.helpfulCount desc, g.publishedAt desc
            """)
    List<CommunityGuide> searchPublished(@Param("keyword") String keyword,
                                         @Param("status") String status,
                                         Pageable pageable);
}
