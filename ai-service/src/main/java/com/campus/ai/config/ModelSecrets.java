package com.campus.ai.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
class ModelSecrets {
    private final byte[] key;
    ModelSecrets(@Value("${ai-admin.master-key:}") String value) {
        try { key = value.isBlank() ? null : Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException error) { throw new IllegalStateException("AI_CONFIG_MASTER_KEY 必须为 Base64 编码的 32 字节密钥"); }
        if (key != null && key.length != 32) throw new IllegalStateException("AI_CONFIG_MASTER_KEY 必须为 Base64 编码的 32 字节密钥");
    }
    boolean configured() { return key != null; }
    String encrypt(String secret) {
        if (secret == null || secret.isBlank()) return "";
        if (key == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请部署人员先配置 AI_CONFIG_MASTER_KEY，不能明文保存 API Key");
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("模型密钥加密失败"); }
    }
    String decrypt(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            String[] parts = value.split("\\."); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception error) { throw new IllegalStateException("无法解密已保存的模型密钥，请恢复原 AI_CONFIG_MASTER_KEY"); }
    }
}
