package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.data.model.DiaryEntry
import com.shiye.journal.data.model.DiaryField
import com.shiye.journal.data.model.DiaryStatus
import com.shiye.journal.data.model.Template
import com.shiye.journal.data.local.entity.AiNoteEntity
import com.shiye.journal.data.repository.AiNoteRepository
import com.shiye.journal.data.repository.DiaryRepository
import com.shiye.journal.data.repository.TemplateRepository
import com.shiye.journal.domain.AiApplyDecision
import com.shiye.journal.domain.AiPrompts
import com.shiye.journal.domain.AiSummaryEntry
import com.shiye.journal.domain.AiSummaryList
import com.shiye.journal.domain.AutosaveQueue
import com.shiye.journal.domain.SaveFlushRegistry
import com.shiye.journal.domain.SaveState
import com.shiye.journal.domain.decideApply
import com.shiye.journal.domain.isLegacyField
import com.shiye.journal.util.DateTimeUtil
import com.shiye.journal.features.ai.AiClient
import com.shiye.journal.features.ai.AiConfigStore
import com.shiye.journal.features.ai.AiMode
import com.shiye.journal.features.ai.AiResult
import com.shiye.journal.features.ai.AiStage
import com.shiye.journal.features.ai.AiTask
import com.shiye.journal.features.ai.AiTaskKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 日记编辑页的状态容器。
 *
 * 三条容易出错、在这里明确处理的规则：
 *
 * 1. **内容以内存为准，不回读数据库**。
 *    进入编辑页时从库里读一次，之后 UI 显示的一直是 [contents]。
 *    如果每次保存完都拿库里的结果覆盖，用户正在敲的字会被"上一次保存的内容"顶掉，
 *    表现就是光标乱跳、刚打的字消失。
 *
 * 2. **自动保存只存内容，不改状态**。
 *    草稿/已完成的标记只在用户主动点"标记完成/取消完成"时变（[markComplete] / [markDraft]），
 *    否则刚写两个字就被标成"已完成"，这个标记就没有意义了。
 *
 * 3. **切换模板前先落盘当前内容**。
 *    切换会重建字段快照，若此时还有未保存的输入，合并时读到的就是旧内容。
 *
 * @param date 日记日期（yyyy-MM-dd）。一天只有一篇，日期即主键。
 */
class DiaryEditorViewModel(
    private val date: String,
    private val diaryRepository: DiaryRepository,
    private val templateRepository: TemplateRepository,
    private val saveFlushRegistry: SaveFlushRegistry,
    private val aiClient: AiClient,
    private val aiConfigStore: AiConfigStore,
    private val aiNoteRepository: AiNoteRepository
) : ViewModel() {

    private val registryKey = "diary-editor-$date"

    /** 日记本体。null = 还在加载 */
    private val _entry = MutableStateFlow<DiaryEntry?>(null)
    val entry: StateFlow<DiaryEntry?> = _entry.asStateFlow()

    /** 字段 id → 当前内容。UI 直接读它显示，输入时经 [updateContent] 更新 */
    private val _contents = MutableStateFlow<Map<String, String>>(emptyMap())
    val contents: StateFlow<Map<String, String>> = _contents.asStateFlow()

    private val _saveState = MutableStateFlow(SaveState.IDLE)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /** 一次性提示文案，被 UI 消费后调用 [consumeMessage] */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 这篇日记在编辑期间被删除了（列表页长按删除）。UI 据此退出编辑页。 */
    private val _removed = MutableStateFlow(false)
    val removed: StateFlow<Boolean> = _removed.asStateFlow()

    /** 切换模板用的候选模板 */
    val templates: StateFlow<List<Template>> = templateRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 当前模板的字段 key 集合。
     * null 表示模板已被删除 —— 这时不做"遗留字段"判断，免得把整篇都标成遗留。
     */
    private val _templateFieldKeys = MutableStateFlow<Set<String>?>(null)
    val templateFieldKeys: StateFlow<Set<String>?> = _templateFieldKeys.asStateFlow()

    private val queue = AutosaveQueue(
        scope = viewModelScope,
        debounceMillis = AUTOSAVE_DELAY_MILLIS,
        write = { writeCurrentContents() },
        onState = { _saveState.value = it }
    )

    init {
        viewModelScope.launch {
            val loaded = diaryRepository.getOrCreateByDate(date)
            applyEntry(loaded)
        }
        // 让 Activity 在退到后台时能强制把内容写出去（规格 §4.4）
        saveFlushRegistry.register(registryKey) { queue.flushAsync() }
    }

    // ---------- 输入 ----------

    fun updateContent(fieldId: String, content: String) {
        // 用"拷贝 + 覆盖"而不是直接改 Map：StateFlow 靠引用变化判断更新，
        // 原地修改同一个 Map 实例不会触发重组，界面会停在旧内容上
        _contents.value = _contents.value + (fieldId to content)
        queue.schedule()
    }

    /** 手动触发一次保存，用于"保存失败"后的重试 */
    fun retrySave() = queue.schedule()

    /**
     * 页面即将离开时调用。
     * 只负责启动保存；它跑在 viewModelScope 里，Composable 销毁后仍能跑完。
     */
    fun flushOnExit() = queue.flushAsync()

    // ---------- 状态标记 ----------

    /**
     * 标记为完成。**只有这里才校验必填**（规格 §4.5）。
     *
     * 校验不通过时给出具体缺哪些字段，并允许继续编辑 —— 不弹窗打断、不清空输入。
     */
    fun markComplete() {
        val current = _entry.value ?: return
        val missing = current.fields.filter { field ->
            field.required && (_contents.value[field.id].isNullOrBlank())
        }
        if (missing.isNotEmpty()) {
            _message.value = "还有必填字段没写：${missing.joinToString("、") { it.label }}"
            return
        }
        viewModelScope.launch {
            queue.flush()
            diaryRepository.setStatus(current.id, DiaryStatus.COMPLETE)
            diaryRepository.getByDate(date)?.let { applyEntry(it) }
            _message.value = "已标记为完成"
        }
    }

    /** 取消完成标记，退回草稿 */
    fun markDraft() {
        val current = _entry.value ?: return
        viewModelScope.launch {
            queue.flush()
            diaryRepository.setStatus(current.id, DiaryStatus.DRAFT)
            diaryRepository.getByDate(date)?.let { applyEntry(it) }
            _message.value = "已标记为草稿"
        }
    }

    // ---------- 模板 ----------

    /**
     * 切换模板。
     *
     * 先 [flush] 把当前输入落盘，再做字段合并 —— 否则"保留已有内容"
     * 保留的是上一次保存的版本，用户最后的输入就没了。
     */
    fun switchTemplate(templateId: String) {
        val current = _entry.value ?: return
        viewModelScope.launch {
            queue.flush()
            val retained = diaryRepository.switchTemplate(current.id, templateId)
            diaryRepository.getByDate(date)?.let { applyEntry(it) }
            _message.value = if (retained > 0) {
                "已切换模板；$retained 个旧字段无法对应，已原样保留在日记中"
            } else {
                "模板已切换"
            }
        }
    }

    /** 某个字段是否不属于当前模板（切换模板时保留下来的旧字段） */
    fun isLegacy(field: DiaryField): Boolean {
        val keys = _templateFieldKeys.value ?: return false
        return isLegacyField(field, keys)
    }

    // ---------- 导出 ----------

    /**
     * 当前内容的快照（含未保存的改动），供导出使用。
     *
     * 导出的必须是"屏幕上看到的"，而不是数据库里的旧版本 ——
     * 用户写完直接导出时，自动保存可能还没触发。
     */
    fun currentSnapshot(): DiaryEntry? {
        val base = _entry.value ?: return null
        return base.copy(
            fields = base.fields.map { field ->
                field.copy(content = _contents.value[field.id] ?: field.content)
            }
        )
    }

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * 「已删除」提示消费一次就清掉（和 [consumeMessage] 一样是一次性标记）。
     *
     * 不清的话它会一直为真：页面任何一次重新挂载都会被当成"刚被删掉"而立刻弹回去。
     */
    fun consumeRemoved() {
        _removed.value = false
    }

    // ---------------- V2 可选 AI（规格 §8.3 / §8.4） ----------------
    //
    // 两条底线：
    // 1. **AI 关着的时候什么都不做**：连配置都不读，按钮在 UI 上就不出现；
    // 2. **发请求前必须先过一道确认**，把"发什么、发到哪"摆给用户看。

    /** AI 是否可用（开着且配置完整）。UI 据此决定要不要显示 AI 入口。 */
    val aiEnabled: StateFlow<Boolean> = aiConfigStore.activeConfig
        .map { it.isUsable }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 请求会发到哪（模型名 + 主机）。
     *
     * "发送前告知"必须说清目标（规格 §8.2 要求本地模型也要明确目标地址）。
     * 只显示主机不显示完整路径：路径里可能带用户的租户标识之类。
     */
    val aiTarget: StateFlow<String> = aiConfigStore.activeConfig
        .map { config ->
            if (!config.isUsable) "" else "${config.model} @ ${hostOf(config.baseUrl)}"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private fun hostOf(baseUrl: String): String = runCatching {
        java.net.URI(baseUrl).host?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: baseUrl

    /** 当前进行中的 AI 任务；null 表示没有 */
    private val _aiTask = MutableStateFlow<AiTask?>(null)
    val aiTask: StateFlow<AiTask?> = _aiTask.asStateFlow()

    /**
     * 这篇日记已存的 AI 建议（V2 的独立字段，不属于模板）。
     *
     * **从晚到早**排列：最新的在最前面 —— 用户最想看的就是刚要的那一条。
     */
    private val _aiSummaryEntries = MutableStateFlow<List<AiSummaryEntry>>(emptyList())
    val aiSummaryEntries: StateFlow<List<AiSummaryEntry>> = _aiSummaryEntries.asStateFlow()

    /** 结果弹窗里用户勾选要保留的旧建议（内容是 key） */
    private val _keptSummaries = MutableStateFlow<Set<String>>(emptySet())
    val keptSummaries: StateFlow<Set<String>> = _keptSummaries.asStateFlow()

    /** 采纳后的一次性提示（含"撤销"入口） */
    private val _aiNotice = MutableStateFlow<String?>(null)
    val aiNotice: StateFlow<String?> = _aiNotice.asStateFlow()

    /**
     * 是否有可撤销的 AI 替换。
     *
     * 必须是 StateFlow 而不是一个普通方法：Compose 只跟踪快照里的状态，
     * 直接读字段的话按钮该出现时不出现（这个项目在别处踩过一次，见 PROGRESS.md）。
     */
    private val _aiCanUndo = MutableStateFlow(false)
    val aiCanUndo: StateFlow<Boolean> = _aiCanUndo.asStateFlow()

    /** 上一次被 AI 替换掉的原文，供撤销；采纳新结果时会被覆盖 */
    private var lastReplaced: Pair<String, String>? = null

    /**
     * 准备润色某个字段。
     *
     * 只做一件事：把要发的内容与快照记下来，进入"待确认"阶段 ——
     * **这时还没发出去**，等用户在弹窗里点了"发送"才真正请求（§8.2）。
     */
    fun preparePolish(fieldId: String) {
        val content = _contents.value[fieldId].orEmpty()
        if (content.isBlank()) {
            _message.value = "这个字段还是空的，没什么可润色的"
            return
        }
        _aiTask.value = AiTask(
            kind = AiTaskKind.POLISH,
            fieldId = fieldId,
            snapshot = content,
            stage = AiStage.CONFIRM_SEND
        )
    }

    /**
     * 准备给整篇要建议。
     *
     * **先让用户挑发哪几个字段**，再确认发送：
     * 一篇日记可能有心情、正文、备注等好几栏，
     * 一股脑全发出去既浪费，也可能有他不想让模型看到的内容。
     * 只有一个字段时就没必要问了，直接进确认。
     */
    fun prepareSummary() {
        val current = _entry.value ?: return
        val filled = current.fields.filter {
            (_contents.value[it.id] ?: it.content).isNotBlank()
        }
        if (filled.isEmpty()) {
            _message.value = "这篇还没写内容，没什么可建议的"
            return
        }
        val allIds = filled.map { it.id }.toSet()
        _aiTask.value = if (filled.size == 1) {
            AiTask(
                kind = AiTaskKind.SUMMARY,
                fieldId = null,
                snapshot = entryTextFor(allIds),
                stage = AiStage.CONFIRM_SEND,
                pickedFieldIds = allIds
            )
        } else {
            // 默认全选：多数时候就是要整篇；要排除由用户主动取消
            AiTask(
                kind = AiTaskKind.SUMMARY,
                fieldId = null,
                snapshot = "",
                stage = AiStage.PICK_FIELDS,
                pickedFieldIds = allIds
            )
        }
    }

    /** 勾选/取消一个字段 */
    fun togglePickedField(fieldId: String) {
        val task = _aiTask.value ?: return
        val picked = task.pickedFieldIds
        _aiTask.value = task.copy(
            pickedFieldIds = if (fieldId in picked) picked - fieldId else picked + fieldId
        )
    }

    /** 全选 / 全不选 */
    fun setAllPickedFields(pickAll: Boolean) {
        val task = _aiTask.value ?: return
        val filled = (_entry.value?.fields ?: emptyList()).filter {
            (_contents.value[it.id] ?: it.content).isNotBlank()
        }.map { it.id }
        _aiTask.value = task.copy(
            pickedFieldIds = if (pickAll) filled.toSet() else emptySet()
        )
    }

    /** 选好字段，进入发送确认 */
    fun confirmPickedFields() {
        val task = _aiTask.value ?: return
        val text = entryTextFor(task.pickedFieldIds)
        if (text.isBlank()) {
            _message.value = "至少选一个写了内容的字段"
            return
        }
        _aiTask.value = task.copy(snapshot = text, stage = AiStage.CONFIRM_SEND)
    }

    /** 按选中的字段拼出要发给模型的文本 */
    private fun entryTextFor(fieldIds: Set<String>): String {
        val current = _entry.value ?: return ""
        return AiPrompts.entryText(
            current.fields
                .filter { it.id in fieldIds }
                .map { it.label to (_contents.value[it.id] ?: it.content) }
        )
    }

    // 选字段弹窗的候选项由 UI 层从 entry + contents 直接派生 ——
    // 那样正文一改预览就跟着变；放在这里反而要再暴露一份状态

    /** 用户在确认框里点了"取消" */
    fun cancelAi() {
        _aiTask.value = null
    }

    /** 用户确认发送，真正发起请求 */
    fun sendAi() {
        val task = _aiTask.value ?: return
        val config = aiConfigStore.currentActiveConfig()
        if (!config.isUsable) {
            _aiTask.value = task.copy(stage = AiStage.FAILED, error = "AI 还没配置好")
            return
        }
        _aiTask.value = task.copy(stage = AiStage.RUNNING)
        viewModelScope.launch(Dispatchers.IO) {
            val messages = when (task.kind) {
                // 自定义提示词跟随当前在用的那套档案；没填就是默认行为
                AiTaskKind.POLISH -> AiPrompts.polish(task.snapshot, config.polishRule)
                AiTaskKind.SUMMARY -> AiPrompts.entryAdvice(task.snapshot, config.entryAdviceRule)
                AiTaskKind.ADVICE -> AiPrompts.advice(task.snapshot, config.adviceRule)
            }
            val outcome = aiClient.chat(config, messages)
            _aiTask.value = when (outcome) {
                is AiResult.Success -> {
                    // 建议类要把这次的和以前的摆在一起让用户挑，先把勾选初始化好
                    if (task.kind == AiTaskKind.SUMMARY) initSummarySelection()
                    task.copy(stage = AiStage.RESULT, result = outcome.text)
                }

                is AiResult.Failure -> task.copy(stage = AiStage.FAILED, error = outcome.failure.message(localTarget = config.mode == AiMode.LOCAL))
            }
        }
    }

    /**
     * 采纳结果，替换对应字段。
     *
     * 替换前先用 [decideApply] 判一下：用户在等 AI 的那几十秒里很可能又打了字，
     * 这时直接替换等于把人家新写的东西抹掉（§8.3 明确禁止）。
     * 冲突时提示会说清楚，由用户决定要不要覆盖。
     */
    fun adoptAiResult() {
        val task = _aiTask.value ?: return
        val result = task.result ?: return
        val fieldId = task.fieldId ?: return
        val current = _contents.value[fieldId].orEmpty()
        when (decideApply(task.snapshot, current, result)) {
            AiApplyDecision.REPLACE -> applyReplacement(fieldId, current, result)
            AiApplyDecision.NEEDS_CONFIRM -> {
                _aiNotice.value = "你在这期间又改了内容。再点一次「采纳」就会用结果覆盖现在的版本。"
                // 把快照更新成当前内容：下一次点击时"当前 == 快照"，就判为可替换
                _aiTask.value = task.copy(snapshot = current)
            }
            AiApplyDecision.NO_CHANGE -> {
                _aiTask.value = null
                _aiNotice.value = "内容和结果一样，没有改动"
            }
        }
    }

    private fun applyReplacement(fieldId: String, before: String, after: String) {
        lastReplaced = fieldId to before
        _aiCanUndo.value = true
        _contents.value = _contents.value + (fieldId to after)
        queue.schedule()
        _aiTask.value = null
        _aiNotice.value = "已替换为 AI 结果"
    }

    /** 撤销上一次 AI 替换（规格 §8.3「替换前保存原始内容快照，支持回滚」） */
    fun undoAiReplacement() {
        val (fieldId, before) = lastReplaced ?: return
        lastReplaced = null
        _aiCanUndo.value = false
        _contents.value = _contents.value + (fieldId to before)
        queue.schedule()
        _aiNotice.value = "已恢复到 AI 替换前的内容"
    }

    fun dismissAiNotice() {
        _aiNotice.value = null
    }

    /**
     * 结果弹窗里勾选/取消勾选一条旧建议。
     *
     * 同一篇日记可以反复要建议，用户可以挑着留：
     * 这次的好就留这条，上次那条也还有用就一起留。
     */
    fun toggleKeptSummary(text: String) {
        val current = _keptSummaries.value
        _keptSummaries.value = if (text in current) current - text else current + text
    }

    /** 本次结果要不要留（不满意这次的，可以只保留旧的） */
    private val _keepCurrentSummary = MutableStateFlow(true)
    val keepCurrentSummary: StateFlow<Boolean> = _keepCurrentSummary.asStateFlow()

    fun toggleKeepCurrentSummary() {
        _keepCurrentSummary.value = !_keepCurrentSummary.value
    }

    /**
     * 拿到结果后的默认勾选：**本次和已有的都先勾上**。
     *
     * 反过来（默认只勾本次）更"干净"，但有个坏处 ——
     * 用户一路点下来忘了勾，旧的总结就没了。丢失数据比多点两下严重，
     * 所以默认全留，要舍弃由用户主动取消勾选。
     */
    private fun initSummarySelection() {
        _keptSummaries.value = _aiSummaryEntries.value.map { it.text }.toSet()
        _keepCurrentSummary.value = true
    }

    /**
     * 把选中的建议存进这篇日记自己的「AI 总结」栏（规格 §8.4）。
     *
     * 存到**专属字段**而不是追加到正文里：
     * 用户要的是"给点建议"，不是让模型替他把日记再复述一遍 ——
     * 塞进正文只会污染记录，过几个月就分不清哪句是自己写的。
     *
     * 顺序**从晚到早**（本次的在最前），旧条目沿用各自原来的时间，
     * 不会被记成"刚生成的"—— 否则日期就骗人了。
     *
     * 这里直接落库而不走自动保存队列：它不属于模板字段内容，
     * 走队列会被当成"正文改动"参与状态刷新，没必要。
     */
    fun saveSummaryToField() {
        val entry = _entry.value ?: return
        val result = _aiTask.value?.result
        val existing = _aiSummaryEntries.value
        val now = DateTimeUtil.nowMillis()

        val merged = AiSummaryList.merge(
            existing = existing,
            kept = _keptSummaries.value,
            // 用户不勾这次的，就只保留他勾中的旧建议
            newResult = if (_keepCurrentSummary.value) result else null,
            now = now
        )
        viewModelScope.launch {
            diaryRepository.saveAiSummary(entry.id, AiSummaryList.writeNewestFirst(merged))
            _aiSummaryEntries.value = merged.sortedByDescending { it.createdAt }
            _aiTask.value = null
            initSummarySelection()
            _aiNotice.value = when {
                merged.isEmpty() -> "没有留下任何建议，「AI 总结」已清空"
                merged.size == 1 -> "已存入 1 条建议，正文没有改动"
                else -> "已存入 ${merged.size} 条建议（新的在前），正文没有改动"
            }
        }
    }

    /** 不要这条建议了：只清「AI 总结」栏，正文不动 */
    fun clearAiSummary() {
        val entry = _entry.value ?: return
        viewModelScope.launch {
            diaryRepository.saveAiSummary(entry.id, AiSummaryList.EMPTY)
            _aiSummaryEntries.value = emptyList()
            _aiNotice.value = "已清掉「AI 总结」，正文没有改动"
        }
    }

    /** 只删掉其中一条，其余保留 */
    fun removeSummaryEntry(item: AiSummaryEntry) {
        val entry = _entry.value ?: return
        val remaining = _aiSummaryEntries.value.filter { it !== item && it.text != item.text }
        viewModelScope.launch {
            diaryRepository.saveAiSummary(entry.id, AiSummaryList.writeNewestFirst(remaining))
            _aiSummaryEntries.value = remaining.sortedByDescending { it.createdAt }
        }
    }

    /**
     * 把结果存进独立的 AI 栏目而不动日记（规格 §8.4）。
     *
     * 和 [saveSummaryToField] 的区别：这条存在栏目里，
     * 日记本身（包括 AI 总结栏）一个字都不动。
     */
    fun saveResultToAiNotes() {
        val task = _aiTask.value ?: return
        val result = task.result ?: return
        val label = task.fieldId?.let { id ->
            _entry.value?.fields?.firstOrNull { it.id == id }?.label
        } ?: "整篇"
        viewModelScope.launch {
            aiNoteRepository.save(
                kind = when (task.kind) {
                    AiTaskKind.POLISH -> AiNoteEntity.Kind.POLISH
                    AiTaskKind.SUMMARY -> AiNoteEntity.Kind.SUMMARY
                    AiTaskKind.ADVICE -> AiNoteEntity.Kind.ADVICE
                },
                content = result,
                sourceDate = date,
                sourceLabel = label
            )
            _aiTask.value = null
            _aiNotice.value = "已存入 AI 栏目，日记没有改动"
        }
    }

    /** 结果页"丢弃" */
    fun discardAiResult() {
        _aiTask.value = null
    }

    /**
     * 每次进入编辑页调用一次：这篇日记在库里已经不存在了，就按日期重新建一篇。
     *
     * 编辑页 ViewModel 的 key 是日期，退到列表再回来拿到的是**同一个实例**，
     * 里面还留着已删除那篇的 id。不重建的话用户一敲字，自动保存写的是一个不存在的
     * id，立刻被判成"这篇已被删除"并自动返回 —— 表现为当天再也写不了日记。
     *
     * 判断顺序是刻意的：
     * - 首次加载还没跑完（`_entry` 为 null）时直接返回，交给 init，避免两边各建一篇；
     * - 日记还在库里就什么都不做，正在输入的内存内容一律不动。
     */
    suspend fun refreshIfMissing() {
        if (_entry.value == null) return
        if (diaryRepository.getByDate(date) != null) return
        queue.reset()
        _removed.value = false
        _message.value = null
        applyEntry(diaryRepository.getOrCreateByDate(date))
    }

    // ---------- 内部 ----------

    private suspend fun writeCurrentContents() {
        val id = _entry.value?.id ?: return
        val stillThere = diaryRepository.saveContents(id, _contents.value)
        if (!stillThere) {
            // 日记已被删除：让队列彻底失效，避免这条记录被在途写入"复活"
            queue.invalidate()
            _removed.value = true
        }
    }

    private suspend fun applyEntry(loaded: DiaryEntry) {
        _entry.value = loaded
        _contents.value = loaded.fields.associate { it.id to it.content }
        // 兼容老数据：那一列早先存的是纯文本，读出来当成一条没有时间的记录
        _aiSummaryEntries.value =
            AiSummaryList.parse(loaded.aiSummary, fallbackTime = loaded.updatedAt)
                .sortedByDescending { it.createdAt }
        val template = templateRepository.getById(loaded.templateId)
        _templateFieldKeys.value = template?.fields?.map { it.fieldKey }?.toSet()
    }

    override fun onCleared() {
        saveFlushRegistry.unregister(registryKey)
        queue.invalidate()
        super.onCleared()
    }

    companion object {
        /** 规格 §4.4：用户停止输入约 0.8 秒后触发 */
        const val AUTOSAVE_DELAY_MILLIS = 800L
    }
}
