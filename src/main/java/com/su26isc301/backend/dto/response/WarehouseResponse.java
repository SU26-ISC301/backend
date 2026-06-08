package com.su26isc301.backend.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class WarehouseResponse {
    private Long id;
    private String type;
    private String warehouseName;
    private String contactName;
    private String phoneNumber;
    private String province;
    private String district;
    private String ward;
    private String addressDetail;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean isDefault;
    private String status;
    private Instant createdAt;
}
