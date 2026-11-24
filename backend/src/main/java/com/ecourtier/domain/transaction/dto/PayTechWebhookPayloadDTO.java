package com.ecourtier.domain.transaction.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload officiel des notifications IPN PayTech (paiements, remboursements, transferts).
 * Supporte aussi bien JSON que application/x-www-form-urlencoded.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayTechWebhookPayloadDTO {

    @JsonProperty("type_event")
    private String typeEvent;

    @JsonProperty("ref_command")
    private String refCommand;

    @JsonProperty("item_name")
    private String itemName;

    @JsonProperty("item_price")
    private String itemPrice;

    @JsonProperty("final_item_price")
    private String finalItemPrice;

    @JsonProperty("initial_item_price")
    private String initialItemPrice;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("command_name")
    private String commandName;

    @JsonProperty("token")
    private String token;

    @JsonProperty("env")
    private String env;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("client_phone")
    private String clientPhone;

    @JsonProperty("custom_field")
    private String customField;

    // Champs spécifiques Transfer API (transfer_success / transfer_failed)
    @JsonProperty("id_transfer")
    private String idTransfer;

    @JsonProperty("token_transfer")
    private String tokenTransfer;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("state")
    private String state;

    @JsonProperty("service_name")
    private String serviceName;

    @JsonProperty("destination_number")
    private String destinationNumber;

    // Champs de vérification d'authenticité PayTech
    @JsonProperty("hmac_compute")
    private String hmacCompute;

    @JsonProperty("api_key_sha256")
    private String apiKeySha256;

    @JsonProperty("api_secret_sha256")
    private String apiSecretSha256;
}
