package com.campus.business.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    private final ObjectMapper mapper;

    public ApiErrors(ObjectMapper mapper) { this.mapper = mapper; }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail expectedError(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason() == null ? "请求失败" : error.getReason());
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail unavailable(ResourceAccessException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂不可用，请检查 8091 端口的服务；社团和活动查询仍可使用。");
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail upstream(RestClientResponseException error) {
        String detail = "AI 服务请求失败";
        try { detail = mapper.readTree(error.getResponseBodyAsByteArray()).path("detail").asText(detail); }
        catch (java.io.IOException ignored) { /* Upstream may return a non-JSON error. */ }
        return ProblemDetail.forStatusAndDetail(error.getStatusCode(), detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求参数格式不正确");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "资料文件不能超过 128KB");
    }
}
