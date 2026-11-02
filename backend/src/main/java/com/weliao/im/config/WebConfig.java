package com.weliao.im.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 把本地上传目录映射成 /uploads/** 静态资源。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        // 目录必须先存在：目录不存在时 Path.toUri() 不会补结尾斜杠，
        // Spring 会把该位置当成「文件」而不是「目录」，导致所有文件 404。
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + dir, e);
        }
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        String prefix = props.getUploadUrlPrefix();
        log.info("静态资源映射: {}/** -> {}", prefix, location);
        registry.addResourceHandler(prefix + "/**")
                .addResourceLocations(location);
    }
}
