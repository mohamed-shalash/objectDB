package org.shalash.adminapi.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.shalash.adminapi.dto.AutherizationResponse;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDto {
    private String accessKey;
    private boolean active;
    private List<AutherizationResponse> authorities;
}
