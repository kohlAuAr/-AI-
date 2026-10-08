package com.campus.ai.config;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail expectedError(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason() == null ? "请求失败" : error.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "问题不能为空且最多 2000 字符，会话编号必须为 UUID");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidArgument() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求参数格式不正确");
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "模型接口暂不可用，请检查模型服务地址与网络");
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail modelError(RestClientResponseException error) {
        // Do not expose provider response bodies, credentials or authorization headers.
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "模型接口返回 HTTP " + error.getStatusCode().value() + "，请检查模型配置与额度");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "资料文件不能超过 128KB");
    }
}
