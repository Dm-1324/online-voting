package com.example.voting.repository;

import com.example.voting.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findByPollIdAndVoterId(Long pollId, String voterId);
    List<Vote> findByPollIdAndOptionIdOrderByIdAsc(Long pollId, Long optionId);
}
