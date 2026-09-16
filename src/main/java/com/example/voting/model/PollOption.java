package com.example.voting.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class PollOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String text;
    private int voteCount = 0;

    @ManyToOne
    @JoinColumn(name = "poll_id")
    @JsonIgnore
    private Poll poll;

    @Transient
    @JsonProperty("voterNames")
    private List<String> voterNames = new ArrayList<>();

    @Transient
    @JsonProperty("customOpinions")
    private List<String> customOpinions = new ArrayList<>();

    @Transient
    @JsonProperty("other")
    private boolean other;

    public PollOption() {}
    public PollOption(String text) { this.text = text; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }
    public Poll getPoll() { return poll; }
    public void setPoll(Poll poll) { this.poll = poll; }
    public List<String> getVoterNames() { return voterNames; }
    public void setVoterNames(List<String> voterNames) { this.voterNames = voterNames == null ? new ArrayList<>() : voterNames; }
    public List<String> getCustomOpinions() { return customOpinions; }
    public void setCustomOpinions(List<String> customOpinions) { this.customOpinions = customOpinions == null ? new ArrayList<>() : customOpinions; }
    public boolean isOther() { return other; }
    public void setOther(boolean other) { this.other = other; }
}
