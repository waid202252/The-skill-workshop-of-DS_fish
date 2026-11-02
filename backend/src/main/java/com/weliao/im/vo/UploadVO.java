package com.weliao.im.vo;

/**
 * 文件上传结果，url 为可直接访问的相对路径。
 */
public record UploadVO(String url, String name, long size) {
}
