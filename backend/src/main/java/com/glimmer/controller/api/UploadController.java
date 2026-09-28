package com.glimmer.controller.api;

import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.service.OssService;
import com.glimmer.service.dto.OssSignatureVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件上传接口（OSS 直传签名）
 */
@Tag(name = "文件上传", description = "OSS 直传签名")
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final OssService ossService;

    public UploadController(OssService ossService) {
        this.ossService = ossService;
    }

    @Operation(summary = "获取 OSS 直传签名（需登录）")
    @GetMapping("/signature")
    public Result<OssSignatureVO> getSignature(@RequestParam String scene) {
        // 登录校验（拦截器已保证登录，这里仅取 userId 用于日志）
        SecurityUtils.getCurrentUserId();
        // scene 白名单：avatar / article / campfire
        if (!"avatar".equals(scene) && !"article".equals(scene) && !"campfire".equals(scene)) {
            scene = "common";
        }
        OssSignatureVO vo = ossService.generatePostSignature(scene);
        return Result.success(vo);
    }
}
