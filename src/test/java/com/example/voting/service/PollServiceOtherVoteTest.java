package com.example.voting.service;

import com.example.voting.model.Poll;
import com.example.voting.model.PollOption;
import com.example.voting.model.Vote;
import com.example.voting.repository.PollOptionRepository;
import com.example.voting.repository.PollRepository;
import com.example.voting.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollServiceOtherVoteTest {
    private static final Long POLL_ID = 1L;
    private static final Long OPTION_ID = 10L;
    private static final Long OTHER_OPTION_ID = -1L;

    @Mock
    private PollRepository pollRepository;

    @Mock
    private PollOptionRepository optionRepository;

    @Mock
    private VoteRepository voteRepository;

    private PollService service;

    @BeforeEach
    void setUp() {
        service = new PollService(pollRepository, optionRepository, voteRepository);
        ReflectionTestUtils.setField(service, "mainAdminUsername", "dhruvm1324");
        ReflectionTestUtils.setField(service, "mainAdminPassword", "admin");
        lenient().when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(anyLong(), anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void otherVoteStoresTrimmedCustomText() {
        Poll poll = buildPoll();
        when(pollRepository.findWithOptionsById(POLL_ID)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(POLL_ID, OPTION_ID)).thenReturn(List.of());
        when(voteRepository.findByPollIdAndVoterId(POLL_ID, "device-1")).thenReturn(Optional.empty());

        service.vote(POLL_ID, "Dhruv", "device-1", OTHER_OPTION_ID, "  Go to Goa  ");

        ArgumentCaptor<Vote> captor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).saveAndFlush(captor.capture());
        assertEquals("Go to Goa", captor.getValue().getCustomText());
        assertEquals(OTHER_OPTION_ID, captor.getValue().getOptionId());
        verify(optionRepository, never()).save(any(PollOption.class));
    }

    @Test
    void otherVoteRejectsBlankCustomText() {
        Poll poll = buildPoll();
        when(pollRepository.findWithOptionsById(POLL_ID)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(POLL_ID, OPTION_ID)).thenReturn(List.of());
        when(voteRepository.findByPollIdAndVoterId(POLL_ID, "device-1")).thenReturn(Optional.empty());
        String customText = "   ";

        assertThrows(IllegalArgumentException.class,
                () -> service.vote(POLL_ID, "Dhruv", "device-1", OTHER_OPTION_ID, customText));

        verify(voteRepository, never()).saveAndFlush(any(Vote.class));
    }

    @Test
    void otherVoteRejectsCustomTextLongerThanTwoHundredCharacters() {
        Poll poll = buildPoll();
        when(pollRepository.findWithOptionsById(POLL_ID)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(POLL_ID, OPTION_ID)).thenReturn(List.of());
        when(voteRepository.findByPollIdAndVoterId(POLL_ID, "device-1")).thenReturn(Optional.empty());
        String customText = "x".repeat(201);

        assertThrows(IllegalArgumentException.class,
                () -> service.vote(POLL_ID, "Dhruv", "device-1", OTHER_OPTION_ID, customText));

        verify(voteRepository, never()).saveAndFlush(any(Vote.class));
    }

    @Test
    void getByIdBuildsOtherResultFromCustomVotes() {
        Poll poll = buildPoll();
        Vote otherVote = new Vote(POLL_ID, "device-1", "Dhruv", OTHER_OPTION_ID, "Go to Goa");
        when(pollRepository.findWithOptionsById(POLL_ID)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(POLL_ID, OPTION_ID)).thenReturn(List.of());
        when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(POLL_ID, OTHER_OPTION_ID)).thenReturn(List.of(otherVote));

        Poll result = service.getById(POLL_ID);

        PollOption other = result.getOptions().get(result.getOptions().size() - 1);
        assertEquals(OTHER_OPTION_ID, other.getId());
        assertEquals(1, other.getVoteCount());
        assertEquals(List.of("Dhruv"), other.getVoterNames());
        assertEquals(List.of("Go to Goa"), other.getCustomOpinions());
        assertEquals(true, other.isOther());
    }

    private Poll buildPoll() {
        Poll poll = new Poll("What should we do?");
        ReflectionTestUtils.setField(poll, "id", POLL_ID);
        PollOption option = new PollOption("Yes");
        ReflectionTestUtils.setField(option, "id", OPTION_ID);
        option.setPoll(poll);
        poll.getOptions().add(option);
        PollOption secondOption = new PollOption("No");
        ReflectionTestUtils.setField(secondOption, "id", 11L);
        secondOption.setPoll(poll);
        poll.getOptions().add(secondOption);
        return poll;
    }
}
