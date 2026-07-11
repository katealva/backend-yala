package com.yala.event;

/**
 * Por qué se cerró una subasta flash. Permite distinguir el mensaje que se muestra en el chat del live:
 * cierre manual del vendedor vs. cierre automático al alcanzar el monto máximo permitido.
 */
public enum LiveAuctionCloseReason {
    /** El vendedor cerró la subasta manualmente. */
    MANUAL,
    /** Una puja alcanzó el monto máximo permitido (9999) y la subasta se cerró automáticamente. */
    MAX_REACHED
}
