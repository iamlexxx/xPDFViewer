package com.xpdfviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpdfviewer.data.DocumentFormState
import com.xpdfviewer.data.FormFieldItem
import com.xpdfviewer.data.FormFieldType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFieldsBottomSheet(
    formState: DocumentFormState,
    onDismiss: () -> Unit,
    onUpdateTextField: (String, String) -> Unit,
    onToggleCheckbox: (String, Boolean) -> Unit,
    onSelectRadio: (String, String) -> Unit,
    onSelectChoice: (String, String) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onClearAllFields: () -> Unit,
    onOpenSaveDialog: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val fieldsByPage = remember(formState.fields) {
        formState.fields.groupBy { it.pageIndex }.toSortedMap()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AssignmentTurnedIn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Form Filling Assistant",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formState.filledFieldCount} of ${formState.totalFieldCount} fields filled (${formState.completionPercentage}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (formState.filledFieldCount > 0) {
                    TextButton(onClick = onClearAllFields) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close sheet")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { (formState.completionPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Field List grouped by Page
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                fieldsByPage.forEach { (pageIdx, fieldsOnPage) ->
                    item(key = "header_$pageIdx") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Page ${pageIdx + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${fieldsOnPage.size} ${if (fieldsOnPage.size == 1) "field" else "fields"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    onJumpToPage(pageIdx + 1)
                                    onDismiss()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Go to Page", style = MaterialTheme.typography.labelSmall)
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    items(fieldsOnPage, key = { it.id }) { field ->
                        FormFieldCard(
                            field = field,
                            onUpdateText = { onUpdateTextField(field.id, it) },
                            onToggleCheck = { onToggleCheckbox(field.id, it) },
                            onSelectRadio = { onSelectRadio(field.id, it) },
                            onSelectChoice = { onSelectChoice(field.id, it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Save Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }

                Button(
                    onClick = {
                        onDismiss()
                        onOpenSaveDialog()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Form", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FormFieldCard(
    field: FormFieldItem,
    onUpdateText: (String) -> Unit,
    onToggleCheck: (Boolean) -> Unit,
    onSelectRadio: (String) -> Unit,
    onSelectChoice: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Label & Type badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = field.displayTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (field.isRequired) {
                    Text(
                        text = "Required",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (field.type) {
                FormFieldType.TEXT_INPUT -> {
                    OutlinedTextField(
                        value = field.currentValue,
                        onValueChange = onUpdateText,
                        placeholder = { Text("Enter text...") },
                        singleLine = !field.isMultiline,
                        maxLines = if (field.isMultiline) 4 else 1,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                FormFieldType.CHECKBOX -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCheck(!field.isChecked) }
                    ) {
                        Checkbox(
                            checked = field.isChecked,
                            onCheckedChange = onToggleCheck
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (field.isChecked) "Checked" else "Not checked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                FormFieldType.RADIO_GROUP -> {
                    Column {
                        field.radioOptions.forEach { opt ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectRadio(opt.exportValue) }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = field.currentValue == opt.exportValue,
                                    onClick = { onSelectRadio(opt.exportValue) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = opt.displayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                FormFieldType.DROPDOWN_CHOICE -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        field.choiceOptions.take(4).forEach { opt ->
                            val isSelected = field.currentValue == opt.exportValue
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .clickable { onSelectChoice(opt.exportValue) }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = opt.displayLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
