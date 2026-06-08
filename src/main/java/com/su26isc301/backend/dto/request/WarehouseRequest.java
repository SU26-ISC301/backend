package com.su26isc301.backend.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WarehouseRequest {
    private String warehouseName;
    private String contactName;
    private String phoneNumber;
    private String province;
    private String district;
    private String ward;
    private String addressDetail;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
