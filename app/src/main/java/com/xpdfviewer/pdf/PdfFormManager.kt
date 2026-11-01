package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.xpdfviewer.data.FormFieldItem
import com.xpdfviewer.data.FormFieldOption
import com.xpdfviewer.data.FormFieldType
import com.xpdfviewer.data.NormalizedRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfFormManager {

    /**
     * Inspects a document for an AcroForm and parses all interactive form fields.
     */
    suspend fun extractFormFields(
        context: Context,
        uri: Uri
    ): List<FormFieldItem> = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        var doc: PDDocument? = null
        try {
            val inStream = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
            doc = PDDocument.load(inStream)

            val acroForm: PDAcroForm = doc.documentCatalog.acroForm ?: return@withContext emptyList()

            // Pre-index widget annotations to page indices for accurate positioning
            val widgetPageMap = HashMap<PDAnnotationWidget, Int>()
            val totalPages = doc.numberOfPages
            for (pIdx in 0 until totalPages) {
                val page = doc.getPage(pIdx)
                for (annot in page.annotations) {
                    if (annot is PDAnnotationWidget) {
                        widgetPageMap[annot] = pIdx
                    }
                }
            }

            val extracted = mutableListOf<FormFieldItem>()
            collectFields(acroForm.fields, doc, widgetPageMap, extracted)
            extracted
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            runCatching { doc?.close() }
        }
    }

    private fun collectFields(
        fields: List<PDField>,
        doc: PDDocument,
        widgetPageMap: Map<PDAnnotationWidget, Int>,
        result: MutableList<FormFieldItem>
    ) {
        for (field in fields) {
            if (field is PDNonTerminalField) {
                collectFields(field.children, doc, widgetPageMap, result)
            } else {
                parseTerminalField(field, doc, widgetPageMap, result)
            }
        }
    }

    private fun parseTerminalField(
        field: PDField,
        doc: PDDocument,
        widgetPageMap: Map<PDAnnotationWidget, Int>,
        result: MutableList<FormFieldItem>
    ) {
        val widgets = field.widgets
        if (widgets.isNullOrEmpty()) return

        for ((wIdx, widget) in widgets.withIndex()) {
            val pageIdx = widgetPageMap[widget] ?: resolveWidgetPage(widget, doc) ?: 0
            val page = doc.getPage(pageIdx)
            val pageBox = page.cropBox ?: page.mediaBox

            val rect = widget.rectangle ?: continue
            if (pageBox.width <= 0 || pageBox.height <= 0) continue

            val left = ((rect.lowerLeftX - pageBox.lowerLeftX) / pageBox.width).coerceIn(0f, 1f)
            val top = ((pageBox.upperRightY - rect.upperRightY) / pageBox.height).coerceIn(0f, 1f)
            val right = ((rect.upperRightX - pageBox.lowerLeftX) / pageBox.width).coerceIn(0f, 1f)
            val bottom = ((pageBox.upperRightY - rect.lowerLeftY) / pageBox.height).coerceIn(0f, 1f)

            val normRect = NormalizedRect(
                left = minOf(left, right),
                top = minOf(top, bottom),
                right = maxOf(left, right),
                bottom = maxOf(top, bottom)
            )

            val fieldId = if (widgets.size > 1) "${field.fullyQualifiedName}#$wIdx" else field.fullyQualifiedName
            val isReadOnly = field.isReadOnly
            val isRequired = field.isRequired
            val alternateName = field.alternateFieldName

            when (field) {
                is PDTextField -> {
                    result.add(
                        FormFieldItem(
                            id = fieldId,
                            pageIndex = pageIdx,
                            bounds = normRect,
                            type = FormFieldType.TEXT_INPUT,
                            fieldName = field.partialName ?: field.fullyQualifiedName,
                            alternateName = alternateName,
                            isReadOnly = isReadOnly,
                            isRequired = isRequired,
                            isMultiline = field.isMultiline,
                            isPassword = field.isPassword,
                            currentValue = field.value ?: "",
                            isChecked = false
                        )
                    )
                }
                is PDCheckBox -> {
                    result.add(
                        FormFieldItem(
                            id = fieldId,
                            pageIndex = pageIdx,
                            bounds = normRect,
                            type = FormFieldType.CHECKBOX,
                            fieldName = field.partialName ?: field.fullyQualifiedName,
                            alternateName = alternateName,
                            isReadOnly = isReadOnly,
                            isRequired = isRequired,
                            currentValue = if (field.isChecked) "Yes" else "No",
                            isChecked = field.isChecked
                        )
                    )
                }
                is PDRadioButton -> {
                    val exportVals = runCatching { field.exportValues }.getOrNull() ?: emptyList()
                    val options = exportVals.map { FormFieldOption(it, it) }
                    result.add(
                        FormFieldItem(
                            id = fieldId,
                            pageIndex = pageIdx,
                            bounds = normRect,
                            type = FormFieldType.RADIO_GROUP,
                            fieldName = field.partialName ?: field.fullyQualifiedName,
                            alternateName = alternateName,
                            isReadOnly = isReadOnly,
                            isRequired = isRequired,
                            currentValue = field.value ?: "",
                            radioOptions = options
                        )
                    )
                }
                is PDChoice -> {
                    val opts = runCatching { field.options }.getOrNull() ?: emptyList()
                    val options = opts.map { FormFieldOption(it, it) }
                    val currentVal = runCatching { field.value?.firstOrNull() }.getOrNull() ?: ""
                    result.add(
                        FormFieldItem(
                            id = fieldId,
                            pageIndex = pageIdx,
                            bounds = normRect,
                            type = FormFieldType.DROPDOWN_CHOICE,
                            fieldName = field.partialName ?: field.fullyQualifiedName,
                            alternateName = alternateName,
                            isReadOnly = isReadOnly,
                            isRequired = isRequired,
                            currentValue = currentVal,
                            choiceOptions = options
                        )
                    )
                }
                else -> {
                    // Ignore unsupported or custom annotation fields
                }
            }
        }
    }

    private fun resolveWidgetPage(widget: PDAnnotationWidget, doc: PDDocument): Int? {
        val widgetPage = widget.page ?: return null
        for (i in 0 until doc.numberOfPages) {
            if (doc.getPage(i) == widgetPage) return i
        }
        return null
    }

    /**
     * Writes updated form field values to [destinationUri]. If [flatten] is true,
     * burns the appearances into the page content streams permanently and strips the AcroForm.
     */
    suspend fun saveFormDocument(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        fieldItems: List<FormFieldItem>,
        flatten: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        val tempFile = File(context.cacheDir, "xpdf_form_save_${System.currentTimeMillis()}.pdf")
        var doc: PDDocument? = null
        try {
            val inStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext false
            doc = PDDocument.load(inStream)

            val acroForm = doc.documentCatalog.acroForm ?: return@withContext false

            for (item in fieldItems) {
                val cleanId = item.id.substringBefore("#")
                val field = acroForm.getField(cleanId) ?: continue
                try {
                    when (field) {
                        is PDTextField -> {
                            field.value = item.currentValue
                        }
                        is PDCheckBox -> {
                            if (item.isChecked) {
                                field.check()
                            } else {
                                field.unCheck()
                            }
                        }
                        is PDRadioButton -> {
                            if (item.currentValue.isNotBlank()) {
                                field.value = item.currentValue
                            }
                        }
                        is PDChoice -> {
                            if (item.currentValue.isNotBlank()) {
                                field.value = listOf(item.currentValue)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip individual unassignable field to prevent complete save failure
                    e.printStackTrace()
                }
            }

            if (flatten) {
                try {
                    acroForm.flatten()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            FileOutputStream(tempFile).use { fos ->
                doc.save(fos)
            }

            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { outStream ->
                tempFile.inputStream().use { fileIn ->
                    fileIn.copyTo(outStream)
                }
            } ?: return@withContext false

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            runCatching { doc?.close() }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}

