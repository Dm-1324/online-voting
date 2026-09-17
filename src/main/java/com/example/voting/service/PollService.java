package com.example.voting.service;

import com.example.voting.exception.AlreadyVotedException;
import com.example.voting.exception.PollClosedException;
import com.example.voting.model.Poll;
import com.example.voting.model.PollOption;
import com.example.voting.model.Vote;
import com.example.voting.repository.PollOptionRepository;
import com.example.voting.repository.PollRepository;
import com.example.voting.repository.VoteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PollService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long OTHER_OPTION_ID = -1L;

    private final PollRepository pollRepository;
    private final PollOptionRepository optionRepository;
    private final VoteRepository voteRepository;

    @Value("${app.admin.username:}")
    private String mainAdminUsername;

    @Value("${app.admin.password:}")
    private String mainAdminPassword;

    public PollService(PollRepository pollRepository,
                       PollOptionRepository optionRepository,
                       VoteRepository voteRepository) {
        this.pollRepository = pollRepository;
        this.optionRepository = optionRepository;
        this.voteRepository = voteRepository;
    }

    public List<Poll> getAll() {
        List<Poll> polls = pollRepository.findAllWithOptions();
        polls.forEach(this::attachVoterNamesAndOther);
        return polls;
    }

    public Poll getById(Long id) {
        Poll poll = pollRepository.findWithOptionsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found: " + id));
        attachVoterNamesAndOther(poll);
        return poll;
    }

    public Poll getByShareCode(String code) {
        validateShareCode(code);
        Poll poll = pollRepository.findWithOptionsByShareCode(code.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
        attachVoterNamesAndOther(poll);
        return poll;
    }

    private void validateShareCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Poll link is invalid");
        }
    }

    private void attachVoterNamesAndOther(Poll poll) {
        poll.getOptions().removeIf(PollOption::isOther);
        poll.getOptions().forEach(option -> attachVoters(poll, option));

        PollOption other = createOtherOption(poll);
        List<Vote> otherVotes = voteRepository
                .findByPollIdAndOptionIdOrderByIdAsc(poll.getId(), OTHER_OPTION_ID);
        other.setVoteCount(otherVotes.size());
        other.setVoterNames(otherVotes.stream().map(Vote::getVoterName).toList());
        other.setCustomOpinions(otherVotes.stream()
                .map(Vote::getCustomText)
                .filter(text -> text != null && !text.isBlank())
                .toList());
        poll.getOptions().add(other);
    }

    private void attachVoterNames(Poll poll) {
        poll.getOptions().removeIf(PollOption::isOther);
        poll.getOptions().forEach(option -> attachVoters(poll, option));
    }

    private void attachVoters(Poll poll, PollOption option) {
        List<Vote> votes = voteRepository
                .findByPollIdAndOptionIdOrderByIdAsc(poll.getId(), option.getId());
        option.setVoterNames(votes.stream().map(Vote::getVoterName).toList());
        option.setCustomOpinions(List.of());
    }

    private PollOption createOtherOption(Poll poll) {
        PollOption other = new PollOption("Other");
        other.setId(OTHER_OPTION_ID);
        other.setPoll(poll);
        other.setOther(true);
        return other;
    }

    @Transactional
    public PollCreation createPollWithAdminToken(String question, List<String> optionTexts) {
        return createPollInternal(question, optionTexts);
    }

    @Transactional
    public Poll createPoll(String question, List<String> options) {
        return createPollInternal(question, options).poll();
    }

    private PollCreation createPollInternal(String question, List<String> optionTexts) {
        validatePoll(question, optionTexts);
        Poll poll = new Poll(question.trim());
        poll.setShareCode(generateShareCode());

        String adminToken = UUID.randomUUID().toString().replace("-", "");
        poll.setAdminTokenHash(sha256(adminToken));

        String username = "creator-" + poll.getShareCode().toLowerCase();
        String password = randomPassword();
        poll.setCreatorUsername(username);
        poll.setCreatorPasswordHash(sha256(password));
        addOptions(poll, optionTexts);

        Poll saved = pollRepository.save(poll);
        return new PollCreation(saved, adminToken, username, password);
    }

    private void addOptions(Poll poll, List<String> optionTexts) {
        for (String text : optionTexts) {
            PollOption option = new PollOption(text.trim());
            option.setPoll(poll);
            poll.getOptions().add(option);
        }
    }

    public AuthResult loginAdmin(String username, String password) {
        if (!credentialsMatch(username, password)) {
            throw unauthorized("Invalid admin username or password");
        }
        return new AuthResult(mainAdminTokenValue(), null);
    }

    public AuthResult loginCreator(String username, String password) {
        Poll poll = pollRepository.findByCreatorUsername(username)
                .orElseThrow(() -> unauthorized("Invalid creator username or password"));
        if (!creatorPasswordMatches(poll, password)) {
            throw unauthorized("Invalid creator username or password");
        }
        attachVoterNamesAndOther(poll);
        return new AuthResult(creatorToken(poll), poll);
    }

    public List<Poll> getAdminPolls(String token) {
        requireMainAdmin(token);
        return getAll();
    }

    public Poll getCreatorPoll(String username, String token) {
        Poll poll = pollRepository.findByCreatorUsername(username)
                .orElseThrow(() -> unauthorized("Creator account not found"));
        requireCreator(poll, username, token);
        attachVoterNamesAndOther(poll);
        return poll;
    }

    @Transactional
    public Poll updatePoll(Long id, String question, List<String> options,
                           String adminToken, String creatorUsername, String creatorToken) {
        Poll poll = loadPollForManagement(id, adminToken, creatorUsername, creatorToken);
        validatePoll(question, options);
        validateOptionCountAfterVotes(poll, options);
        replaceOriginalOptions(poll, question, options);
        return pollRepository.save(poll);
    }

    private Poll loadPollForManagement(Long id, String adminToken,
                                       String creatorUsername, String creatorToken) {
        Poll poll = pollRepository.findWithOptionsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found: " + id));
        attachVoterNames(poll);
        if (!isMainAdminToken(adminToken)) {
            requireCreator(poll, creatorUsername, creatorToken);
        }
        return poll;
    }

    private void validateOptionCountAfterVotes(Poll poll, List<String> options) {
        long voteCount = voteRepository.countByPollId(poll.getId());
        int originalCount = (int) poll.getOptions().stream()
                .filter(option -> !option.isOther())
                .count();
        if (voteCount > 0 && options.size() != originalCount) {
            throw new IllegalArgumentException(
                    "This poll already has votes. Keep the same number of original options when editing it.");
        }
    }

    private void replaceOriginalOptions(Poll poll, String question, List<String> options) {
        poll.setQuestion(question.trim());
        List<PollOption> originals = poll.getOptions().stream()
                .filter(option -> !option.isOther())
                .toList();
        int overlap = Math.min(options.size(), originals.size());
        updateExistingOptions(options, originals, overlap);
        addNewOptions(poll, options, originals.size());
    }

    private void updateExistingOptions(List<String> options, List<PollOption> originals, int overlap) {
        for (int i = 0; i < overlap; i++) {
            originals.get(i).setText(options.get(i).trim());
        }
    }

    private void addNewOptions(Poll poll, List<String> options, int originalCount) {
        for (int i = originalCount; i < options.size(); i++) {
            PollOption option = new PollOption(options.get(i).trim());
            option.setPoll(poll);
            poll.getOptions().add(option);
        }
    }

    @Transactional
    public void deletePoll(Long id, String token) {
        requireMainAdmin(token);
        if (!pollRepository.existsById(id)) {
            throw new IllegalArgumentException("Poll not found: " + id);
        }
        pollRepository.deleteById(id);
    }

    @Transactional
    public Poll vote(Long id, String name, Long option) {
        String voterId = name == null ? null : sha256(name.trim().toLowerCase());
        return voteInternal(id, name, voterId, option, null);
    }

    @Transactional
    public Poll vote(Long id, String name, String voterId, Long option) {
        return voteInternal(id, name, voterId, option, null);
    }

    @Transactional
    public Poll vote(Long id, String name, String voterId, Long option, String customText) {
        return voteInternal(id, name, voterId, option, customText);
    }

    private Poll voteInternal(Long id, String name, String voterId, Long option, String customText) {
        Poll poll = loadPollForVoting(id);
        assertPollIsVotable(poll);
        assertVoterIsValid(name, voterId);
        assertOptionProvided(option);
        assertVoterHasNotVoted(id, voterId);

        String finalCustomText = resolveVoteChoice(poll, option, customText);
        saveVote(id, voterId, name.trim(), option, finalCustomText);
        return poll;
    }

    private Poll loadPollForVoting(Long id) {
        Poll poll = pollRepository.findWithOptionsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found: " + id));
        attachVoterNames(poll);
        return poll;
    }

    private void assertPollIsVotable(Poll poll) {
        if (!poll.isOpen()) {
            throw new PollClosedException("This poll is closed");
        }
        if (poll.getExpiresAt() != null && poll.getExpiresAt().isBefore(Instant.now())) {
            poll.setOpen(false);
            pollRepository.save(poll);
            throw new PollClosedException("This poll has expired");
        }
    }

    private void assertVoterIsValid(String name, String voterId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Voter name is required");
        }
        if (voterId == null || voterId.isBlank() || voterId.length() > 64) {
            throw new IllegalArgumentException("Voter identity is invalid");
        }
    }

    private void assertOptionProvided(Long option) {
        if (option == null) {
            throw new IllegalArgumentException("An option is required");
        }
    }

    private void assertVoterHasNotVoted(Long pollId, String voterId) {
        if (voteRepository.findByPollIdAndVoterId(pollId, voterId).isPresent()) {
            throw new AlreadyVotedException("You have already voted on this poll");
        }
    }

    private String resolveVoteChoice(Poll poll, Long option, String customText) {
        if (option == OTHER_OPTION_ID) {
            return validateCustomText(customText);
        }

        PollOption matchedOption = poll.getOptions().stream()
                .filter(candidate -> candidate.getId() != null)
                .filter(candidate -> candidate.getId().equals(option))
                .filter(candidate -> !candidate.isOther())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid option for this poll"));
        matchedOption.setVoteCount(matchedOption.getVoteCount() + 1);
        optionRepository.save(matchedOption);
        return null;
    }

    private String validateCustomText(String customText) {
        if (customText == null || customText.isBlank()) {
            throw new IllegalArgumentException("Write your own opinion for Other");
        }
        String trimmed = customText.trim();
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("Other opinion must be 200 characters or fewer");
        }
        return trimmed;
    }

    private void saveVote(Long pollId, String voterId, String voterName,
                          Long option, String customText) {
        try {
            voteRepository.saveAndFlush(new Vote(pollId, voterId, voterName, option, customText));
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyVotedException("You have already voted on this poll");
        }
    }

    @Transactional
    public Poll closePoll(Long id, String token) {
        Poll poll = loadPollForManagementClose(id);
        if (!isAuthorizedManager(poll, token)) {
            throw unauthorized("Only an authorized manager can close this poll");
        }
        poll.setOpen(false);
        return pollRepository.save(poll);
    }

    @Transactional
    public Poll closePoll(Long id) {
        Poll poll = loadPollForManagementClose(id);
        poll.setOpen(false);
        return pollRepository.save(poll);
    }

    private Poll loadPollForManagementClose(Long id) {
        return pollRepository.findWithOptionsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found: " + id));
    }

    private boolean isAuthorizedManager(Poll poll, String token) {
        return isMainAdminToken(token) || isPollOwnerToken(poll, token) || isCreatorToken(poll, token);
    }

    private boolean isPollOwnerToken(Poll poll, String token) {
        return token != null && sha256(token).equals(poll.getAdminTokenHash());
    }

    private boolean isCreatorToken(Poll poll, String token) {
        return token != null && token.equals(creatorToken(poll));
    }

    private void requireMainAdmin(String token) {
        if (!isMainAdminToken(token)) {
            throw unauthorized("Admin access required");
        }
    }

    private boolean isMainAdminToken(String token) {
        return token != null && token.equals(mainAdminTokenValue());
    }

    private boolean credentialsMatch(String username, String password) {
        return mainAdminUsername != null
                && mainAdminPassword != null
                && mainAdminUsername.equals(username)
                && mainAdminPassword.equals(password);
    }

    private boolean creatorPasswordMatches(Poll poll, String password) {
        return poll.getCreatorPasswordHash() != null
                && poll.getCreatorPasswordHash().equals(sha256(password));
    }

    private void requireCreator(Poll poll, String username, String token) {
        boolean valid = poll.getCreatorUsername() != null
                && username != null
                && poll.getCreatorUsername().equals(username)
                && token != null
                && token.equals(creatorToken(poll));
        if (!valid) {
            throw unauthorized("Creator access required");
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private String mainAdminTokenValue() {
        return sha256(mainAdminUsername + ":" + mainAdminPassword);
    }

    private String creatorToken(Poll poll) {
        return sha256(poll.getCreatorUsername() + ":" + poll.getCreatorPasswordHash() + ":" + poll.getId());
    }

    private void validatePoll(String question, List<String> options) {
        validateQuestion(question);
        validateOptions(options);
    }

    private void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question is required");
        }
        if (question.trim().length() > 200) {
            throw new IllegalArgumentException("Question must be 200 characters or fewer");
        }
    }

    private void validateOptions(List<String> options) {
        if (options == null || options.size() < 2 || options.size() > 10) {
            throw new IllegalArgumentException("A poll needs between 2 and 10 options");
        }
        for (String option : options) {
            if (option == null || option.isBlank() || option.trim().length() > 100) {
                throw new IllegalArgumentException(
                        "Each option is required and must be 100 characters or fewer");
            }
        }
    }

    private String generateShareCode() {
        String code;
        do {
            code = randomAlphabetString(8);
        } while (pollRepository.findByShareCode(code).isPresent());
        return code;
    }

    private String randomPassword() {
        return randomAlphabetString(10);
    }

    private String randomAlphabetString(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return value.toString();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record PollCreation(Poll poll, String adminToken, String creatorUsername, String creatorPassword) {
    }

    public record AuthResult(String token, Poll poll) {
    }
}
