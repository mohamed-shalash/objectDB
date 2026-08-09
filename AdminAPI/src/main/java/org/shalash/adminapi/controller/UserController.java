package org.shalash.adminapi.controller;

import lombok.RequiredArgsConstructor;
import org.shalash.adminapi.dto.request.GenerateHmacRequest;
import org.shalash.adminapi.dto.request.UserDto;
import org.shalash.adminapi.dto.request.UserRequest;
import org.shalash.adminapi.dto.response.GenerateHmacResponse;
import org.shalash.adminapi.dto.response.UserResponse;
import org.shalash.adminapi.service.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final AdminUserService adminUserService;

    @GetMapping("/{accessKey}")
    public ResponseEntity<UserDto> getByAccessKey(@PathVariable String accessKey) {
         return adminUserService.getByAccessKey(accessKey);
    }

    @PostMapping
    public ResponseEntity<UserResponse>  addNewUser(@RequestBody UserRequest request) {
        return adminUserService.addNewUser(request);
    }

    @PutMapping
    public ResponseEntity<UserResponse> updateUser(@RequestBody UserRequest request) {
        return adminUserService.updateUser(request);
    }

    @DeleteMapping("/{accessKey}")
    public ResponseEntity<String> removeUser(@PathVariable String accessKey) {
        return  adminUserService.removeUser(accessKey);
    }

    @PostMapping("/generateHmac")
    public ResponseEntity<GenerateHmacResponse> generateHmac(@RequestBody GenerateHmacRequest generateHmacRequest) {
        return adminUserService.generateHmac(generateHmacRequest);
    }
}
