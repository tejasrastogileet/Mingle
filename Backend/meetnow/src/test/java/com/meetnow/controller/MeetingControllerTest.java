package com.meetnow.controller;

import com.meetnow.entity.MeetingRoom;
import com.meetnow.repository.MeetingRoomRepository;
import com.meetnow.service.AgoraTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingControllerTest {

    @Mock
    private MeetingRoomRepository meetingRoomRepository;

    @Mock
    private AgoraTokenService agoraTokenService;

    private MeetingController meetingController;

    @BeforeEach
    void setUp() {
        meetingController = new MeetingController(meetingRoomRepository, agoraTokenService);
    }

    @Test
    void hostCanReceiveTokenForActiveMeeting() {
        MeetingRoom room = room("host@example.com");
        when(meetingRoomRepository.findByRoomId("room-123")).thenReturn(Optional.of(room));
        when(agoraTokenService.generateRtcToken("room-123", 42)).thenReturn("rtc-token");

        ResponseEntity<?> response = meetingController.getAgoraToken("room-123", 42, "host@example.com");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("rtc-token", ((Map<?, ?>) response.getBody()).get("token"));
    }

    @Test
    void guestNeedsHostApprovalBeforeReceivingToken() {
        when(meetingRoomRepository.findByRoomId("room-123"))
                .thenReturn(Optional.of(room("host@example.com")));

        ResponseEntity<?> response = meetingController.getAgoraToken("room-123", 42, "guest@example.com");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(agoraTokenService);
    }

    @Test
    void invalidChannelAndUidAreRejected() {
        ResponseEntity<?> invalidChannel = meetingController.getAgoraToken("room/123", 42, "host@example.com");
        ResponseEntity<?> invalidUid = meetingController.getAgoraToken("room-123", 0, "host@example.com");

        assertEquals(HttpStatus.BAD_REQUEST, invalidChannel.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidUid.getStatusCode());
        verifyNoInteractions(meetingRoomRepository, agoraTokenService);
    }

    @Test
    void approvedGuestCanReceiveSingleMeetingToken() {
        MeetingRoom room = room("host@example.com");
        when(meetingRoomRepository.findByRoomId("room-123")).thenReturn(Optional.of(room));
        when(agoraTokenService.generateRtcToken("room-123", 42)).thenReturn("rtc-token");

        ResponseEntity<?> requestResponse = meetingController.requestAdmission(
                Map.of("channelName", "room-123", "name", "Guest"),
                "guest@example.com");
        String requestId = (String) ((Map<?, ?>) requestResponse.getBody()).get("requestId");

        ResponseEntity<?> approvalResponse = meetingController.respondToAdmission(
                Map.of("requestId", requestId, "action", "ACCEPT"),
                "host@example.com");
        ResponseEntity<?> statusResponse = meetingController.getAdmissionStatus(requestId, "guest@example.com");
        ResponseEntity<?> tokenResponse = meetingController.getAgoraToken("room-123", 42, "guest@example.com");
        ResponseEntity<?> secondTokenResponse = meetingController.getAgoraToken("room-123", 42, "guest@example.com");

        assertEquals(HttpStatus.OK, requestResponse.getStatusCode());
        assertEquals(HttpStatus.OK, approvalResponse.getStatusCode());
        assertEquals("ACCEPTED", ((Map<?, ?>) statusResponse.getBody()).get("status"));
        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, secondTokenResponse.getStatusCode());
    }

    @Test
    void inactiveOrMissingMeetingCannotIssueToken() {
        when(meetingRoomRepository.findByRoomId("room-123")).thenReturn(Optional.empty());

        ResponseEntity<?> missing = meetingController.getAgoraToken("room-123", 42, "host@example.com");

        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());

        MeetingRoom inactiveRoom = room("host@example.com");
        inactiveRoom.setActive(false);
        when(meetingRoomRepository.findByRoomId("room-123")).thenReturn(Optional.of(inactiveRoom));

        ResponseEntity<?> inactive = meetingController.getAgoraToken("room-123", 42, "host@example.com");

        assertEquals(HttpStatus.NOT_FOUND, inactive.getStatusCode());
        verifyNoInteractions(agoraTokenService);
    }

    private MeetingRoom room(String creator) {
        MeetingRoom room = MeetingRoom.builder()
                .roomId("room-123")
                .createdBy(creator)
                .active(true)
                .build();
        return room;
    }
}
