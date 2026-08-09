package org.shalash.objectstoredb.service;

import lombok.RequiredArgsConstructor;
import org.shalash.objectstoredb.repository.AuthoretiesRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final AuthoretiesRepository repository;

    public boolean isAuthorized(String uri, String accessKey, String authority) {
        return repository.getAuthoritiesByUser(accessKey)
                .stream()
                .anyMatch(auth ->
                        auth.getAuthorities().contains(authority)
                        && matches(auth.getPattern(), uri));
    }

    public boolean matches(String pattern, String uri) {
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            System.out.println("checking prefix: " + prefix+"\t"+uri.startsWith(prefix));
            return uri.startsWith(prefix);
        }

        System.out.println("pattern prefix: " + uri+"\t"+pattern);
        return uri.equals(pattern)
                || uri.startsWith(pattern + "/");
    }
}
