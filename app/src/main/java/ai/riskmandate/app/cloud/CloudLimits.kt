package ai.riskmandate.app.cloud

/**
 * Every limit of the cloud client in one place, none of them settable from a screen. The money numbers are
 * placeholders (security's proposal, checklist M.5.1) until Dinis sets them; change them here, in one commit.
 * Amounts are micro-dollars (1,000,000 = US$1).
 */
object CloudLimits {
    /** Most that may be spent in one app session (a process lifetime). */
    const val SESSION_CAP_MICROS = 500_000L

    /** Most that may be spent on one tablet in all, across restarts (the ledger). */
    const val TOTAL_CAP_MICROS = 2_000_000L

    /** Charged for an answer even when OpenRouter reports no cost (a floor under the estimate). */
    const val RESERVE_MICROS = 10_000L

    /** Least time between the end of one call and the start of the next. */
    const val MIN_GAP_MS = 2_000L

    /** The request body, in bytes. */
    const val MAX_REQUEST_BYTES = 32 * 1024

    /** What is read of a response, in characters, before it is refused. */
    const val MAX_RESPONSE_CHARS = 1_000_000

    /** Most characters of one server-sent line. */
    const val MAX_LINE_CHARS = 262_144
}
