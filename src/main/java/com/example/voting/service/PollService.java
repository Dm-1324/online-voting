package com.example.voting.service;

import com.example.voting.exception.AlreadyVotedException;
import com.example.voting.exception.PollClosedException;
import com.example.voting.model.Poll;
import com.example.voting.model.PollOption;
import com.example.voting.model.Vote;
import com.example.voting.repository.PollOptionRepository;
import com.example.voting.repository.PollRepository;
import com.example.voting.repository.VoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PollService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired private PollRepository pollRepository;
    @Autowired private PollOptionRepository optionRepository;
    @Autowired private VoteRepository voteRepository;

    public List<Poll> getAll() { return pollRepository.findAll(); }

    public Poll getById(Long id) {
        return pollRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Poll not found: " + id));
    }

    public Poll getByShareCode(String shareCode) {
        if (shareCode == null || shareCode.isBlank()) throw new IllegalArgumentException("Poll link is invalid");
        return pollRepository.findByShareCode(shareCode.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
    }

    @Transactional
    public PollCreation createPollWithAdminToken(String question, List<String> optionTexts) {
        validatePoll(question, optionTexts);
        Poll poll = new Poll(question.trim());
        poll.setShareCode(generateShareCode());
        String adminToken = UUID.randomUUID().toString().replace("-", "");
        poll.setAdminTokenHash(sha256(adminToken));
        for (String text : optionTexts) {
            PollOption option = new PollOption(text.trim());
            option.setPoll(poll);
            poll.getOptions().add(option);
        }
        return new PollCreation(pollRepository.save(poll), adminToken);
    }

    public Poll createPoll(String question, List<String> optionTexts) {
        return createPollWithAdminToken(question, optionTexts).poll();
    }

    @Transactional
    public Poll vote(Long pollId, String voterName, Long optionId) {
        String fallbackVoterId = voterName == null ? null : sha256(voterName.trim().toLowerCase());
        return vote(pollId, voterName, fallbackVoterId, optionId);
    }

    @Transactional
    public Poll vote(Long pollId, String voterName, String voterId, Long optionId) {
        Poll poll = getById(pollId);
        if (!poll.isOpen()) throw new PollClosedException("This poll is closed");
        if (poll.getExpiresAt() != null && poll.getExpiresAt().isBefore(Instant.now())) {
            poll.setOpen(false);
            pollRepository.save(poll);
            throw new PollClosedException("This poll has expired");
        }
        if (voterName == null || voterName.isBlank()) throw new IllegalArgumentException("Voter name is required");
        if (voterId == null || voterId.isBlank() || voterId.length() > 64) throw new IllegalArgumentException("Voter identity is invalid");
        if (optionId == null) throw new IllegalArgumentException("An option is required");
        if (voteRepository.findByPollIdAndVoterId(pollId, voterId).isPresent()) {
            throw new AlreadyVotedException("You have already voted on this poll");
        }

        PollOption option = poll.getOptions().stream()
                .filter(o -> o.getId() != null && o.getId().equals(optionId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid option for this poll"));
        option.setVoteCount(option.getVoteCount() + 1);
        optionRepository.save(option);
        try {
            voteRepository.saveAndFlush(new Vote(pollId, voterId, voterName.trim(), optionId));
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyVotedException("You have already voted on this poll");
        }
        return poll;
    }

    @Transactional
    public Poll closePoll(Long id, String adminToken) {
        Poll poll = getById(id);
        requireAdmin(poll, adminToken);
        poll.setOpen(false);
        return pollRepository.save(poll);
    }

    // Internal/service-test helper. Public HTTP traffic always uses the token-protected overload.
    public Poll closePoll(Long id) {
        Poll poll = getById(id);
        poll.setOpen(false);
        return pollRepository.save(poll);
    }

    private void requireAdmin(Poll poll, String adminToken) {
        if (adminToken == null || !sha256(adminToken).equals(poll.getAdminTokenHash())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Only the poll creator can close this poll");
        }
    }

    private void validatePoll(String question, List<String> optionTexts) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Question is required");
        if (question.trim().length() > 200) throw new IllegalArgumentException("Question must be 200 characters or fewer");
        if (optionTexts == null || optionTexts.size() < 2 || optionTexts.size() > 10) throw new IllegalArgumentException("A poll needs between 2 and 10 options");
        for (String text : optionTexts) {
            if (text == null || text.isBlank()) throw new IllegalArgumentException("Option text is required");
            if (text.trim().length() > 100) throw new IllegalArgumentException("Options must be 100 characters or fewer");
        }
    }

    private String generateShareCode() {
        String code;
        do {
            StringBuilder value = new StringBuilder(8);
            for (int i = 0; i < 8; i++) value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            code = value.toString();
        } while (pollRepository.findByShareCode(code).isPresent());
        return code;
    }

    private String sha256(String value) {
        if (value == null) return "";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }

    public record PollCreation(Poll poll, String adminToken) {}
}
