package com.campus.business.identity;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

final class AccountPrincipal extends User {
    private final long credentialVersion;

    AccountPrincipal(Account account) {
        super(account.getUsername(), account.getPasswordHash(), AuthorityUtils.createAuthorityList("ROLE_" + account.getRole()));
        this.credentialVersion = account.getCredentialVersion();
    }

    long credentialVersion() { return credentialVersion; }
}
