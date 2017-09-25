package com.buyback.eve.web.rest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import com.buyback.eve.domain.Killmail;
import com.buyback.eve.repository.KillmailRepository;
import com.buyback.eve.service.JsonRequestService;
import com.buyback.eve.service.KillmailPuller;
import static com.buyback.eve.service.KillmailParser.parseKillmail;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for executing appraisal requests.
 */
@RestController
@RequestMapping("/api/killmail")
public class KillmailResource {

    private final Logger log = LoggerFactory.getLogger(KillmailResource.class);

    private LocalDateTime lastLongPullInvocation = fiveMinutesAgo();

    private final KillmailRepository killmailRepository;
    private final KillmailPuller killmailPuller;
    private final JsonRequestService jsonRequestService;

    public KillmailResource(final KillmailRepository killmailRepository,
                            final KillmailPuller killmailPuller,
                            final JsonRequestService jsonRequestService) {
        this.killmailRepository = killmailRepository;
        this.killmailPuller = killmailPuller;
        this.jsonRequestService = jsonRequestService;
    }

    /**
     * Adds a killmail if it doesn't exist yet.
     *
     * @param killmailId
     * @return
     */
    @PostMapping("/{killmailId}")
    public ResponseEntity addKillmail(@PathVariable("killmailId") Long killmailId) {
        log.info("Adding Killmail manually. ID={}", killmailId);

        Optional<Killmail> existingKill = killmailRepository.findByKillId(killmailId);
        if (!existingKill.isPresent()) {
            Optional<JSONObject> jsonObject = jsonRequestService.getKillmail(killmailId);
            if (jsonObject.isPresent()) {
                Killmail killmail = parseKillmail(jsonObject.get());
                killmailPuller.filterAndSaveKillmails(Collections.singletonList(killmail));
            }
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/long-pull")
    public ResponseEntity longPull() {
        if (lastLongPullInvocation.isBefore(fiveMinutesAgo())) {
            lastLongPullInvocation = LocalDateTime.now();
            killmailPuller.longPull();
            return ResponseEntity.status(200).build();
        } else {
            return ResponseEntity.status(420).build();
        }
    }

    private LocalDateTime fiveMinutesAgo() {
        return LocalDateTime.now().minusMinutes(5L);
    }
}