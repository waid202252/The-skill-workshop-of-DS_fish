package com.weliao.im.repository;

import com.weliao.im.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 按用户名或昵称模糊搜索，排除自己。 */
    @Query("""
            select u from User u
            where u.id <> :selfId
              and (lower(u.username) like lower(concat('%', :kw, '%'))
                or lower(u.nickname) like lower(concat('%', :kw, '%')))
            order by u.id
            """)
    List<User> search(@Param("kw") String keyword, @Param("selfId") Long selfId, Pageable pageable);
}
