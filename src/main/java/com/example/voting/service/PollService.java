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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PollService {
    @Autowired private PollRepository pollRepository;
    @Autowired private PollOptionRepository optionRepository;
    @Autowired private VoteRepository voteRepository;

    public List<Poll> getAll() { return pollRepository.findAll(); }

    public Poll getById(Long id) {
        return pollRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found: " + id));
    }

    public Poll createPoll(String question, List<String> optionTexts) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Question is required");
        if (optionTexts == null || optionTexts.size() < 2) throw new IllegalArgumentException("A poll needs at least 2 options");
        Poll poll = new Poll(question);
        for (String text : optionTexts) {
            if (text == null || text.isBlank()) throw new IllegalArgumentException("Option text is required");
            PollOption option = new PollOption(text);
            option.setPoll(poll);
            poll.getOptions().add(option);
        }
        return pollRepository.save(poll);
    }

    @Transactional
    public Poll vote(Long pollId, String voterName, Long optionId) {
        Poll poll = getById(pollId);
        if (!poll.isOpen()) throw new PollClosedException("This poll is closed");
        if (voterName == null || voterName.isBlank()) throw new IllegalArgumentException("Voter name is required");
        voteRepository.findByPollIdAndVoterName(pollId, voterName)
                .ifPresent(v -> { throw new AlreadyVotedException(voterName + " has already voted on this poll"); });
        PollOption option = poll.getOptions().stream().filter(o -> o.getId().equals(optionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid option for this poll: " + optionId));
        option.setVoteCount(option.getVoteCount() + 1);
        optionRepository.save(option);
        voteRepository.save(new Vote(pollId, voterName, optionId));
        return poll;
    }

    public Poll closePoll(Long id) {
        Poll poll = getById(id);
        poll.setOpen(false);
        return pollRepository.save(poll);
    }
}
