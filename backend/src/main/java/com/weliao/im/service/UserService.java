package com.weliao.im.service;

import com.weliao.im.common.BizException;
import com.weliao.im.dto.LoginRequest;
import com.weliao.im.dto.RegisterRequest;
import com.weliao.im.dto.UpdateProfileRequest;
import com.weliao.im.entity.User;
import com.weliao.im.repository.UserRepository;
import com.weliao.im.security.JwtService;
import com.weliao.im.vo.AuthVO;
import com.weliao.im.vo.UserVO;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户相关：注册、登录、资料、搜索。
 */
@Service
public class UserService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VoMapper voMapper;
    private final com.weliao.im.ai.AiConversationService aiConversationService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, VoMapper voMapper,
                       com.weliao.im.ai.AiConversationService aiConversationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.voMapper = voMapper;
        this.aiConversationService = aiConversationService;
    }

    @Transactional
    public AuthVO register(RegisterRequest req) {
        String username = req.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw BizException.conflict("用户名已被占用");
        }
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setNickname(req.nickname().trim());
        u.setCreatedAt(LocalDateTime.now());
        u.setLastActiveAt(LocalDateTime.now());
        userRepository.save(u);

        // 新用户自动开通与 AI 伙伴的会话，让「注册完就能聊」成立。
        // 这一步失败不能影响注册主流程，所以吞掉异常只记日志。
        try {
            aiConversationService.openBotConversation(u.getId());
        } catch (Exception e) {
            log.warn("为新用户 {} 开通 AI 会话失败: {}", u.getId(), e.getMessage());
        }

        return new AuthVO(jwtService.issue(u.getId()), voMapper.toUserVO(u));
    }

    @Transactional
    public AuthVO login(LoginRequest req) {
        User u = userRepository.findByUsername(req.username().trim())
                .orElseThrow(() -> BizException.badRequest("用户名或密码不正确"));
        if (!passwordEncoder.matches(req.password(), u.getPassword())) {
            throw BizException.badRequest("用户名或密码不正确");
        }
        u.setLastActiveAt(LocalDateTime.now());
        userRepository.save(u);
        return new AuthVO(jwtService.issue(u.getId()), voMapper.toUserVO(u));
    }

    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
    }

    /**
     * 修改密码。校验原密码后再写入新哈希。
     * <p>
     * 只更新 t_user.password 一列，不动表结构；也不让用户改用户名（唯一索引 + 历史消息引用，
     * 改名成本远高于收益）。
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User u = requireUser(userId);
        if (!passwordEncoder.matches(oldPassword, u.getPassword())) {
            throw BizException.badRequest("原密码不正确");
        }
        if (oldPassword.equals(newPassword)) {
            throw BizException.badRequest("新密码不能和原密码相同");
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(u);
    }

    public UserVO me(Long userId) {
        return voMapper.toUserVO(requireUser(userId));
    }

    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileRequest req) {
        User u = requireUser(userId);
        if (StringUtils.hasText(req.nickname())) {
            u.setNickname(req.nickname().trim());
        }
        if (req.avatar() != null) {
            u.setAvatar(req.avatar().isBlank() ? null : req.avatar().trim());
        }
        if (req.signature() != null) {
            u.setSignature(req.signature().isBlank() ? null : req.signature().trim());
        }
        userRepository.save(u);
        return voMapper.toUserVO(u);
    }

    public List<UserVO> search(Long selfId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        List<User> users = userRepository.search(keyword.trim(), selfId, PageRequest.of(0, 20));
        return voMapper.toUserVOs(users);
    }
}
