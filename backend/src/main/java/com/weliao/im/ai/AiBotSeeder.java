package com.weliao.im.ai;

import com.weliao.im.config.AppProperties;
import com.weliao.im.entity.User;
import com.weliao.im.entity.UserType;
import com.weliao.im.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 启动时确保 AI 账号存在。
 * <p>
 * 把 AI 做成一条普通用户记录（type=BOT），而不是特殊逻辑分支，好处是：
 * 会话、成员、消息、未读、多端同步全部复用，AI 和真人在数据层面没有区别。
 */
@Component
public class AiBotSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiBotSeeder.class);

    private final UserRepository userRepository;
    private final AppProperties props;
    private final PasswordEncoder passwordEncoder;

    public AiBotSeeder(UserRepository userRepository, AppProperties props, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Ai ai = props.getAi();
        if (!ai.isEnabled()) {
            return;
        }
        User existing = userRepository.findByUsername(ai.getBotUsername()).orElse(null);
        if (existing != null) {
            if (existing.getType() == null) {
                existing.setType(UserType.BOT);
                userRepository.save(existing);
            }
            log.info("AI 伙伴已存在: id={} nickname={}", existing.getId(), existing.getNickname());
            return;
        }

        User bot = new User();
        bot.setUsername(ai.getBotUsername());
        bot.setNickname(ai.getBotNickname());
        // 随机密码：这个账号不用于登录，只是让数据结构保持一致
        bot.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        bot.setAvatar(StringUtils.hasText(ai.getBotAvatar()) ? ai.getBotAvatar() : null);
        bot.setSignature("我是 AI 伙伴，随时可以找我聊天");
        bot.setType(UserType.BOT);
        bot.setCreatedAt(LocalDateTime.now());
        bot.setLastActiveAt(LocalDateTime.now());
        userRepository.save(bot);
        log.info("已创建 AI 伙伴账号: username={} nickname={}", ai.getBotUsername(), ai.getBotNickname());
    }
}
