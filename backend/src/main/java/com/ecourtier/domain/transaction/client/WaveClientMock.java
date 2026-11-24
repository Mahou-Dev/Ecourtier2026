package com.ecourtier.domain.transaction.client;

import com.ecourtier.domain.transaction.gateway.MockPaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bean Spring actif lorsque {@code ecourtier.payment.provider=mock} (valeur par défaut en tests/dev local).
 * Délègue intégralement à {@link MockPaymentGateway} (simulation locale persistante en base, zéro débit réel).
 */
@Component("mockPaymentGateway")
@ConditionalOnProperty(name = "ecourtier.payment.provider", havingValue = "mock", matchIfMissing = true)
public class WaveClientMock extends MockPaymentGateway {
}
