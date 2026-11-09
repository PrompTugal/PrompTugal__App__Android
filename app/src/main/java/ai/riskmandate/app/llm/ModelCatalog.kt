package ai.riskmandate.app.llm

/**
 * One model file the app knows. Every value is pinned in code: the exact Hugging Face revision, the file
 * name, its size and its SHA-256. A file whose name, size or hash differ is never loaded.
 *
 * Sizes and hashes are the Hugging Face LFS values for that revision (the LFS object id is the SHA-256 of the
 * file's bytes). The two Gemma 4 files were also downloaded and hashed end to end once, on 9 Nov 2026, before
 * Dinis said models are downloaded by the tablet only; the tablet confirms these values on first download.
 */
data class ModelSpec(
    /** Stable id, lowercase letters and digits only. */
    val id: String,
    /** Shown to people. A model name, so not translated. */
    val displayName: String,
    val repo: String,
    /** A full commit hash, never a branch name, so the bytes behind the URL cannot change. */
    val revision: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Lowercase hex, 64 characters. */
    val sha256: String,
    /** The weight licence, as the model card states it. */
    val licence: String,
    /**
     * False while the file needs a Hugging Face login (gated). Such a model can only be pushed with adb by
     * someone who accepted its licence; the app never holds a token.
     */
    val downloadable: Boolean,
) {
    /** The one HTTPS URL this file may come from (Hugging Face redirects it to its CDN). */
    val url: String get() = "https://huggingface.co/$repo/resolve/$revision/$fileName"
}

object ModelCatalog {
    /** Default model. The same file and revision Google AI Edge Gallery offers (allow-list 1.0.23). */
    val GEMMA4_E2B = ModelSpec(
        id = "gemma4e2b",
        displayName = "Gemma 4 E2B",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        revision = "7fa1d78473894f7e736a21d920c3aa80f950c0db",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_583_085_056,
        sha256 = "ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42",
        licence = "apache-2.0",
        downloadable = true,
    )

    /** The bigger one. Same source as Edge Gallery (allow-list 1.0.23). */
    val GEMMA4_E4B = ModelSpec(
        id = "gemma4e4b",
        displayName = "Gemma 4 E4B",
        repo = "litert-community/gemma-4-E4B-it-litert-lm",
        revision = "9695417f248178c63a9f318c6e0c56cb917cb837",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_654_467_584,
        sha256 = "f335f2bfd1b758dc6476db16c0f41854bd6237e2658d604cbe566bcefd00a7bc",
        licence = "apache-2.0",
        downloadable = true,
    )

    /** Small fallback candidate. Gated on Hugging Face (Gemma licence): adb only, for the bench. */
    val GEMMA3_1B = ModelSpec(
        id = "gemma3m1b",
        displayName = "Gemma 3 1B (int4)",
        repo = "litert-community/Gemma3-1B-IT",
        revision = "aa49427ccd3bcacb1820d2bc3bb969eaffe632bf",
        fileName = "gemma3-1b-it-int4.litertlm",
        sizeBytes = 584_417_280,
        sha256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
        licence = "gemma",
        downloadable = false,
    )

    /** The smallest Google model for LiteRT-LM. Gated on Hugging Face (Gemma licence): adb only, for the bench. */
    val GEMMA3_270M = ModelSpec(
        id = "gemma3m270m",
        displayName = "Gemma 3 270M (q8)",
        repo = "litert-community/gemma-3-270m-it",
        revision = "9d2093270fb5aa49a986b49b5779d763dde7b630",
        fileName = "gemma3-270m-it-q8.litertlm",
        sizeBytes = 304_005_120,
        sha256 = "757e9119fa5bd667a2774fb470ac4afcd3190a21c677f8e69a5d6bc908abdd63",
        licence = "gemma",
        downloadable = false,
    )

    val ALL: List<ModelSpec> = listOf(GEMMA4_E2B, GEMMA4_E4B, GEMMA3_1B, GEMMA3_270M)

    fun byFileName(name: String): ModelSpec? = ALL.firstOrNull { it.fileName == name }
}
