package com.su26isc301.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerAppealRequest {
    private String appealReason;
    private List<String> evidenceUrls;
}
