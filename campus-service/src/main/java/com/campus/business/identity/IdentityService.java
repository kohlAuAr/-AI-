package com.campus.business.identity;

import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityService {
    private final AccountRepository accounts;
    public IdentityService(AccountRepository accounts) { this.accounts = accounts; }
    public Account current(Principal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return accounts.findByUsername(principal.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不可用"));
    }
}
