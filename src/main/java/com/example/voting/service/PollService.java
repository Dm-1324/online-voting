package com.example.voting.service;

import com.example.voting.exception.AlreadyVotedException;
import com.example.voting.exception.PollClosedException;
import com.example.voting.model.Poll;
import com.example.voting.model.PollOption;
import com.example.voting.model.Vote;
import com.example.voting.repository.PollOptionRepository;
import com.example.voting.repository.PollRepository;
import com.example.voting.repository.VoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String MAIN_ADMIN_USERNAME="dhruvm1324";
    private static final String MAIN_ADMIN_PASSWORD="admin";
    private static final long OTHER_OPTION_ID=-1L;
    @Autowired private PollRepository pollRepository;
    @Autowired private PollOptionRepository optionRepository;
    @Autowired private VoteRepository voteRepository;

    @Transactional public List<Poll> getAll(){List<Poll> polls=pollRepository.findAllWithOptions();polls.forEach(this::attachVoterNamesAndOther);return polls;}
    @Transactional public Poll getById(Long id){Poll p=pollRepository.findWithOptionsById(id).orElseThrow(()->new IllegalArgumentException("Poll not found: "+id));attachVoterNamesAndOther(p);return p;}
    @Transactional public Poll getByShareCode(String code){if(code==null||code.isBlank())throw new IllegalArgumentException("Poll link is invalid");Poll p=pollRepository.findWithOptionsByShareCode(code.trim().toUpperCase()).orElseThrow(()->new IllegalArgumentException("Poll not found"));attachVoterNamesAndOther(p);return p;}

    private void attachVoterNamesAndOther(Poll poll){
        poll.getOptions().removeIf(PollOption::isOther);
        poll.getOptions().forEach(o->{List<Vote> votes=voteRepository.findByPollIdAndOptionIdOrderByIdAsc(poll.getId(),o.getId());o.setVoterNames(votes.stream().map(Vote::getVoterName).toList());o.setCustomOpinions(List.of());});
        PollOption other=new PollOption("Other");other.setId(OTHER_OPTION_ID);other.setPoll(poll);other.setOther(true);
        List<Vote> votes=voteRepository.findByPollIdAndOptionIdOrderByIdAsc(poll.getId(),OTHER_OPTION_ID);other.setVoteCount(votes.size());other.setVoterNames(votes.stream().map(Vote::getVoterName).toList());other.setCustomOpinions(votes.stream().map(Vote::getCustomText).filter(t->t!=null&&!t.isBlank()).toList());poll.getOptions().add(other);
    }

    @Transactional public PollCreation createPollWithAdminToken(String question,List<String> optionTexts){validatePoll(question,optionTexts);Poll p=new Poll(question.trim());p.setShareCode(generateShareCode());String adminToken=UUID.randomUUID().toString().replace("-","");p.setAdminTokenHash(sha256(adminToken));String username="creator-"+p.getShareCode().toLowerCase();String password=randomPassword();p.setCreatorUsername(username);p.setCreatorPasswordHash(sha256(password));for(String text:optionTexts){PollOption o=new PollOption(text.trim());o.setPoll(p);p.getOptions().add(o);}return new PollCreation(pollRepository.save(p),adminToken,username,password);}
    public Poll createPoll(String q,List<String> opts){return createPollWithAdminToken(q,opts).poll();}

    public AuthResult loginAdmin(String username,String password){if(!MAIN_ADMIN_USERNAME.equals(username)||!MAIN_ADMIN_PASSWORD.equals(password))throw unauthorized("Invalid admin username or password");return new AuthResult(sha256(MAIN_ADMIN_USERNAME+":"+MAIN_ADMIN_PASSWORD),null);}
    @Transactional public AuthResult loginCreator(String username,String password){Poll p=pollRepository.findByCreatorUsername(username).orElseThrow(()->unauthorized("Invalid creator username or password"));if(p.getCreatorPasswordHash()==null||!p.getCreatorPasswordHash().equals(sha256(password)))throw unauthorized("Invalid creator username or password");attachVoterNamesAndOther(p);return new AuthResult(creatorToken(p),p);}
    @Transactional public List<Poll> getAdminPolls(String token){requireMainAdmin(token);return getAll();}
    @Transactional public Poll getCreatorPoll(String username,String token){Poll p=pollRepository.findByCreatorUsername(username).orElseThrow(()->unauthorized("Creator account not found"));requireCreator(p,username,token);attachVoterNamesAndOther(p);return p;}

    @Transactional public Poll updatePoll(Long id,String question,List<String> options,String adminToken,String creatorUsername,String creatorToken){
        Poll p=getById(id);boolean main=isMainAdminToken(adminToken);if(!main)requireCreator(p,creatorUsername,creatorToken);validatePoll(question,options);long votes=voteRepository.countByPollId(id);List<PollOption> originals=p.getOptions().stream().filter(o->!o.isOther()).toList();
        if(votes>0&&options.size()!=originals.size())throw new IllegalArgumentException("This poll already has votes. Keep the same number of original options when editing it.");
        p.setQuestion(question.trim());for(int i=0;i<Math.min(options.size(),originals.size());i++)originals.get(i).setText(options.get(i).trim());if(options.size()>originals.size())for(int i=originals.size();i<options.size();i++){PollOption o=new PollOption(options.get(i).trim());o.setPoll(p);p.getOptions().add(o);}p.getOptions().removeIf(PollOption::isOther);return pollRepository.save(p);
    }
    @Transactional public void deletePoll(Long id,String token){requireMainAdmin(token);if(!pollRepository.existsById(id))throw new IllegalArgumentException("Poll not found: "+id);pollRepository.deleteById(id);}

    @Transactional public Poll vote(Long id,String name,Long option){return vote(id,name,name==null?null:sha256(name.trim().toLowerCase()),option,null);}
    @Transactional public Poll vote(Long id,String name,String voterId,Long option){return vote(id,name,voterId,option,null);}
    @Transactional public Poll vote(Long id,String name,String voterId,Long option,String customText){
        Poll p=getById(id);if(!p.isOpen())throw new PollClosedException("This poll is closed");if(p.getExpiresAt()!=null&&p.getExpiresAt().isBefore(Instant.now())){p.setOpen(false);pollRepository.save(p);throw new PollClosedException("This poll has expired");}if(name==null||name.isBlank())throw new IllegalArgumentException("Voter name is required");if(voterId==null||voterId.isBlank()||voterId.length()>64)throw new IllegalArgumentException("Voter identity is invalid");if(option==null)throw new IllegalArgumentException("An option is required");if(voteRepository.findByPollIdAndVoterId(id,voterId).isPresent())throw new AlreadyVotedException("You have already voted on this poll");
        if(option==OTHER_OPTION_ID){if(customText==null||customText.isBlank())throw new IllegalArgumentException("Write your own opinion for Other");customText=customText.trim();if(customText.length()>200)throw new IllegalArgumentException("Other opinion must be 200 characters or fewer");}else{PollOption o=p.getOptions().stream().filter(x->x.getId()!=null&&x.getId().equals(option)&&!x.isOther()).findFirst().orElseThrow(()->new IllegalArgumentException("Invalid option for this poll"));o.setVoteCount(o.getVoteCount()+1);optionRepository.save(o);}
        try{voteRepository.saveAndFlush(new Vote(id,voterId,name.trim(),option,customText));}catch(DataIntegrityViolationException ex){throw new AlreadyVotedException("You have already voted on this poll");}return p;
    }

    @Transactional public Poll closePoll(Long id,String token){Poll p=getById(id);if(isMainAdminToken(token)||sha256(token==null?"":token).equals(p.getAdminTokenHash())||token!=null&&token.equals(creatorToken(p))){p.setOpen(false);return pollRepository.save(p);}throw unauthorized("Only an authorized manager can close this poll");}
    public Poll closePoll(Long id){Poll p=getById(id);p.setOpen(false);return pollRepository.save(p);}

    private void requireMainAdmin(String token){if(!isMainAdminToken(token))throw unauthorized("Admin access required");}
    private boolean isMainAdminToken(String token){return token!=null&&token.equals(sha256(MAIN_ADMIN_USERNAME+":"+MAIN_ADMIN_PASSWORD));}
    private void requireCreator(Poll p,String username,String token){if(p.getCreatorUsername()==null||username==null||!p.getCreatorUsername().equals(username)||token==null||!token.equals(creatorToken(p)))throw unauthorized("Creator access required");}
    private ResponseStatusException unauthorized(String m){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,m);}
    private String creatorToken(Poll p){return sha256(p.getCreatorUsername()+":"+p.getCreatorPasswordHash()+":"+p.getId());}
    private void validatePoll(String q,List<String> opts){if(q==null||q.isBlank())throw new IllegalArgumentException("Question is required");if(q.trim().length()>200)throw new IllegalArgumentException("Question must be 200 characters or fewer");if(opts==null||opts.size()<2||opts.size()>10)throw new IllegalArgumentException("A poll needs between 2 and 10 options");for(String t:opts)if(t==null||t.isBlank()||t.trim().length()>100)throw new IllegalArgumentException("Each option is required and must be 100 characters or fewer");}
    private String generateShareCode(){String c;do{StringBuilder v=new StringBuilder(8);for(int i=0;i<8;i++)v.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));c=v.toString();}while(pollRepository.findByShareCode(c).isPresent());return c;}
    private String randomPassword(){StringBuilder v=new StringBuilder(10);for(int i=0;i<10;i++)v.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));return v.toString();}
    private String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((v==null?"":v).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 is unavailable",e);}}
    public record PollCreation(Poll poll,String adminToken,String creatorUsername,String creatorPassword){}
    public record AuthResult(String token,Poll poll){}
}
