package com.weliao.im.controller;

import com.weliao.im.common.R;
import com.weliao.im.service.FileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public R upload(@RequestParam("file") MultipartFile file) {
        return R.ok(fileService.upload(file));
    }
}
