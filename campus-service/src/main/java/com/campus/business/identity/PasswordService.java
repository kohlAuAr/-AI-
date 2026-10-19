package com.campus.business.identity;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    public PasswordService(AccountRepository accounts, PasswordEncoder passwords) { this.accounts = accounts; this.passwords = passwords; }

    @Transactional
    public void change(Principal principal, String currentPassword, String newPassword) {
        Account account = accounts.findForPasswordChange(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录"));
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72 || !passwords.matches(currentPassword, account.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码 UTF-8 编码不能超过 72 字节");
        if (passwords.matches(newPassword, account.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        account.changePassword(passwords.encode(newPassword));
    }
}
