package com.campus.ai.recommendation;

import jakarta.persistence.*;

@Entity
@Table(name = "interest_vector")
public class InterestVector {
    @Id @Column(length = 64)
    private String contentKey;
    @Lob @Column(nullable = false)
    private String vectorJson;
    protected InterestVector() {}
    public InterestVector(String contentKey, String vectorJson) { this.contentKey = contentKey; this.vectorJson = vectorJson; }
    public String getVectorJson() { return vectorJson; }
}
