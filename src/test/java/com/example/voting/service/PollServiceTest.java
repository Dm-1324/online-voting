package com.example.voting.service;

import com.example.voting.exception.AlreadyVotedException;
import com.example.voting.exception.PollClosedException;
import com.example.voting.model.Poll;
import com.example.voting.model.PollOption;
import com.example.voting.model.Vote;
import com.example.voting.repository.PollOptionRepository;
import com.example.voting.repository.PollRepository;
import com.example.voting.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {
    @Mock private PollRepository pollRepository;
    @Mock private PollOptionRepository optionRepository;
    @Mock private VoteRepository voteRepository;
    @InjectMocks private PollService service;

    private static final String ADMIN_USER = "dhruvm1324";
    private static final String ADMIN_PASS = "admin";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "mainAdminUsername", ADMIN_USER);
        ReflectionTestUtils.setField(service, "mainAdminPassword", ADMIN_PASS);
        lenient().when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(anyLong(), anyLong()))
                .thenReturn(List.of());
    }

    @Test void createPollWithBlankQuestionThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.createPoll("", List.of("A", "B")));
    }

    @Test void createPollWithTooFewOptionsThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.createPoll("Q?", List.of("A")));
    }

    @Test void createPollWithTooManyOptionsThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.createPoll("Q?", List.of("1","2","3","4","5","6","7","8","9","10","11")));
    }

    @Test void createPollWithLongQuestionThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.createPoll("x".repeat(201), List.of("A", "B")));
    }

    @Test void createPollWithLongOptionThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.createPoll("Q?", List.of("A", "x".repeat(101))));
    }

    @Test void createPollWithNullOptionThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.createPoll("Q?", Arrays.asList("A", null)));
    }

    @Test void createPollSavesSuccessfully() {
        when(pollRepository.findByShareCode(anyString())).thenReturn(Optional.empty());
        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PollService.PollCreation creation = service.createPollWithAdminToken(" Favorite? ", List.of(" Red ", " Blue "));

        assertEquals("Favorite?", creation.poll().getQuestion());
        assertEquals(List.of("Red", "Blue"), creation.poll().getOptions().stream().map(PollOption::getText).toList());
        assertEquals(8, creation.poll().getShareCode().length());
        assertNotNull(creation.adminToken());
        assertNotNull(creation.creatorUsername());
        assertNotNull(creation.creatorPassword());
        assertNotNull(creation.poll().getCreatorPasswordHash());
    }

    @Test void getAllAddsOtherOptionAndVoterNames() {
        Poll poll = buildPollWithTwoOptions();
        Vote vote = new Vote(1L, "device-1", "Dhruv", 1L);
        when(pollRepository.findAllWithOptions()).thenReturn(List.of(poll));
        when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(vote));
        when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(1L, 2L)).thenReturn(List.of());
        when(voteRepository.findByPollIdAndOptionIdOrderByIdAsc(1L, -1L)).thenReturn(List.of());

        Poll result = service.getAll().get(0);

        assertEquals(3, result.getOptions().size());
        assertEquals(List.of("Dhruv"), result.getOptions().get(0).getVoterNames());
        assertTrue(result.getOptions().get(2).isOther());
    }

    @Test void getByIdNotFoundThrows() {
        when(pollRepository.findWithOptionsById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getById(99L));
    }

    @Test void getByShareCodeRejectsBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> service.getByShareCode("  "));
    }

    @Test void getByShareCodeReturnsPoll() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsByShareCode("ABC12345")).thenReturn(Optional.of(poll));
        assertSame(poll, service.getByShareCode(" abc12345 "));
    }

    @Test void voteOnClosedPollThrows() {
        Poll poll = buildPollWithTwoOptions();
        poll.setOpen(false);
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        assertThrows(PollClosedException.class, () -> service.vote(1L, "Dhruv", "device-1", 1L));
    }

    @Test void voteOnExpiredPollClosesPoll() {
        Poll poll = buildPollWithTwoOptions();
        poll.setExpiresAt(Instant.now().minusSeconds(60));
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThrows(PollClosedException.class, () -> service.vote(1L, "Dhruv", "device-1", 1L));
        assertFalse(poll.isOpen());
    }

    @Test void voteRejectsBlankName() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        assertThrows(IllegalArgumentException.class, () -> service.vote(1L, " ", "device-1", 1L));
    }

    @Test void voteRejectsInvalidVoterId() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        assertThrows(IllegalArgumentException.class, () -> service.vote(1L, "Dhruv", "", 1L));
    }

    @Test void voteRejectsLongVoterId() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        assertThrows(IllegalArgumentException.class, () -> service.vote(1L, "Dhruv", "x".repeat(65), 1L));
    }

    @Test void voteRejectsMissingOption() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        assertThrows(IllegalArgumentException.class, () -> service.vote(1L, "Dhruv", "device-1", null));
    }

    @Test void voteTwiceBySameVoterThrows() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterId(1L, "device-1")).thenReturn(Optional.of(new Vote(1L, "device-1", "Dhruv", 1L)));
        assertThrows(AlreadyVotedException.class, () -> service.vote(1L, "Dhruv", "device-1", 1L));
    }

    @Test void voteWithInvalidOptionThrows() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterId(1L, "device-1")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.vote(1L, "Dhruv", "device-1", 999L));
    }

    @Test void voteIncrementsCountCorrectly() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterId(1L, "device-1")).thenReturn(Optional.empty());
        service.vote(1L, "Dhruv", "device-1", 1L);
        assertEquals(1, poll.getOptions().get(0).getVoteCount());
        verify(optionRepository).save(poll.getOptions().get(0));
    }

    @Test void voteMapsDatabaseDuplicateToAlreadyVoted() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterId(1L, "device-1")).thenReturn(Optional.empty());
        when(voteRepository.saveAndFlush(any(Vote.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThrows(AlreadyVotedException.class, () -> service.vote(1L, "Dhruv", "device-1", 1L));
    }

    @Test void voteWithOtherOptionAndCustomTextSucceeds() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterId(1L, "device-1")).thenReturn(Optional.empty());
        service.vote(1L, "Dhruv", "device-1", -1L, " My own answer ");
        verify(voteRepository).saveAndFlush(argThat(v -> "My own answer".equals(v.getCustomText())));
    }

    @Test void voteWithOtherOptionBlankTextThrows() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterId(1L, "device-1")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.vote(1L, "Dhruv", "device-1", -1L, " "));
    }

    @Test void voteWithOtherOptionLongTextThrows() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterId(1L, "device-1")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.vote(1L, "Dhruv", "device-1", -1L, "x".repeat(201)));
    }

    @Test void legacyVoteOverloadUsesDerivedVoterId() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.findByPollIdAndVoterId(anyLong(), anyString())).thenReturn(Optional.empty());
        service.vote(1L, "Dhruv", 1L);
        verify(voteRepository).saveAndFlush(any(Vote.class));
    }

    @Test void loginAdminWithCorrectCredentialsReturnsToken() {
        PollService.AuthResult result = service.loginAdmin(ADMIN_USER, ADMIN_PASS);
        assertNotNull(result.token());
        assertNull(result.poll());
    }

    @Test void loginAdminWithWrongCredentialsThrowsUnauthorized() {
        assertThrows(ResponseStatusException.class, () -> service.loginAdmin(ADMIN_USER, "wrong"));
        assertThrows(ResponseStatusException.class, () -> service.loginAdmin("wrong", ADMIN_PASS));
    }

    @Test void loginCreatorWithCorrectCredentialsReturnsPoll() {
        PollService.PollCreation creation = createStoredPoll();
        when(pollRepository.findByCreatorUsername(creation.creatorUsername())).thenReturn(Optional.of(creation.poll()));
        PollService.AuthResult result = service.loginCreator(creation.creatorUsername(), creation.creatorPassword());
        assertNotNull(result.token());
        assertSame(creation.poll(), result.poll());
    }

    @Test void loginCreatorWithWrongUsernameThrowsUnauthorized() {
        when(pollRepository.findByCreatorUsername("nobody")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.loginCreator("nobody", "whatever"));
    }

    @Test void loginCreatorWithWrongPasswordThrowsUnauthorized() {
        PollService.PollCreation creation = createStoredPoll();
        when(pollRepository.findByCreatorUsername(creation.creatorUsername())).thenReturn(Optional.of(creation.poll()));
        assertThrows(ResponseStatusException.class, () -> service.loginCreator(creation.creatorUsername(), "wrong"));
    }

    @Test void getAdminPollsWithValidTokenReturnsAllPolls() {
        String token = service.loginAdmin(ADMIN_USER, ADMIN_PASS).token();
        when(pollRepository.findAllWithOptions()).thenReturn(List.of(buildPollWithTwoOptions()));
        assertEquals(1, service.getAdminPolls(token).size());
    }

    @Test void getAdminPollsWithInvalidTokenThrowsUnauthorized() {
        assertThrows(ResponseStatusException.class, () -> service.getAdminPolls("bogus"));
    }

    @Test void getCreatorPollWithValidTokenReturnsPoll() {
        PollService.PollCreation creation = createStoredPoll();
        when(pollRepository.findByCreatorUsername(creation.creatorUsername())).thenReturn(Optional.of(creation.poll()));
        String token = service.loginCreator(creation.creatorUsername(), creation.creatorPassword()).token();
        assertSame(creation.poll(), service.getCreatorPoll(creation.creatorUsername(), token));
    }

    @Test void getCreatorPollWithWrongTokenThrowsUnauthorized() {
        PollService.PollCreation creation = createStoredPoll();
        when(pollRepository.findByCreatorUsername(creation.creatorUsername())).thenReturn(Optional.of(creation.poll()));
        assertThrows(ResponseStatusException.class, () -> service.getCreatorPoll(creation.creatorUsername(), "forged"));
    }

    @Test void updatePollAsMainAdminSucceeds() {
        Poll poll = buildPollWithTwoOptions();
        String token = service.loginAdmin(ADMIN_USER, ADMIN_PASS).token();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.countByPollId(1L)).thenReturn(0L);
        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Poll updated = service.updatePoll(1L, "Updated?", List.of("X", "Y"), token, null, null);
        assertEquals("Updated?", updated.getQuestion());
        assertEquals("X", updated.getOptions().get(0).getText());
    }

    @Test void updatePollAddsNewOptionWhenThereAreNoVotes() {
        Poll poll = buildPollWithTwoOptions();
        String token = service.loginAdmin(ADMIN_USER, ADMIN_PASS).token();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.countByPollId(1L)).thenReturn(0L);
        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Poll updated = service.updatePoll(1L, "Updated?", List.of("X", "Y", "Z"), token, null, null);
        assertEquals(3, updated.getOptions().size());
    }

    @Test void updatePollAfterVotesRejectsDifferentOptionCount() {
        Poll poll = buildPollWithTwoOptions();
        String token = service.loginAdmin(ADMIN_USER, ADMIN_PASS).token();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        when(voteRepository.countByPollId(1L)).thenReturn(5L);
        assertThrows(IllegalArgumentException.class, () -> service.updatePoll(1L, "Updated?", List.of("Only one"), token, null, null));
    }

    @Test void updatePollWithWrongCreatorTokenThrowsUnauthorized() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        assertThrows(ResponseStatusException.class, () -> service.updatePoll(1L, "Updated?", List.of("X", "Y"), null, "someone", "wrong"));
    }

    @Test void deletePollAsAdminDeletesIt() {
        String token = service.loginAdmin(ADMIN_USER, ADMIN_PASS).token();
        when(pollRepository.existsById(1L)).thenReturn(true);
        service.deletePoll(1L, token);
        verify(pollRepository).deleteById(1L);
    }

    @Test void deletePollRejectsNonAdmin() {
        assertThrows(ResponseStatusException.class, () -> service.deletePoll(1L, "not-admin"));
    }

    @Test void deletePollNotFoundThrows() {
        String token = service.loginAdmin(ADMIN_USER, ADMIN_PASS).token();
        when(pollRepository.existsById(99L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.deletePoll(99L, token));
    }

    @Test void closePollWithCreatorTokenSucceeds() {
        PollService.PollCreation creation = createStoredPoll();
        Poll poll = creation.poll();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        Poll result = service.closePoll(1L, service.loginCreatorTokenForTest(creation));
        assertFalse(result.isOpen());
    }

    @Test void closePollWithInvalidTokenThrows() {
        Poll poll = buildPollWithTwoOptions();
        when(pollRepository.findWithOptionsById(1L)).thenReturn(Optional.of(poll));
        assertThrows(ResponseStatusException.class, () -> service.closePoll(1L, "wrong"));
    }

    private PollService.PollCreation createStoredPoll() {
        when(pollRepository.findByShareCode(anyString())).thenReturn(Optional.empty());
        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> {
            Poll poll = invocation.getArgument(0);
            if (poll.getId() == null) {
                poll.setId(1L);
            }
            return poll;
        });
        return service.createPollWithAdminToken("Q?", List.of("A", "B"));
    }

    private Poll buildPollWithTwoOptions() {
        Poll poll = new Poll("Q?");
        poll.setId(1L);
        PollOption first = new PollOption("A");
        first.setId(1L);
        first.setPoll(poll);
        PollOption second = new PollOption("B");
        second.setId(2L);
        second.setPoll(poll);
        poll.getOptions().add(first);
        poll.getOptions().add(second);
        poll.setShareCode("ABC12345");
        poll.setAdminTokenHash("hash");
        return poll;
    }
}
