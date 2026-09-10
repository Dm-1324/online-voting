package com.example.voting.service;

import com.example.voting.exception.AlreadyVotedException;
import com.example.voting.exception.PollClosedException;
import com.example.voting.model.Poll;
import com.example.voting.model.PollOption;
import com.example.voting.model.Vote;
import com.example.voting.repository.PollOptionRepository;
import com.example.voting.repository.PollRepository;
import com.example.voting.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {
    @Mock private PollRepository pollRepository;
    @Mock private PollOptionRepository optionRepository;
    @Mock private VoteRepository voteRepository;
    @InjectMocks private PollService service;

    @Test void createPollWithBlankQuestionThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.createPoll("", List.of("A", "B")));
    }
    @Test void createPollWithFewerThanTwoOptionsThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.createPoll("Favorite color?", List.of("Red")));
    }
    @Test void createPollSavesSuccessfully() {
        when(pollRepository.save(any(Poll.class))).thenAnswer(inv -> inv.getArgument(0));
        Poll poll = service.createPoll("Favorite color?", List.of("Red", "Blue"));
        assertEquals("Favorite color?", poll.getQuestion());
        assertEquals(2, poll.getOptions().size());
    }
    @Test void voteOnClosedPollThrows() {
        Poll poll = new Poll("Q?"); poll.setOpen(false);
        when(pollRepository.findById(1L)).thenReturn(Optional.of(poll));
        assertThrows(PollClosedException.class, () -> service.vote(1L, "Dhruv", 1L));
    }
    @Test void voteTwiceBySameVoterThrows() {
        Poll poll = new Poll("Q?");
        when(pollRepository.findById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterName(1L, "Dhruv")).thenReturn(Optional.of(new Vote(1L, "Dhruv", 1L)));
        assertThrows(AlreadyVotedException.class, () -> service.vote(1L, "Dhruv", 1L));
    }
    @Test void voteWithInvalidOptionThrows() {
        Poll poll = new Poll("Q?"); PollOption opt = new PollOption("Red");
        opt.setId(1L); opt.setPoll(poll); poll.getOptions().add(opt);
        when(pollRepository.findById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterName(1L, "Dhruv")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.vote(1L, "Dhruv", 999L));
    }
    @Test void voteIncrementsCountCorrectly() {
        Poll poll = new Poll("Q?"); PollOption opt = new PollOption("Red");
        opt.setId(1L); opt.setPoll(poll); poll.getOptions().add(opt);
        when(pollRepository.findById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterName(1L, "Dhruv")).thenReturn(Optional.empty());
        service.vote(1L, "Dhruv", 1L);
        assertEquals(1, opt.getVoteCount());
    }
    @Test void closePollSetsOpenFalse() {
        Poll poll = new Poll("Q?");
        when(pollRepository.findById(5L)).thenReturn(Optional.of(poll));
        when(pollRepository.save(any(Poll.class))).thenAnswer(inv -> inv.getArgument(0));
        Poll closed = service.closePoll(5L);
        assertFalse(closed.isOpen());
    }
}
