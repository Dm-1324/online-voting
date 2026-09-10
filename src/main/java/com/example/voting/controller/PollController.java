package com.example.voting.controller;

import com.example.voting.model.Poll;
import com.example.voting.service.PollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/polls")
@CrossOrigin
public class PollController {
    @Autowired private PollService service;
    @GetMapping public List<Poll> getAll() { return service.getAll(); }
    @GetMapping("/{id}") public Poll getOne(@PathVariable Long id) { return service.getById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Poll create(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        @SuppressWarnings("unchecked") List<String> options = (List<String>) body.get("options");
        return service.createPoll(question, options);
    }
    @PostMapping("/{id}/vote")
    public Poll vote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String voterName = (String) body.get("voterName");
        Long optionId = Long.valueOf(body.get("optionId").toString());
        return service.vote(id, voterName, optionId);
    }
    @PostMapping("/{id}/close") public Poll close(@PathVariable Long id) { return service.closePoll(id); }
}
