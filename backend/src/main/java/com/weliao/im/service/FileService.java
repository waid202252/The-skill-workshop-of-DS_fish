package com.weliao.im.service;

import com.weliao.im.common.BizException;
import com.weliao.im.config.AppProperties;
import com.weliao.im.vo.UploadVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储。按 年/月 分目录，文件名用 UUID 防止覆盖与路径穿越。
 */
@Service
public class FileService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXT = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "bmp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "csv", "zip", "rar", "7z",
            "mp3", "wav", "mp4", "mov"
    ));

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path baseDir;
    private final String urlPrefix;

    public FileService(AppProperties props) {
        this.baseDir = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        this.urlPrefix = props.getUploadUrlPrefix();
    }

    public UploadVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.badRequest("文件超过 20MB 限制");
        }
        String original = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "未命名";
        String ext = extensionOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.badRequest("不支持的文件类型：" + (ext.isEmpty() ? "未知" : ext));
        }

        String ym = LocalDate.now().format(YM);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = baseDir.resolve(ym);
        Path target = dir.resolve(storedName);

        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(50000, "文件保存失败，请稍后重试",
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new UploadVO(urlPrefix + "/" + ym + "/" + storedName, original, file.getSize());
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
