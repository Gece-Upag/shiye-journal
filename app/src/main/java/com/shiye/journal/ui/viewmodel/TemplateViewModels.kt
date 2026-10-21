package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.data.model.FieldType
import com.shiye.journal.data.model.Template
import com.shiye.journal.data.model.TemplateField
import com.shiye.journal.data.repository.TemplateRepository
import com.shiye.journal.domain.newTemplateField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 模板管理列表页。
 */
class TemplateViewModel(private val repository: TemplateRepository) : ViewModel() {

    val templates: StateFlow<List<Template>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setDefault(id: String) {
        viewModelScope.launch { repository.setDefault(id) }
    }

    /**
     * 删除模板。
     *
     * 规格 §4.5：删除模板绝不能级联删除已有日记 —— 靠日记里的字段快照已经做到，
     * 这里额外挡一道"不能删掉最后一个模板"，否则新建日记就没有模板可用了。
     */
    fun delete(id: String) {
        viewModelScope.launch {
            if (!repository.canDelete(templates.value.size)) {
                _message.value = "至少保留一个模板"
                return@launch
            }
            repository.delete(id)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/**
 * 模板编辑 / 新建页。
 *
 * 编辑的是一份内存草稿，点保存时才校验并写库：
 * 边输边校验会在"刚点开还没填"时满屏报错，体验很差（规格也明确要求
 * "不要在每次输入时强制拦截用户"）。
 *
 * @param templateId null 表示新建
 */
class TemplateEditorViewModel(
    private val repository: TemplateRepository,
    private val templateId: String?
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _fields = MutableStateFlow<List<TemplateField>>(listOf(defaultFirstField()))
    val fields: StateFlow<List<TemplateField>> = _fields.asStateFlow()

    /** 编辑已有模板时，未加载完之前 UI 显示加载态，避免把"新建"的默认值闪一下 */
    private val _loading = MutableStateFlow(templateId != null)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val isNew: Boolean get() = templateId == null

    init {
        val id = templateId
        if (id != null) {
            viewModelScope.launch {
                repository.getById(id)?.let { template ->
                    _name.value = template.name
                    _fields.value = template.fields
                }
                _loading.value = false
            }
        }
    }

    fun setName(value: String) {
        _name.value = value
    }

    fun addField() {
        _fields.value = _fields.value + newTemplateField(
            templateId = templateId.orEmpty(),
            label = "",
            type = FieldType.MULTI,
            required = false,
            sortOrder = _fields.value.size
        )
    }

    fun setLabel(fieldId: String, label: String) {
        replaceField(fieldId) { it.copy(label = label) }
    }

    fun setType(fieldId: String, type: FieldType) {
        replaceField(fieldId) { it.copy(type = type) }
    }

    fun setRequired(fieldId: String, required: Boolean) {
        replaceField(fieldId) { it.copy(required = required) }
    }

    fun removeField(fieldId: String) {
        // 剩最后一个字段时不让删：规格要求"至少包含一个字段"
        if (_fields.value.size <= 1) return
        _fields.value = _fields.value
            .filter { it.id != fieldId }
            .reindexed()
    }

    fun moveUp(fieldId: String) = move(fieldId, -1)

    fun moveDown(fieldId: String) = move(fieldId, 1)

    /**
     * 保存。
     * @return 错误文案；null 表示保存成功。校验规则见 [TemplateRepository.validate]。
     */
    suspend fun save(): String? {
        val error = repository.validate(_name.value, _fields.value, templateId)
        if (error != null) return error

        if (templateId == null) {
            repository.create(_name.value, _fields.value)
            return null
        }
        val existing = repository.getById(templateId) ?: return "模板不存在"
        repository.update(existing, _name.value, _fields.value)
        return null
    }

    private fun move(fieldId: String, offset: Int) {
        val list = _fields.value.toMutableList()
        val index = list.indexOfFirst { it.id == fieldId }
        val target = index + offset
        if (index < 0 || target !in list.indices) return
        val tmp = list[index]
        list[index] = list[target]
        list[target] = tmp
        _fields.value = list.reindexed()
    }

    private fun replaceField(fieldId: String, transform: (TemplateField) -> TemplateField) {
        _fields.value = _fields.value.map { if (it.id == fieldId) transform(it) else it }
    }

    /** 重排 sort_order，保证存进去的顺序与界面上的顺序永远一致 */
    private fun List<TemplateField>.reindexed(): List<TemplateField> =
        mapIndexed { index, field -> field.copy(sortOrder = index) }

    private fun defaultFirstField(): TemplateField = newTemplateField(
        templateId = templateId.orEmpty(),
        label = "正文",
        type = FieldType.MULTI,
        required = true,
        sortOrder = 0
    )
}
