package com.xpdfviewer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun JumpToPageDialog(
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(currentPage.toString()) }
    var isError by remember { mutableStateOf(false) }

    val handleConfirm = {
        val page = textValue.toIntOrNull()
        if (page != null && page in 1..pageCount) {
            onJump(page)
            onDismiss()
        } else {
            isError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Jump to Page") },
        text = {
            Column {
                Text(text = "Enter a page number between 1 and $pageCount:")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it.filter { char -> char.isDigit() }
                        isError = false
                    },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(text = "Must be between 1 and $pageCount") }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { handleConfirm() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = handleConfirm) {
                Text(text = "Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}
