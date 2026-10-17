package com.campus.ai.config;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "platform_model_profile")
class ModelProfile {
    @Id String purpose;
    long revision;
    Long testedRevision;
    @Lob String draftJson;
    @Lob String activeJson;
    Instant activatedAt;
    Long activatedBy;
    protected ModelProfile() {}
    ModelProfile(String purpose) { this.purpose = purpose; }
}
