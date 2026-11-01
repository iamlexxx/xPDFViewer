package com.xpdfviewer.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xpdfviewer.data.FormFieldItem
import com.xpdfviewer.data.FormFieldType

@Composable
fun EditFormFieldDialog(
    field: FormFieldItem,
    onDismiss: () -> Unit,
    onSaveValue: (String) -> Unit
) {
    var textValue by remember(field.id) { mutableStateOf(field.currentValue) }
    var selectedOption by remember(field.id) { mutableStateOf(field.currentValue) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (field.type) {
                            FormFieldType.TEXT_INPUT -> Icons.Default.EditNote
                            FormFieldType.RADIO_GROUP -> Icons.Default.RadioButtonChecked
                            FormFieldType.DROPDOWN_CHOICE -> Icons.AutoMirrored.Filled.List
                            FormFieldType.CHECKBOX -> Icons.Default.EditNote
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = field.displayTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Page ${field.pageIndex + 1}${if (field.isRequired) " • Required" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Controls based on type
                when (field.type) {
                    FormFieldType.TEXT_INPUT -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("Field Value") },
                            placeholder = { Text("Enter text") },
                            singleLine = !field.isMultiline,
                            maxLines = if (field.isMultiline) 5 else 1,
                            visualTransformation = if (field.isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    FormFieldType.RADIO_GROUP -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            field.radioOptions.forEach { opt ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedOption = opt.exportValue }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedOption == opt.exportValue,
                                        onClick = { selectedOption = opt.exportValue }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            field.choiceOptions.forEach { opt ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedOption == opt.exportValue) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedOption = opt.exportValue }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = opt.displayLabel,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (selectedOption == opt.exportValue) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    FormFieldType.CHECKBOX -> {
                        // Checkboxes are toggled directly, but if opened in dialog:
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Checkbox is ${if (field.isChecked) "Checked" else "Unchecked"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val finalVal = when (field.type) {
                                FormFieldType.TEXT_INPUT -> textValue
                                FormFieldType.RADIO_GROUP, FormFieldType.DROPDOWN_CHOICE -> selectedOption
                                FormFieldType.CHECKBOX -> if (field.isChecked) "Yes" else "No"
                            }
                            onSaveValue(finalVal)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
