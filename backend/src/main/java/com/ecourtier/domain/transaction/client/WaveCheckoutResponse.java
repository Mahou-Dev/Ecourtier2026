package com.ecourtier.domain.transaction.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaveCheckoutResponse {
    private String waveReference;
    private String checkoutUrl;
    private String status;
}
