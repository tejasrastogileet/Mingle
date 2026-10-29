package com.meetnow.controller;

import com.meetnow.entity.MeetingRoom;
import com.meetnow.repository.MeetingRoomRepository;
import com.meetnow.service.AgoraTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingRoomRepository meetingRoomRepository;
    private final AgoraTokenService agoraTokenService;

    // In-memory participant name registry: channelName -> (uid -> name)
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> participantRegistry = new ConcurrentHashMap<>();

    // â”€â”€ Admission Queue â”€â”€
    // requestId -> AdmissionRequest
    private static final ConcurrentHashMap<String, AdmissionRequest> admissionQueue = new ConcurrentHashMap<>();

    private static class AdmissionRequest {
        String requestId;
        String channelName;
        String name;
        String email;
        String status; // PENDING, ACCEPTED, DENIED
        Instant createdAt;

        AdmissionRequest(String requestId, String channelName, String name, String email) {
            this.requestId = requestId;
            this.channelName = channelName;
            this.name = name;
            this.email = email;
            this.status = "PENDING";
            this.createdAt = Instant.now();
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("requestId", requestId);
            map.put("channelName", channelName);
            map.put("name", name);
            map.put("email", email);
            map.put("status", status);
            map.put("createdAt", createdAt.toString());
            return map;
        }
    }

    /**
     * POST /api/meetings/create â€” Create a new meeting room (authenticated)
     */
    @PostMapping("/create")
    public ResponseEntity<?> createMeetingRoom(
            @RequestBody Map<String, String> request,
            @CurrentSecurityContext(expression = "authentication?.name") String email) {

        String roomId = request.get("roomId");
        if (roomId == null || roomId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "Room ID is required"));
        }

        // Use the authenticated user's email as creator
        String creator = (email != null && !email.equals("anonymousUser"))
                ? email
                : request.getOrDefault("createdBy", "Guest");

        // Check if room already exists
        if (meetingRoomRepository.findByRoomId(roomId).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "Room ID already exists"));
        }

        MeetingRoom meetingRoom = MeetingRoom.builder()
                .roomId(roomId)
                .roomName(request.getOrDefault("roomName", "Meeting Room"))
                .createdBy(creator)
                .active(true)
                .build();

        meetingRoomRepository.save(meetingRoom);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Meeting room created successfully");
        response.put("roomId", roomId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/meetings/validate/{roomId} â€” Check if a room exists (public)
     * Now also returns the creator email so the frontend can identify the host.
     */
    @GetMapping("/validate/{roomId}")
    public ResponseEntity<?> validateRoom(@PathVariable String roomId) {
        Optional<MeetingRoom> roomOpt = meetingRoomRepository.findByRoomId(roomId);
        if (roomOpt.isPresent()) {
            MeetingRoom room = roomOpt.get();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Room is valid");
            response.put("roomId", roomId);
            response.put("createdBy", room.getCreatedBy());
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Room not found or invalid link"));
        }
    }

    /**
     * GET /api/meetings/token â€” Generate an Agora RTC token for an authorized participant
     */
    @GetMapping("/token")
    public ResponseEntity<?> getAgoraToken(
            @RequestParam String channelName,
            @RequestParam int uid,
            @CurrentSecurityContext(expression = "authentication?.name") String email) {
        if (channelName == null || !channelName.matches("[A-Za-z0-9_-]{1,64}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "Invalid channel name"));
        }
        if (uid <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "UID must be a positive integer"));
        }

        Optional<MeetingRoom> roomOptional = meetingRoomRepository.findByRoomId(channelName);
        if (roomOptional.isEmpty() || !roomOptional.get().isActive()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Meeting room not found or inactive"));
        }

        MeetingRoom room = roomOptional.get();
        boolean isHost = room.getCreatedBy() != null && room.getCreatedBy().equalsIgnoreCase(email);
        AdmissionRequest approvedRequest = null;
        if (!isHost) {
            approvedRequest = admissionQueue.values().stream()
                    .filter(request -> channelName.equals(request.channelName)
                            && email.equalsIgnoreCase(request.email)
                            && "ACCEPTED".equals(request.status))
                    .findFirst()
                    .orElse(null);
            if (approvedRequest == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", true, "message", "Host approval is required to join this meeting"));
            }
        }

        try {
            String token = agoraTokenService.generateRtcToken(channelName, uid);
            if (approvedRequest != null) {
                admissionQueue.remove(approvedRequest.requestId, approvedRequest);
            }
            return ResponseEntity.ok(Map.of("token", token));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", true, "message", "Failed to generate meeting token"));
        }
    }

    /**
     * POST /api/meetings/participants/register â€” Register participant name for a channel
     */
    @PostMapping("/participants/register")
    public ResponseEntity<?> registerParticipant(@RequestBody Map<String, String> request) {
        String channelName = request.get("channelName");
        String uid = request.get("uid");
        String name = request.get("name");

        if (channelName == null || uid == null || name == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "channelName, uid, and name are required"));
        }

        participantRegistry
                .computeIfAbsent(channelName, k -> new ConcurrentHashMap<>())
                .put(uid, name);

        return ResponseEntity.ok(Map.of("message", "Participant registered"));
    }

    /**
     * GET /api/meetings/participants/{channelName} â€” Get all participant names for a channel
     */
    @GetMapping("/participants/{channelName}")
    public ResponseEntity<?> getParticipants(@PathVariable String channelName) {
        Map<String, String> participants = participantRegistry.getOrDefault(channelName, new ConcurrentHashMap<>());
        return ResponseEntity.ok(participants);
    }

    /**
     * POST /api/meetings/participants/unregister â€” Remove participant from registry when they leave
     */
    @PostMapping("/participants/unregister")
    public ResponseEntity<?> unregisterParticipant(@RequestBody Map<String, String> request) {
        String channelName = request.get("channelName");
        String uid = request.get("uid");

        if (channelName != null && uid != null) {
            ConcurrentHashMap<String, String> channelParticipants = participantRegistry.get(channelName);
            if (channelParticipants != null) {
                channelParticipants.remove(uid);
                if (channelParticipants.isEmpty()) {
                    participantRegistry.remove(channelName);
                }
            }
        }

        return ResponseEntity.ok(Map.of("message", "Participant unregistered"));
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // â”€â”€ Admission Control Endpoints â”€â”€
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * POST /api/meetings/admission/request â€” Joiner submits an admission request
     * Body: { channelName, name, email }
     * Returns: { requestId, status }
     */
    @PostMapping("/admission/request")
    public ResponseEntity<?> requestAdmission(
            @RequestBody Map<String, String> request,
            @CurrentSecurityContext(expression = "authentication?.name") String email) {
        String channelName = request.get("channelName");
        String name = request.get("name");

        if (channelName == null || !channelName.matches("[A-Za-z0-9_-]{1,64}")
                || name == null || name.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "A valid channel name and participant name are required"));
        }

        Optional<MeetingRoom> roomOptional = meetingRoomRepository.findByRoomId(channelName);
        if (roomOptional.isEmpty() || !roomOptional.get().isActive()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Meeting room not found or inactive"));
        }
        if (roomOptional.get().getCreatedBy() != null
                && roomOptional.get().getCreatedBy().equalsIgnoreCase(email)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "Meeting host does not need admission"));
        }

        String requestId = UUID.randomUUID().toString();
        AdmissionRequest admissionRequest = new AdmissionRequest(requestId, channelName, name, email);
        admissionQueue.put(requestId, admissionRequest);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("requestId", requestId);
        response.put("status", "PENDING");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/meetings/admission/status/{requestId} â€” Joiner polls their admission status
     * Returns: { requestId, status }  (PENDING / ACCEPTED / DENIED)
     */
    @GetMapping("/admission/status/{requestId}")
    public ResponseEntity<?> getAdmissionStatus(
            @PathVariable String requestId,
            @CurrentSecurityContext(expression = "authentication?.name") String email) {
        AdmissionRequest req = admissionQueue.get(requestId);
        if (req == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Admission request not found"));
        }
        if (!email.equalsIgnoreCase(req.email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", true, "message", "This admission request belongs to another user"));
        }

        Map<String, String> response = new LinkedHashMap<>();
        response.put("requestId", req.requestId);
        response.put("status", req.status);

        // Keep approved requests until their one-time Agora token is issued.
        if ("DENIED".equals(req.status)) {
            admissionQueue.remove(requestId);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/meetings/admission/pending/{channelName} â€” Host polls for pending requests
     * Returns: list of pending admission requests for the given channel
     */
    @GetMapping("/admission/pending/{channelName}")
    public ResponseEntity<?> getPendingAdmissions(
            @PathVariable String channelName,
            @CurrentSecurityContext(expression = "authentication?.name") String email) {
        Optional<MeetingRoom> roomOptional = meetingRoomRepository.findByRoomId(channelName);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Meeting room not found"));
        }
        if (roomOptional.get().getCreatedBy() == null
                || !roomOptional.get().getCreatedBy().equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", true, "message", "Only the meeting host can view admission requests"));
        }

        List<Map<String, Object>> pendingList = admissionQueue.values().stream()
                .filter(req -> channelName.equals(req.channelName) && "PENDING".equals(req.status))
                .map(AdmissionRequest::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pendingList);
    }

    /**
     * POST /api/meetings/admission/respond â€” Host accepts or denies a request
     * Body: { requestId, action }  where action = "ACCEPT" or "DENY"
     */
    @PostMapping("/admission/respond")
    public ResponseEntity<?> respondToAdmission(
            @RequestBody Map<String, String> request,
            @CurrentSecurityContext(expression = "authentication?.name") String email) {
        String requestId = request.get("requestId");
        String action = request.get("action");

        if (requestId == null || action == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "requestId and action are required"));
        }

        AdmissionRequest req = admissionQueue.get(requestId);
        if (req == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Admission request not found"));
        }
        Optional<MeetingRoom> roomOptional = meetingRoomRepository.findByRoomId(req.channelName);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Meeting room not found"));
        }
        if (roomOptional.get().getCreatedBy() == null
                || !roomOptional.get().getCreatedBy().equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", true, "message", "Only the meeting host can respond to admission requests"));
        }

        if ("ACCEPT".equalsIgnoreCase(action)) {
            req.status = "ACCEPTED";
        } else if ("DENY".equalsIgnoreCase(action)) {
            req.status = "DENIED";
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "Invalid action. Use ACCEPT or DENY."));
        }

        return ResponseEntity.ok(Map.of("message", "Response recorded", "status", req.status));
    }
}
