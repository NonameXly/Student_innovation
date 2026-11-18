package com.innovation.service;

import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;

public interface ProjectMaterialService {

    /**
     * 保存项目材料，返回相对 uploadRoot 的路径（使用 / 分隔便于跨平台存库）
     */
    String storeProjectFile(Long competitionId, MultipartFile file) throws IOException;

    /**
     * 管理员「创新项目」模块附件，按项目 ID 分目录存储。
     */
    String storeManagedProjectFile(Long projectId, MultipartFile file) throws IOException;

    Path resolveStoredFile(String storedRelative);

    void writeToResponse(String storedRelative, HttpServletResponse response, String downloadFileName) throws IOException;
}
