package com.U100.fojcodesandbox.controller;

import com.U100.fojcodesandbox.CodeSandbox;
import com.U100.fojcodesandbox.JavaDockerCodeSandbox;
import com.U100.fojcodesandbox.JavaNativeCodeSandbox;
import com.U100.fojcodesandbox.model.ExecuteCodeRequest;
import com.U100.fojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 代码沙箱执行接口
 *
 * <p>与判题服务 {@code RemoteCodeSandbox} 约定：请求头 {@code auth: secretKey} 鉴权。
 */
@RestController
public class ExecuteCodeController {

    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @Resource
    private JavaDockerCodeSandbox javaDockerCodeSandbox;

    @Value("${codesandbox.type:native}")
    private String type;

    private static final String AUTH_HEADER = "auth";
    private static final String AUTH_SECRET = "secretKey";

    @PostMapping("/executeCode")
    public ResponseEntity<ExecuteCodeResponse> executeCode(
            @RequestBody ExecuteCodeRequest executeCodeRequest,
            @RequestHeader(value = AUTH_HEADER, required = false) String auth) {
        if (!AUTH_SECRET.equals(auth)) {
            ExecuteCodeResponse errorResponse = ExecuteCodeResponse.builder()
                    .status(2)
                    .message("鉴权失败")
                    .build();
            return ResponseEntity.status(403).body(errorResponse);
        }
        CodeSandbox codeSandbox = "docker".equals(type) ? javaDockerCodeSandbox : javaNativeCodeSandbox;
        ExecuteCodeResponse response = codeSandbox.executeCode(executeCodeRequest);
        return ResponseEntity.ok(response);
    }
}
