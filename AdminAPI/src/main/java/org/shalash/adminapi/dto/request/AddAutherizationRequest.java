package org.shalash.adminapi.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddAutherizationRequest {
    private String accessKey;
    private String pattern;
}
