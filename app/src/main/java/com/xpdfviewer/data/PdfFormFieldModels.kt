package com.xpdfviewer.data

/**
 * Types of interactive form fields recognized by the form filler engine.
 */
enum class FormFieldType {
    TEXT_INPUT,
    CHECKBOX,
    RADIO_GROUP,
    DROPDOWN_CHOICE
}

/**
 * Key-value option for choices and radio button groups.
 */
data class FormFieldOption(
    val exportValue: String,
    val displayLabel: String
)

/**
 * Represents a single interactive form field parsed from the PDF AcroForm.
 *
 * @param id Unique identifier (typically fully qualified name in AcroForm tree).
 * @param pageIndex 0-based page index where this field's widget annotation resides.
 * @param bounds Normalized coordinate rectangle (0f..1f) relative to the page bounds.
 * @param type Classification of the field widget.
 * @param fieldName Partial or short field identifier.
 * @param alternateName Tooltip, description, or descriptive label if embedded in the PDF.
 * @param isReadOnly True if marked read-only in the PDF dictionary.
 * @param isRequired True if marked required in the PDF dictionary.
 * @param isMultiline True if this is a multi-line text field.
 * @param isPassword True if this field masks input with bullet points.
 * @param currentValue String value entered or selected by user.
 * @param isChecked Checked state if field is a checkbox.
 * @param radioOptions Available radio choices if type is RADIO_GROUP.
 * @param choiceOptions Available options if type is DROPDOWN_CHOICE.
 */
data class FormFieldItem(
    val id: String,
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val type: FormFieldType,
    val fieldName: String,
    val alternateName: String? = null,
    val isReadOnly: Boolean = false,
    val isRequired: Boolean = false,
    val isMultiline: Boolean = false,
    val isPassword: Boolean = false,
    val currentValue: String = "",
    val isChecked: Boolean = false,
    val radioOptions: List<FormFieldOption> = emptyList(),
    val choiceOptions: List<FormFieldOption> = emptyList()
) {
    val displayTitle: String
        get() = alternateName?.takeIf { it.isNotBlank() } ?: fieldName.ifBlank { "Field on Page ${pageIndex + 1}" }

    val hasValue: Boolean
        get() = if (type == FormFieldType.CHECKBOX) isChecked else currentValue.isNotBlank()
}

/**
 * Encapsulates complete state for interactive form fields in the active document.
 */
data class DocumentFormState(
    val hasAcroForm: Boolean = false,
    val isFormEditable: Boolean = true,
    val fields: List<FormFieldItem> = emptyList(),
    val activeFieldId: String? = null,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val showFormSheet: Boolean = false,
    val showSaveChoiceDialog: Boolean = false,
    val editingField: FormFieldItem? = null
) {
    val totalFieldCount: Int
        get() = fields.size

    val filledFieldCount: Int
        get() = fields.count { it.hasValue }

    val completionPercentage: Int
        get() = if (totalFieldCount > 0) ((filledFieldCount.toFloat() / totalFieldCount.toFloat()) * 100).toInt() else 0

    val activeField: FormFieldItem?
        get() = fields.firstOrNull { it.id == activeFieldId }
}

