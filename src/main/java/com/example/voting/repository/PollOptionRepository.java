package com.example.voting.repository;
import com.example.voting.model.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PollOptionRepository extends JpaRepository<PollOption, Long> {}
