package com.example.voting.repository;
import com.example.voting.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PollRepository extends JpaRepository<Poll, Long> {}
