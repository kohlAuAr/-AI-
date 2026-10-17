package com.campus.ai.security;

import java.util.*;
import com.campus.ai.knowledge.KnowledgeDocument;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Identity asserted only by the authenticated campus service, never by browser JSON. */
public record AiActor(long userId, String role, Set<Long> clubIds, Set<Long> managedClubIds) {
    public boolean canRead(KnowledgeDocument document) {
        return document.getVisibility().equals("PUBLIC") || document.getClubId() != null && clubIds.contains(document.getClubId());
    }
    public void requireUpload(String visibility, Long clubId) {
        boolean allowed = visibility.equals("PUBLIC") ? role.equals("PLATFORM_ADMIN") && clubId == null
                : visibility.equals("CLUB") && role.equals("MANAGER") && clubId != null && managedClubIds.contains(clubId);
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "公开资料由平台管理员维护，社团资料仅限所属负责人上传");
    }
    public String cacheScope() { return userId + ":" + role + ":" + new TreeSet<>(clubIds); }
}
