package com.example.voting.repository;

import com.example.voting.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {
    Optional<Poll> findByShareCode(String shareCode);
}
