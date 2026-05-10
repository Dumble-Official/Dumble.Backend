package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.domain.ParticipantPreferences;
import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.repository.ParticipantPreferencesRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Decision 10.1 — opt out of being named in gym subscriber lists. */
@RestController
@RequestMapping("/me/preferences")
public class PreferencesController {

    private final ParticipantPreferencesRepository repository;

    public PreferencesController(ParticipantPreferencesRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ParticipantPreferences get(@AuthenticationPrincipal CurrentUser user) {
        return repository.findById(user.getId()).orElseGet(() -> {
            ParticipantPreferences fresh = new ParticipantPreferences();
            fresh.setParticipantId(user.getId());
            fresh.setHideFromGymLists(false);
            fresh.setUpdatedAt(Instant.now());
            return fresh;
        });
    }

    @PutMapping
    public ParticipantPreferences set(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody PreferencesUpdate body) {
        ParticipantPreferences pref = repository.findById(user.getId()).orElseGet(() -> {
            ParticipantPreferences fresh = new ParticipantPreferences();
            fresh.setParticipantId(user.getId());
            return fresh;
        });
        // @NotNull on hideFromGymLists is now enforced via @Valid above, so
        // the field is non-null here — Boolean autoboxes safely.
        pref.setHideFromGymLists(body.getHideFromGymLists());
        pref.setUpdatedAt(Instant.now());
        return repository.save(pref);
    }

    @Data
    public static class PreferencesUpdate {
        @NotNull
        private Boolean hideFromGymLists;
    }
}
