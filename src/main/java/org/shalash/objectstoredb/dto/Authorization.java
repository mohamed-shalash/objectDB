package org.shalash.objectstoredb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Authorization {
    private String accessKey;
    private String pattern;
    private List<String> authorities = new ArrayList<>();
}
