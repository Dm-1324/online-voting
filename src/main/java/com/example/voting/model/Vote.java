package com.example.voting.model;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"pollId", "voterName"}))
public class Vote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pollId;
    private String voterName;
    private Long optionId;

    public Vote() {}
    public Vote(Long pollId, String voterName, Long optionId) {
        this.pollId = pollId;
        this.voterName = voterName;
        this.optionId = optionId;
    }

    public Long getId() { return id; }
    public Long getPollId() { return pollId; }
    public void setPollId(Long pollId) { this.pollId = pollId; }
    public String getVoterName() { return voterName; }
    public void setVoterName(String voterName) { this.voterName = voterName; }
    public Long getOptionId() { return optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }
}
