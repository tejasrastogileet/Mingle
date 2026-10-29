package com.meetnow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "JWT_SECRET_KEY=test-only-signing-key-not-for-production")
@AutoConfigureMockMvc
class MeetnowApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void agoraTokenEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/meetings/token")
						.param("channelName", "room-123")
						.param("uid", "42"))
				.andExpect(status().isUnauthorized());
	}

}
