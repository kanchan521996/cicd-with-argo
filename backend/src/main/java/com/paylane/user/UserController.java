package com.paylane.user;

import com.paylane.security.AuthUser;
import com.paylane.user.UserDtos.ChangePasswordRequest;
import com.paylane.user.UserDtos.RecipientResponse;
import com.paylane.user.UserDtos.SetPinRequest;
import com.paylane.user.UserDtos.UpdateProfileRequest;
import com.paylane.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser auth) {
        return userService.me(auth.id());
    }

    @PutMapping("/me")
    public UserResponse update(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(auth.id(), req);
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(auth.id(), req);
    }

    @PostMapping("/me/pin")
    public UserResponse setPin(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody SetPinRequest req) {
        return userService.setPin(auth.id(), req);
    }

    /** Confirm who you are about to pay: ?query=email-or-phone */
    @GetMapping("/lookup")
    public RecipientResponse lookup(@AuthenticationPrincipal AuthUser auth, @RequestParam String query) {
        return userService.lookup(auth.id(), query);
    }
}
