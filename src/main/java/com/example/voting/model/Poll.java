package com.example.voting.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "polls")
public class Poll {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String question;

    @Column(nullable = false)
    private boolean open = true;

    @Column(nullable = false, unique = true, length = 12)
    private String shareCode;

    @JsonIgnore
    @Column(nullable = false, length = 64)
    private String adminTokenHash;

    @Column
    private Instant expiresAt;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PollOption> options = new ArrayList<>();

    public Poll() {}
    public Poll(String question) { this.question = question; }

    public Long getId() { return id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public String getShareCode() { return shareCode; }
    public void setShareCode(String shareCode) { this.shareCode = shareCode; }
    public String getAdminTokenHash() { return adminTokenHash; }
    public void setAdminTokenHash(String adminTokenHash) { this.adminTokenHash = adminTokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public List<PollOption> getOptions() { return options; }
    public void setOptions(List<PollOption> options) { this.options = options; }
}
