package com.example.voting.controller;

import com.example.voting.model.Poll;
import com.example.voting.service.PollService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AdminController {
    private final PollService service;

    public AdminController(PollService service) {
        this.service = service;
    }

    @PostMapping("/admin/login")
    public Map<String, Object> adminLogin(@RequestBody Map<String, String> body) {
        PollService.AuthResult result = service.loginAdmin(body.get("username"), body.get("password"));
        return Map.of("authenticated", true, "token", result.token());
    }

    @PostMapping("/creator/login")
    public Map<String, Object> creatorLogin(@RequestBody Map<String, String> body) {
        PollService.AuthResult result = service.loginCreator(body.get("username"), body.get("password"));
        return Map.of("authenticated", true, "token", result.token(), "poll", result.poll());
    }

    @GetMapping("/admin/polls")
    public List<Poll> adminPolls(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return service.getAdminPolls(token);
    }

    @GetMapping("/creator/poll")
    public Poll creatorPoll(
            @RequestHeader(value = "X-Creator-Username", required = false) String username,
            @RequestHeader(value = "X-Creator-Token", required = false) String token) {
        return service.getCreatorPoll(username, token);
    }
}
