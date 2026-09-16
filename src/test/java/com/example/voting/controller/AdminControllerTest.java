package com.example.voting.controller;

import com.example.voting.model.Poll;
import com.example.voting.service.PollService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminControllerTest {
    private final PollService service = mock(PollService.class);
    private final AdminController controller = new AdminController(service);

    @Test
    void adminLoginReturnsToken() {
        when(service.loginAdmin("dhruvm1324", "admin"))
                .thenReturn(new PollService.AuthResult("admin-token", null));

        Map<String, Object> result = controller.adminLogin(
                Map.of("username", "dhruvm1324", "password", "admin"));

        assertEquals(true, result.get("authenticated"));
        assertEquals("admin-token", result.get("token"));
    }

    @Test
    void creatorLoginReturnsTokenAndPoll() {
        Poll poll = new Poll("Question?");
        when(service.loginCreator("creator-a", "secret"))
                .thenReturn(new PollService.AuthResult("creator-token", poll));

        Map<String, Object> result = controller.creatorLogin(
                Map.of("username", "creator-a", "password", "secret"));

        assertEquals(true, result.get("authenticated"));
        assertEquals("creator-token", result.get("token"));
        assertSame(poll, result.get("poll"));
    }

    @Test
    void adminPollsDelegatesToService() {
        List<Poll> polls = List.of(new Poll("Q1"));
        when(service.getAdminPolls("token")).thenReturn(polls);

        assertSame(polls, controller.adminPolls("token"));
        verify(service).getAdminPolls("token");
    }

    @Test
    void creatorPollDelegatesToService() {
        Poll poll = new Poll("Q1");
        when(service.getCreatorPoll("creator-a", "token")).thenReturn(poll);

        assertSame(poll, controller.creatorPoll("creator-a", "token"));
        verify(service).getCreatorPoll("creator-a", "token");
    }
}
