package android.webkit

/** Test double: SslErrorHandler's constructor is package-private, so this lives in its package. Records the decision. */
class RecordingSslErrorHandler : SslErrorHandler() {
    var cancelled = false
    var proceeded = false
    override fun cancel() { cancelled = true }
    override fun proceed() { proceeded = true }
}
