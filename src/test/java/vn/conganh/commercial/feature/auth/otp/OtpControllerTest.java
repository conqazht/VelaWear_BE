package vn.conganh.commercial.feature.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.feature.auth.email.EmailProvider;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.OtpPurpose;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@DisplayName("Module Auth - OtpController")
class OtpControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @MockitoBean
    private EmailProvider emailProvider;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("POST /auth/otp/request - 200: request REGISTER OTP successfully")
    void requestOtp_register_success() throws Exception {
        // Arrange
        OtpRequest request = new OtpRequest("newuser@velawear.local", OtpPurpose.REGISTER);
        doNothing().when(emailProvider).sendEmail(anyString(), anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Verification code sent successfully"));

        verify(emailProvider).sendEmail(eq("newuser@velawear.local"), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /auth/otp/request - 409: request REGISTER OTP fails if email already exists")
    void requestOtp_register_failsIfEmailExists() throws Exception {
        // Arrange
        User user = new User();
        user.setEmail("existing@velawear.local");
        user.setFullName("Existing User");
        user.setPassword(passwordEncoder.encode("Password123!"));
        user.setGender(UserGender.MALE);
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        userRepository.save(user);

        OtpRequest request = new OtpRequest("existing@velawear.local", OtpPurpose.REGISTER);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value(409))
                .andExpect(jsonPath("$.message").value("User already exists with email: existing@velawear.local"));

        verifyNoInteractions(emailProvider);
    }

    @Test
    @DisplayName("POST /auth/otp/request - 200: request FORGOT_PASSWORD OTP fails silently if email does not exist")
    void requestOtp_forgotPassword_silentSuccess() throws Exception {
        // Arrange
        OtpRequest request = new OtpRequest("nonexistent@velawear.local", OtpPurpose.FORGOT_PASSWORD);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Verification code sent successfully"));

        verifyNoInteractions(emailProvider);
    }

    @Test
    @DisplayName("POST /auth/otp/verify - 200: verify OTP successfully")
    void verifyOtp_success() throws Exception {
        // Arrange
        String email = "testverify@velawear.local";
        OtpRequest request = new OtpRequest(email, OtpPurpose.REGISTER);
        doNothing().when(emailProvider).sendEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Capture raw code sent to email provider
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailProvider).sendEmail(eq(email), anyString(), contentCaptor.capture());
        
        // Extract 6-digit code from the HTML template
        String emailContent = contentCaptor.getValue();
        String code = emailContent.replaceAll("(?s).*letter-spacing: 5px; color: #1a1a1a;\">(\\d{6})</span>.*", "$1");
        assertThat(code).hasSize(6);

        // Verify the code
        OtpVerifyRequest verifyRequest = new OtpVerifyRequest(email, OtpPurpose.REGISTER, code);
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Verification code verified successfully"));

        // Check if verified marker is stored in Redis
        String verifiedKey = "auth:otp:verified:REGISTER:" + email;
        assertThat(redisTemplate.hasKey(verifiedKey)).isTrue();
    }

    @Test
    @DisplayName("POST /auth/otp/verify - 400: verify wrong OTP increments attempts and fails")
    void verifyOtp_wrongCode_fails() throws Exception {
        // Arrange
        String email = "testwrong@velawear.local";
        OtpRequest request = new OtpRequest(email, OtpPurpose.REGISTER);
        doNothing().when(emailProvider).sendEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify the incorrect code
        OtpVerifyRequest verifyRequest = new OtpVerifyRequest(email, OtpPurpose.REGISTER, "000000");
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.message").value("Invalid verification code. Remaining attempts: 4"));

        // Check attempts key in Redis
        String attemptsKey = "auth:otp:attempts:REGISTER:" + email;
        assertThat(redisTemplate.opsForValue().get(attemptsKey)).isEqualTo("1");
    }
}
