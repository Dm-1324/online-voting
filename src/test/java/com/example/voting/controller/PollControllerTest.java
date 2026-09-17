package com.example.voting.controller;

import com.example.voting.model.Poll;
import com.example.voting.service.PollService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PollControllerTest {
    private final PollService service = mock(PollService.class);
    private final PollController controller = new PollController(service);

    @Test
    void getAllDelegates() {
        List<Poll> polls = List.of(new Poll("Q1"));
        when(service.getAll()).thenReturn(polls);
        assertSame(polls, controller.getAll());
    }

    @Test
    void getOneDelegates() {
        Poll poll = new Poll("Q1");
        when(service.getById(1L)).thenReturn(poll);
        assertSame(poll, controller.getOne(1L));
    }

    @Test
    void getPublicDelegates() {
        Poll poll = new Poll("Q1");
        when(service.getByShareCode("ABC12345")).thenReturn(poll);
        assertSame(poll, controller.getPublic("ABC12345"));
    }

    @Test
    void createReturnsCreatorCredentials() {
        Poll poll = new Poll("Q1");
        poll.setShareCode("ABC12345");
        PollService.PollCreation creation =
                new PollService.PollCreation(poll, "admin-token", "creator-a", "secret");
        when(service.createPollWithAdminToken("Q1", List.of("A", "B"))).thenReturn(creation);

        Map<String, Object> result = controller.create(
                Map.of("question", "Q1", "options", List.of("A", "B")));

        assertSame(poll, result.get("poll"));
        assertEquals("admin-token", result.get("adminToken"));
        assertEquals("/p/ABC12345", result.get("shareUrl"));
        assertEquals("creator-a", result.get("creatorUsername"));
        assertEquals("secret", result.get("creatorPassword"));
    }

    @Test
    void createAcceptsMissingQuestion() {
        when(service.createPollWithAdminToken(null, List.of("A", "B")))
                .thenThrow(new IllegalArgumentException("Question is required"));
        Map<String, Object> body = Map.of("options", List.of("A", "B"));
        assertThrows(IllegalArgumentException.class, () -> controller.create(body));
    }

    @Test
    void votePassesCustomText() {
        Poll poll = new Poll("Q1");
        when(service.vote(1L, "Dhruv", "device-1", -1L, "Custom"))
                .thenReturn(poll);

        Poll result = controller.vote(1L, "device-1",
                Map.of("voterName", "Dhruv", "optionId", -1L, "customText", "Custom"));

        assertSame(poll, result);
        verify(service).vote(1L, "Dhruv", "device-1", -1L, "Custom");
    }

    @Test
    void voteRejectsMissingOption() {
        Map<String, Object> body = Map.of("voterName", "Dhruv");
        assertThrows(IllegalArgumentException.class,
                () -> controller.vote(1L, "device-1", body));
    }

    @Test
    void voteRejectsInvalidOption() {
        Map<String, Object> body = Map.of("voterName", "Dhruv", "optionId", "abc");
        assertThrows(IllegalArgumentException.class,
                () -> controller.vote(1L, "device-1", body));
    }

    @Test
    void closeDelegates() {
        Poll poll = new Poll("Q1");
        when(service.closePoll(1L, "token")).thenReturn(poll);
        assertSame(poll, controller.close(1L, "token"));
    }

    @Test
    void updateDelegatesWithCreatorHeaders() {
        Poll poll = new Poll("Q1");
        when(service.updatePoll(1L, "New", List.of("X", "Y"),
                null, "creator-a", "creator-token")).thenReturn(poll);

        assertSame(poll, controller.update(1L, null, "creator-a", "creator-token",
                Map.of("question", "New", "options", List.of("X", "Y"))));
    }

    @Test
    void deleteDelegates() {
        controller.delete(4L, "admin-token");
        verify(service).deletePoll(4L, "admin-token");
    }
}