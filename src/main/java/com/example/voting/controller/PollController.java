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
public class PollController {
    @Autowired private PollService service;

    @GetMapping
    public List<Poll> getAll() { return service.getAll(); }

    @GetMapping("/{id}")
    public Poll getOne(@PathVariable Long id) { return service.getById(id); }

    @GetMapping("/public/{shareCode}")
    public Poll getPublic(@PathVariable String shareCode) { return service.getByShareCode(shareCode); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String question = body.get("question") == null ? null : body.get("question").toString();
        @SuppressWarnings("unchecked") List<String> options = (List<String>) body.get("options");
        PollService.PollCreation creation = service.createPollWithAdminToken(question, options);
        return Map.of(
                "poll", creation.poll(),
                "adminToken", creation.adminToken(),
                "shareUrl", "/p/" + creation.poll().getShareCode()
        );
    }

    @PostMapping("/{id}/vote")
    public Poll vote(@PathVariable Long id,
                     @RequestHeader(value = "X-Voter-Id", required = false) String voterId,
                     @RequestBody Map<String, Object> body) {
        String voterName = body.get("voterName") == null ? null : body.get("voterName").toString();
        Object rawOptionId = body.get("optionId");
        if (rawOptionId == null) throw new IllegalArgumentException("An option is required");
        Long optionId;
        try { optionId = Long.valueOf(rawOptionId.toString()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid option"); }
        return service.vote(id, voterName, voterId, optionId);
    }

    @PostMapping("/{id}/close")
    public Poll close(@PathVariable Long id,
                      @RequestHeader(value = "X-Poll-Admin-Token", required = false) String adminToken) {
        return service.closePoll(id, adminToken);
    }
}
