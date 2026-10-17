package io.github.skyeberhard.hamletfolk.core;

import java.util.UUID;

/**
 * The key of a construction project's in-memory working state (retries, skipped blocks, grading passes...). Project numbers are only
 * unique within their village (every village counts from 1), so state keyed by the number alone is shared between villages that build
 * at the same time. Based on the fix by Owen D. Goode (R4.7/R4.8).
 */
public record WorkKey(UUID settlement, int project) {
}
