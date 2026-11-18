package com.innovation.service.impl;

import com.innovation.common.BusinessException;
import com.innovation.service.ProjectMaterialService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectMaterialServiceImpl implements ProjectMaterialService {

    private static final Set<String> ALLOWED_EXT = Set.of(
            "pdf", "zip", "rar", "doc", "docx", "ppt", "pptx", "7z");

    @Value("${app.upload.root:upload-data}")
    private String uploadRootProperty;

    private Path uploadRoot;

    @PostConstruct
    public void init() throws IOException {
        uploadRoot = Paths.get(uploadRootProperty).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("projects"));
        Files.createDirectories(uploadRoot.resolve("managed-project"));
    }

    @Override
    public String storeProjectFile(Long competitionId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            throw new BusinessException("文件名无效");
        }
        String ext = extension(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持扩展名：" + String.join("、", ALLOWED_EXT));
        }
        String safeBase = original.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        if (safeBase.length() > 120) {
            safeBase = safeBase.substring(safeBase.length() - 120);
        }
        String relative = "projects/" + competitionId + "/" + UUID.randomUUID() + "_" + safeBase;
        Path dest = uploadRoot.resolve(relative).normalize();
        if (!dest.startsWith(uploadRoot)) {
            throw new BusinessException("无效存储路径");
        }
        Files.createDirectories(dest.getParent());
        file.transferTo(dest);
        return relative.replace('\\', '/');
    }

    @Override
    public String storeManagedProjectFile(Long projectId, MultipartFile file) throws IOException {
        if (projectId == null) {
            throw new BusinessException("项目ID无效");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            throw new BusinessException("文件名无效");
        }
        String ext = extension(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持扩展名：" + String.join("、", ALLOWED_EXT));
        }
        String safeBase = original.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        if (safeBase.length() > 120) {
            safeBase = safeBase.substring(safeBase.length() - 120);
        }
        String relative = "managed-project/" + projectId + "/" + UUID.randomUUID() + "_" + safeBase;
        Path dest = uploadRoot.resolve(relative).normalize();
        if (!dest.startsWith(uploadRoot)) {
            throw new BusinessException("无效存储路径");
        }
        Files.createDirectories(dest.getParent());
        file.transferTo(dest);
        return relative.replace('\\', '/');
    }

    @Override
    public Path resolveStoredFile(String storedRelative) {
        if (!StringUtils.hasText(storedRelative)) {
            return null;
        }
        if (storedRelative.contains("..")) {
            throw new BusinessException("无效文件路径");
        }
        Path p = uploadRoot.resolve(storedRelative.replace('\\', '/')).normalize();
        if (!p.startsWith(uploadRoot)) {
            throw new BusinessException("无效文件路径");
        }
        return p;
    }

    @Override
    public void writeToResponse(String storedRelative, HttpServletResponse response, String downloadFileName) throws IOException {
        Path p = resolveStoredFile(storedRelative);
        if (p == null || !Files.exists(p) || !Files.isRegularFile(p)) {
            throw new BusinessException("文件不存在");
        }
        String ct = Files.probeContentType(p);
        if (ct == null) {
            ct = "application/octet-stream";
        }
        response.setContentType(ct);
        String fn = StringUtils.hasText(downloadFileName) ? downloadFileName : p.getFileName().toString();
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fn.replace("\"", "") + "\"");
        Files.copy(p, response.getOutputStream());
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
