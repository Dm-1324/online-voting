package com.example.voting.controller;

import com.example.voting.model.Poll;
import com.example.voting.service.PollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AdminController {
    @Autowired private PollService service;

    @PostMapping("/admin/login")
    public Map<String,Object> adminLogin(@RequestBody Map<String,String> body) {
        return Map.of("authenticated", true, "token", service.loginAdmin(body.get("username"), body.get("password")).token());
    }

    @PostMapping("/creator/login")
    public Map<String,Object> creatorLogin(@RequestBody Map<String,String> body) {
        PollService.AuthResult result = service.loginCreator(body.get("username"), body.get("password"));
        return Map.of("authenticated", true, "token", result.token(), "poll", result.poll());
    }

    @GetMapping("/admin/polls")
    public List<Poll> adminPolls(@RequestHeader(value="X-Admin-Token", required=false) String token) { return service.getAdminPolls(token); }

    @GetMapping("/creator/poll")
    public Poll creatorPoll(@RequestHeader(value="X-Creator-Username", required=false) String username, @RequestHeader(value="X-Creator-Token", required=false) String token) { return service.getCreatorPoll(username, token); }
}
