package com.ecourtier.domain.transaction.entity;

/**
 * État du document PDF de contrat de bail généré lors de la double signature du séquestre.
 */
public enum StatutDocumentBail {
    NON_GENERE,
    A_GENERER,
    GENERE,
    ECHEC
}
