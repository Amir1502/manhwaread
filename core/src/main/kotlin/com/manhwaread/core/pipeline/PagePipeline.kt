package com.manhwaread.core.pipeline

enum class PageStage { DOWNLOAD, PREPROCESS, DETECT, OCR, CLEANUP, INPAINT, TRANSLATE, TYPESET, COMPOSITE }
enum class StageStatus { PENDING, RUNNING, SUCCEEDED, FAILED }

data class StageRecord(val status: StageStatus = StageStatus.PENDING, val failure: String? = null) {
    init { require((status == StageStatus.FAILED) == !failure.isNullOrBlank()) }
}

data class PipelineSnapshot(val sourceImageHash: String, val stages: Map<PageStage, StageRecord>)

// Изменения выполняются через копии снимков; слой Room сможет сохранять их одной транзакцией.
class PagePipeline(snapshot: PipelineSnapshot) {
    private val hash = snapshot.sourceImageHash
    private val records = snapshot.stages.toMutableMap()

    init {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        require(records.keys == PageStage.entries.toSet())
        var previousSucceeded = true
        for (stage in PageStage.entries) {
            val status = records.getValue(stage).status
            if (status != StageStatus.PENDING) require(previousSucceeded)
            previousSucceeded = previousSucceeded && status == StageStatus.SUCCEEDED
        }
    }

    @Synchronized
    fun snapshot(): PipelineSnapshot = PipelineSnapshot(hash, records.toMap())

    @Synchronized
    fun nextStage(): PageStage? = PageStage.entries.firstOrNull { records.getValue(it).status != StageStatus.SUCCEEDED }

    @Synchronized
    fun begin(stage: PageStage): Boolean {
        val current = records.getValue(stage)
        if (current.status == StageStatus.SUCCEEDED || current.status == StageStatus.RUNNING) return false
        require(nextStage() == stage) { "Previous stages must succeed first" }
        records[stage] = StageRecord(StageStatus.RUNNING)
        return true
    }

    @Synchronized
    fun succeed(stage: PageStage) {
        if (records.getValue(stage).status == StageStatus.SUCCEEDED) return
        require(records.getValue(stage).status == StageStatus.RUNNING)
        records[stage] = StageRecord(StageStatus.SUCCEEDED)
    }

    @Synchronized
    fun fail(stage: PageStage, reason: String) {
        require(records.getValue(stage).status == StageStatus.RUNNING)
        require(reason.isNotBlank())
        records[stage] = StageRecord(StageStatus.FAILED, reason)
    }

    @Synchronized
    fun recoverInterrupted() {
        records.replaceAll { _, record ->
            if (record.status == StageStatus.RUNNING) StageRecord() else record
        }
    }

    @Synchronized
    fun invalidateFrom(stage: PageStage) {
        require(records.values.none { it.status == StageStatus.RUNNING }) { "Stop active work before invalidating" }
        PageStage.entries.filter { it.ordinal >= stage.ordinal }.forEach { records[it] = StageRecord() }
    }

    companion object {
        fun fresh(sourceImageHash: String): PagePipeline = PagePipeline(
            PipelineSnapshot(sourceImageHash, PageStage.entries.associateWith { StageRecord() }),
        )
    }
}
