package com.agrovision.kiosk.ui.result.model;

import java.io.Serializable;

/**
 * ResultType
 *
 * Describes the confidence and resolution status
 * of a ScanResult.
 */
public enum ResultType implements Serializable {
    KNOWN,
    UNKNOWN
}
