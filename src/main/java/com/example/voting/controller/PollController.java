package com.example.voting.controller;

import com.example.voting.model.Poll;
import com.example.voting.service.PollService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/polls")
public class PollController {
    private static final String QUESTION_KEY = "question";

    private final PollService service;

    public PollController(PollService service) {
        this.service = service;
    }

    @GetMapping
    public List<Poll> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public Poll getOne(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/public/{shareCode}")
    public Poll getPublic(@PathVariable String shareCode) {
        return service.getByShareCode(shareCode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String question = readQuestion(body);
        List<String> options = readOptions(body);
        PollService.PollCreation creation = service.createPollWithAdminToken(question, options);
        return Map.of(
                "poll", creation.poll(),
                "adminToken", creation.adminToken(),
                "shareUrl", "/p/" + creation.poll().getShareCode(),
                "creatorUsername", creation.creatorUsername(),
                "creatorPassword", creation.creatorPassword());
    }

    @PostMapping("/{id}/vote")
    public Poll vote(@PathVariable Long id,
                     @RequestHeader(value = "X-Voter-Id", required = false) String voterId,
                     @RequestBody Map<String, Object> body) {
        String voterName = body.get("voterName") == null ? null : body.get("voterName").toString();
        Object rawOptionId = body.get("optionId");
        if (rawOptionId == null) {
            throw new IllegalArgumentException("An option is required");
        }

        Long optionId;
        try {
            optionId = Long.valueOf(rawOptionId.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid option");
        }

        String customText = body.get("customText") == null ? null : body.get("customText").toString();
        return service.vote(id, voterName, voterId, optionId, customText);
    }

    @PostMapping("/{id}/close")
    public Poll close(@PathVariable Long id,
                      @RequestHeader(value = "X-Poll-Admin-Token", required = false) String adminToken) {
        return service.closePoll(id, adminToken);
    }

    @PutMapping("/{id}")
    public Poll update(@PathVariable Long id,
                       @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
                       @RequestHeader(value = "X-Creator-Username", required = false) String creatorUsername,
                       @RequestHeader(value = "X-Creator-Token", required = false) String creatorToken,
                       @RequestBody Map<String, Object> body) {
        String question = readQuestion(body);
        List<String> options = readOptions(body);
        return service.updatePoll(id, question, options, adminToken, creatorUsername, creatorToken);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id,
                       @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        service.deletePoll(id, adminToken);
    }

    private String readQuestion(Map<String, Object> body) {
        Object value = body.get(QUESTION_KEY);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> readOptions(Map<String, Object> body) {
        return (List<String>) body.get("options");
    }
}
