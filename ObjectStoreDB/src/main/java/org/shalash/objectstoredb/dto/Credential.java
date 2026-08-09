package org.shalash.objectstoredb.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Credential {


    private Long id;

    private String accessKey;

    private String secretKey;
}
