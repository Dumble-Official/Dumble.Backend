package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.domain.EntryLog;
import com.example.DumbleSubscription.domain.ParticipantGymNote;
import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.dto.NoteRequest;
import com.example.DumbleSubscription.dto.NoteResponse;
import com.example.DumbleSubscription.exception.ResourceNotFoundException;
import com.example.DumbleSubscription.repository.EntryLogRepository;
import com.example.DumbleSubscription.repository.ParticipantGymNoteRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints owned by gym staff (Section 21 + 15.1) — entry log + per-participant
 * notes. All require seller-side authorization (gym staff or owner).
 */
@RestController
public class GymStaffController {

    private final EntryLogRepository entryLogRepository;
    private final ParticipantGymNoteRepository noteRepository;

    public GymStaffController(EntryLogRepository entryLogRepository,
                              ParticipantGymNoteRepository noteRepository) {
        this.entryLogRepository = entryLogRepository;
        this.noteRepository = noteRepository;
    }

    @GetMapping("/me/gym/{gymId}/entries")
    public List<EntryLog> recentEntries(@PathVariable UUID gymId,
                                        @RequestParam(value = "period", defaultValue = "30d") String period) {
        Instant cutoff = parseCutoff(period);
        return entryLogRepository.findByGymIdAndScannedAtAfter(gymId, cutoff);
    }

    @GetMapping("/me/gym/{gymId}/participants/{participantId}/notes")
    public List<NoteResponse> listNotes(@PathVariable UUID gymId, @PathVariable UUID participantId) {
        return noteRepository
                .findByGymIdAndParticipantIdOrderByCreatedAtDesc(gymId, participantId)
                .stream().map(NoteResponse::from).toList();
    }

    @PostMapping("/me/gym/{gymId}/participants/{participantId}/notes")
    public NoteResponse addNote(@PathVariable UUID gymId,
                                @PathVariable UUID participantId,
                                @AuthenticationPrincipal CurrentUser staff,
                                @Valid @RequestBody NoteRequest req) {
        ParticipantGymNote note = new ParticipantGymNote();
        note.setGymId(gymId);
        note.setParticipantId(participantId);
        note.setNote(req.getNote());
        note.setAuthorStaffUserId(staff.getId());
        note.setCreatedAt(Instant.now());
        noteRepository.save(note);
        return NoteResponse.from(note);
    }

    @DeleteMapping("/me/gym/{gymId}/participants/{participantId}/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID gymId,
                                           @PathVariable UUID participantId,
                                           @PathVariable UUID noteId) {
        ParticipantGymNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
        if (!note.getGymId().equals(gymId) || !note.getParticipantId().equals(participantId)) {
            throw new ResourceNotFoundException("Note not found");
        }
        noteRepository.delete(note);
        return ResponseEntity.noContent().build();
    }

    private static Instant parseCutoff(String period) {
        Instant now = Instant.now();
        return switch (period) {
            case "7d"  -> now.minus(7,  ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            case "90d" -> now.minus(90, ChronoUnit.DAYS);
            case "all" -> Instant.EPOCH;
            default    -> now.minus(30, ChronoUnit.DAYS);
        };
    }
}
