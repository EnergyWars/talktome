package com.wafflehq.uikit.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import com.wafflehq.uikit.theme.AppRadius
import com.wafflehq.uikit.theme.AppRole
import com.wafflehq.uikit.theme.AppTheme

enum class TextFieldVariant {
    Outlined, Filled
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    role: AppRole,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
) {
    val t = AppTheme.tokens.textField
    val r = t.forRole(role)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        singleLine = singleLine,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(AppRadius.textField),
        colors = TextFieldDefaults.colors(
            focusedContainerColor   = r.background,
            unfocusedContainerColor = r.background,
            disabledContainerColor  = t.disabledBackground,
            focusedTextColor        = r.content,
            unfocusedTextColor      = r.content,
            disabledTextColor       = t.disabledContent,
            focusedLabelColor       = r.labelFocused,
            unfocusedLabelColor     = r.labelUnfocused,
            focusedIndicatorColor   = r.borderFocused,
            unfocusedIndicatorColor = r.borderUnfocused,
            errorIndicatorColor     = r.errorBorder,
            errorLabelColor         = r.errorLabel,
        ),
    )
}
