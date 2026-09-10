package com.example.voting.repository;

import com.example.voting.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {
    @Query("select distinct p from Poll p left join fetch p.options")
    List<Poll> findAllWithOptions();

    @Query("select distinct p from Poll p left join fetch p.options where p.id = :id")
    Optional<Poll> findWithOptionsById(@Param("id") Long id);

    @Query("select distinct p from Poll p left join fetch p.options where p.shareCode = :shareCode")
    Optional<Poll> findWithOptionsByShareCode(@Param("shareCode") String shareCode);

    Optional<Poll> findByShareCode(String shareCode);
}
