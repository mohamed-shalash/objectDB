package org.shalash.adminapi.controller;

import lombok.RequiredArgsConstructor;
import org.shalash.adminapi.dto.request.AddAutherizationRequest;
import org.shalash.adminapi.service.AuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/autherization")
@RequiredArgsConstructor
public class AutherizationController {

    private final AuthorizationService authorizationService;

    @PostMapping
    public ResponseEntity<String> addAutherization(@RequestBody AddAutherizationRequest request) {
          return authorizationService.addAutherization(request);
    }

    @DeleteMapping
    public ResponseEntity<String> removeAutherization(@RequestParam String accessKey,@RequestParam String pattern) {
               return authorizationService.removeAutherization(accessKey,pattern);
    }

}
