package com.mail.mini_mailing_app.spring.boot.services.impl;

import com.mail.mini_mailing_app.spring.boot.data.dto.request.*;
import com.mail.mini_mailing_app.spring.boot.data.dto.response.ApiResponse;
import com.mail.mini_mailing_app.spring.boot.data.dto.response.AuthenticationResponse;
import com.mail.mini_mailing_app.spring.boot.data.dto.response.MailResponse;
import com.mail.mini_mailing_app.spring.boot.data.dto.response.UpdateUserResponse;
import com.mail.mini_mailing_app.spring.boot.data.model.*;
import com.mail.mini_mailing_app.spring.boot.data.repository.*;
import com.mail.mini_mailing_app.spring.boot.exception.*;
import com.mail.mini_mailing_app.spring.boot.services.AppUserService;
import com.mail.mini_mailing_app.spring.boot.services.JwtTokenService;
import com.mail.mini_mailing_app.spring.boot.services.MyTokenService;
import com.mail.mini_mailing_app.spring.boot.services.UserService;
import com.mail.mini_mailing_app.spring.boot.services.cloudinary.CloudinaryService;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;


@Service
@AllArgsConstructor
public class UserServiceImpl  implements UserService {
    private final AppUserService appUserService;
    private final UserRepository userRepository;
    private final InboxRepository inboxRepository;
    private final SentRepository sentRepository;
    private final DraftRepository draftRepository;
    private final ModelMapper modelMapper;
    private final CloudinaryService cloudinaryService;
    private final MyTokenService myTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    @Override
    public String registerUser(RegisterUserRequest request) {
        AppUser appUser = appUserService.getBy(request.getPhoneNumber()).orElse(null);
        if(appUser != null){
            return checkWhetherUserIsEnableAndNotLocked(appUser);
        }else{
            AppUser userDetails = setAppUser(request);

            User savedUser = saveNewUser(request, userDetails);

            String token = myTokenService.generateAndSaveToken(savedUser.getUserDetails());
            String message = getVerificationMessage(savedUser.getUserDetails(), token);
            String to = savedUser.getUserDetails().getPhoneNumber();
            appUserService.sendSms(to, message);
            return """
                    An activation token has been sent to you account.
                    Please check your phone to input the token.
                    """;
        }
    }

    @Override
    public AuthenticationResponse verifyUser(VerificationRequest verificationRequest) {
        AppUser appUser = appUserService.getBy
                (verificationRequest.getPhoneNUmber()).orElse(null);
        if (appUser == null)throw new VerificationException("Invalid phone number");
        Optional<MyToken> receivedToken = myTokenService.validateReceivedToken(verificationRequest.getVerificationToken(), appUser);
        User user = getUserByAppUser(appUser).orElse(null);
        if(user == null)throw new NotFoundException("User not found");
        else{
            saveVerifiedUser(verificationRequest.getEmail(), appUser, user);
//            send mail to the user
//            tokenRepository.delete(receivedToken.get());
            myTokenService.deleteToken(receivedToken.get());
            String message = "Verification Successful";
            return this.jwtTokenService
                    .getAuthenticationResponse(appUser, message);
        }
    }


    private Optional<User> getUserByAppUser(AppUser appUser) {
        return userRepository.findByUserDetails(appUser);
    }

    @Override
    public AuthenticationResponse login(String email, String password) {
        AppUser appUser = appUserService.getUserByEmail(email);
//        User authenticatedUser = userRepository.findByUserDetails(appUser).orElseThrow(
//                ()-> new NotFoundException("User with this user details not found"));
//        String savedPassword = authenticatedUser.getUserDetails().getPassword();
//        if(savedPassword.equals(password))return AuthenticationResponse.builder()
//                .message("Authentication Successful")
//                .isSuccess(true)
//                .build();
//        throw new LoginException("Incorrect Password");

        try{
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email, password
        ));
        String message = "Authentication Successful";
        return this.jwtTokenService.getAuthenticationResponse(appUser, message);
        }catch (AuthenticationException exception){
            throw new RuntimeException("Incorrect password", exception);
        }
    }

//    private AuthenticationResponse getAuthenticationResponse(AppUser appUser, String message) {
//        UserDetails userDetails = jwtTokenService.getUserDetails(appUser.getEmail());
//        String accessToken = jwtService.generateAccessToken(userDetails);
//        String refreshToken = jwtService.generateRefreshToken(userDetails);
//        jwtTokenService.revokeAllUserTokens(appUser);
//        jwtTokenService.saveUserToken(appUser, accessToken);
//        return AuthenticationResponse.builder()
//                .message(message)
//                .isSuccess(true)
//                .accessToken(accessToken)
//                .refreshToken(refreshToken)
//                .build();
//    }

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElseThrow(
                ()-> new NotFoundException("User with this id is not found"));
    }
    @Override
    public MailResponse sendMail(MailRequest mailRequest) {
        User from = getUserById(mailRequest.getUserId());
        AppUser sender = appUserService.getUserByEmail(mailRequest.getEmail());
        User receiver = userRepository.findByUserDetails(sender).orElse(null);
        if(receiver == null)throw new RuntimeException();

        Message message = createMessage(mailRequest.getSubject(), mailRequest.getMessageBody());
        saveSentMessage(from, sender, message);

        saveReceivedMessage(from, receiver, message);
        return MailResponse.builder()
                .message("Mail sent successfully")
                .isSuccess(true)
                .build();
    }

    @Override
    public MailResponse draftMail(MailRequest mailRequest) {
        User user = getUserById(mailRequest.getUserId());

        Message message = createMessage(mailRequest.getSubject(), mailRequest.getMessageBody());
        message.setMessageType(MessageType.DRAFTED);

        Draft draft = new Draft();
        draft.setMessage(message);
        user.getDrafts().add(draft);
        userRepository.save(user);
        return MailResponse.builder()
                .message("Mail drafted successfully")
                .isSuccess(true)
                .build();
    }
    @Override
    public Inbox getInboxById(long userId, long inboxId) {
        User user = getUserById(userId);
        List<Inbox> receivedMessages = user.getReceivedMessages();
        for(Inbox inbox : receivedMessages){
            if (inbox.getId() == inboxId)return inbox;
        }
        throw new NotFoundException("Inbox with this id not found.");
    }

    @Override
    public Sent getSentMailById(long userId, long sentMailId) {
        User user = getUserById(userId);
        List<Sent> sentMessages = user.getSentMessages();
        for(Sent sent : sentMessages){
            if(sent.getId() == sentMailId)return sent;
        }
        throw new NotFoundException("Sent message with this id not found.");
    }

    @Override
    public Draft getDraftedMailById(long userId, long draftId) {
        User user = getUserById(userId);
        List<Draft> drafts = user.getDrafts();
        for(Draft draft : drafts){
            if (draft.getId() == draftId)return draft;
        }
        throw new NotFoundException("Draft message with this id not found.");
    }

    @Override
    public UpdateUserResponse updateUser(UpdateUserRequest request) {
        User user = getUserById(request.getUserId());
        AppUser appUser = user.getUserDetails();
        appUser.setFirstName(request.getFirstName());
        appUser.setMiddleName(request.getMiddleName());
        appUser.setLastName(request.getLastName());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return UpdateUserResponse.builder()
                .message("User Update Successful")
                .isSuccess(true)
                .build();
    }

    @Override
    public String uploadImage(UploadImageRequest request) {
        User user = getUserById(request.getUserId());
        String imageUrl = cloudinaryService.upload(request.getImage());
        user.setProfileImage(imageUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return "User image uploaded";
    }

    @Override
    public ApiResponse sendResetPasswordSms(String phoneNumber) {
        AppUser appUser = appUserService.getUserByPhoneNumber(phoneNumber);
        String token = myTokenService.generateAndSaveToken(appUser);
        String message = String.format("""
                To change your password, please enter the following characters to verify that it is you
                               %s
                """, token);
        appUserService.sendSms(phoneNumber, message);
        return ApiResponse.builder()
                .message("Check your phone for the token to reset your password")
                .isSuccess(true)
                .build();
    }

    @Override
    public UpdateUserResponse resetPassword(ResetPasswordRequest request) {
        AppUser appUser = appUserService.getBy(request.getPhoneNumber()).orElse(null);
        if(appUser == null)throw new NotFoundException("Invalid phone number");
        Optional<MyToken> myToken = myTokenService.validateReceivedToken(request.getToken(), appUser);
        appUser.setPassword(request.getNewPassword());
        if(!appUser.getPassword().equals(request.getConfirmNewPassword()))
            throw new EmailAppException("Password doesn't match");
        User user = getUserByAppUser(appUser).orElse(null);
        if(user == null)
            throw new NotFoundException("User not found");
        else{
            user.setUserDetails(appUser);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
//            tokenRepository.delete(myToken.get());
            myTokenService.deleteToken(myToken.get());
            return UpdateUserResponse.builder()
                    .message("Password changed successfully")
                    .isSuccess(true)
                    .build();
        }
    }

    @Override
    public void deleteInboxById(long userId, long inboxId) {
        User user = getUserById(userId);
        List<Inbox> inboxes = user.getReceivedMessages();
        inboxes.removeIf(inbox -> inbox.getId() == inboxId);
        userRepository.save(user);
    }

    @Override
    public void deleteSentMailById(long userId, long sentMailId) {
        User user = getUserById(userId);
        List<Sent> sentMessages = user.getSentMessages();
        sentMessages.removeIf(mail -> mail.getId() == sentMailId);
        userRepository.save(user);
    }

    @Override
    public void deleteDraftById(long userId, long draftId) {
        User user = getUserById(userId);
        List<Draft> drafts = user.getDrafts();
        drafts.removeIf(draft -> draft.getId() == draftId);
        userRepository.save(user);

    }

    @Override
    public void deleteAllInbox(long userId) {
        User user = getUserById(userId);
        user.getReceivedMessages().clear();
        userRepository.save(user);
    }

    @Override
    public void deleteAllSent(long userId) {
        User user = getUserById(userId);
        user.getSentMessages().clear();
        userRepository.save(user);
    }

    @Override
    public void deleteAllDrafts(long userId) {
        User user = getUserById(userId);
        user.getDrafts().clear();
        userRepository.save(user);
    }

    @Override
    public void deleteUserById(long userId) {
        userRepository.deleteById(userId);
    }

    @Override
    public Long userCount() {
        return userRepository.count();
    }

    @Override
    public Long inboxCount() {
        return inboxRepository.count();
    }

    @Override
    public Long sentCount() {
        return sentRepository.count();
    }

    @Override
    public Long draftCount() {
        return draftRepository.count();
    }

    @Override
    public String resendVerificationToken(String phoneNumber) {
        AppUser appUser = appUserService.getBy(phoneNumber)
                .orElse(null);
        if (appUser == null)throw new VerificationException("Invalid phone number");
        String token = myTokenService.generateAndSaveToken(appUser);

        String message = getVerificationMessage(appUser, token);

        appUserService.sendSms(phoneNumber, message);
        return """
                Another verification token has been sent to you phone
                Please, enter the verification token to enable your account.
                Note: The token will expired after 10 minutes
                """;
    }



    private User saveNewUser(RegisterUserRequest request, AppUser userDetails) {
        LocalDate dateOfBirth = convertToLocalDate(request.getDateOfBirth());
        int age = getAge(dateOfBirth);

        User user = new User();
        user.setUserDetails(userDetails);
        user.setDateOfBirth(dateOfBirth);
        user.setGender(request.getGender());
        user.setAge(age);
        return userRepository.save(user);
    }

    private AppUser setAppUser(RegisterUserRequest request) {
        AppUser userDetails = modelMapper.map(request, AppUser.class);
        userDetails.setRole(Role.USER);
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        userDetails.setPassword(encodedPassword);
        return userDetails;
    }

    private int getAge(LocalDate date) {
        return Period.between(date, LocalDate.now()).getYears();
    }

    private LocalDate convertToLocalDate(String dateOfBirth) {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate date = LocalDate.parse(dateOfBirth, dateTimeFormatter);
            if(date.isAfter(LocalDate.now()))
                throw new EmailAppException("Date must be in the past");
            return date;
    }

    private String checkWhetherUserIsEnableAndNotLocked(AppUser appUser) {
        String to = appUser.getPhoneNumber();
        if(appUser.isEnabled() && !appUser.isBlocked())
            throw new AlreadyExistsException(String.format(
                    "user with email %s already exists", appUser.getEmail()));
        else if (appUser.isBlocked() && appUser.isEnabled())
            throw new EmailAppException(
                    "Account registered with this email is blocked");
        else return resendVerificationToken(to);
    }

    private static String getVerificationMessage(AppUser appUser, String token) {
        return String.format("""
                Dear %s, please enter the following characters\s
                to verify your phone number
                            %s
                Note: the token expires after 10 minutes
                """, appUser.getFirstName(), token);
    }


    private void saveVerifiedUser(String email, AppUser appUser, User user) {
        appUser.setEmail(email);
        appUser.setBlocked(false);
        appUser.setEnabled(true);
        user.setUserDetails(appUser);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

//    private Optional<MyToken> validateReceivedToken(String token, AppUser appUser) {
//        Optional<MyToken> receivedToken = tokenRepository.findMyTokenByAppUserAndToken
//                (appUser, token);
//        if(receivedToken.isEmpty())throw new VerificationException("Invalid token");
//        else if(receivedToken.get().getExpirationTime().isBefore(LocalDateTime.now())){
//            tokenRepository.delete(receivedToken.get());
//            throw new VerificationException("Token is expired");
//        }
//        return receivedToken;
//    }


//    private void sendSms(String phoneNumber, String message) {
//        SmsRequest smsRequest = new SmsRequest();
//        smsRequest.setRecipientPhoneNumber(phoneNumber);
//        smsRequest.setMessage(message);
//        smsSender.sendSms(smsRequest);
//    }

    private static Message createMessage(String subject, String messageBody) {
        Message message = new Message();
        message.setSubject(subject);
        message.setMessageBody(messageBody);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private void saveSentMessage(User from, AppUser sender, Message message) {
        Sent sentMessage = new Sent();
        sentMessage.setToEmail(sender.getEmail());
        sentMessage.setMessage(message);
        sentMessage.getMessage().setMessageType(MessageType.SENT);
        from.getSentMessages().add(sentMessage);
        userRepository.save(from);
    }

    private void saveReceivedMessage(User from, User receiver, Message message) {
        Inbox inbox = new Inbox();
        inbox.setMessage(message);
        String email = from.getUserDetails().getEmail();
        inbox.setFromEmail(email);
        inbox.getMessage().setMessageType(MessageType.RECEIVED);
        receiver.getReceivedMessages().add(inbox);
        userRepository.save(receiver);
    }

}
