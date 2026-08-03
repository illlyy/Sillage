package com.termux.app

import org.json.JSONObject

/**
 * AskUserQuestion answer logic (pattern: claudecodeui AskUserQuestionPanel.tsx). Pure helpers
 * for option parsing, multi-select toggling, "Other" free-text merging and completion checks.
 * Selections are encoded as a comma-joined string in one `answers` slot so the existing
 * bridge contract stays unchanged.
 */
internal data class NativeUserInputOption(val label: String, val description: String = "")

internal fun nativeQuestionOptions(question: JSONObject): List<NativeUserInputOption> {
    val options = question.optJSONArray("options") ?: return emptyList()
    return buildList {
        for (index in 0 until options.length()) {
            val option = options.optJSONObject(index)
            if (option != null) {
                add(
                    NativeUserInputOption(
                        label = option.optString("label", option.optString("value", "")).trim(),
                        description = option.optString("description"),
                    ),
                )
            } else {
                options.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(NativeUserInputOption(it)) }
            }
        }
    }.filter { it.label.isNotBlank() }
}

internal fun nativeQuestionMultiSelect(question: JSONObject): Boolean =
    question.optBoolean("multiSelect", false)

/** "Other" free-text input is offered when the model asks for it or provides no options. */
internal fun nativeQuestionOtherAllowed(question: JSONObject): Boolean {
    val options = nativeQuestionOptions(question)
    return question.optBoolean("isOther", options.isEmpty())
}

internal fun nativeSelectedLabels(value: String): List<String> =
    value.split(",").map { it.trim() }.filter { it.isNotBlank() }

internal fun nativeOptionLabelSet(question: JSONObject): Set<String> =
    nativeQuestionOptions(question).map { it.label }.toSet()

/** Toggles one option in a multi-select; single-select replaces the whole value. */
internal fun nativeToggleOption(current: String, label: String, multi: Boolean): String {
    if (!multi) return label
    val selected = nativeSelectedLabels(current).toMutableList()
    if (label in selected) selected.remove(label) else selected.add(label)
    return selected.joinToString(",")
}

/** Part of the value that is free text, not a known option. */
internal fun nativeOtherPart(value: String, optionLabels: Set<String>): String =
    nativeSelectedLabels(value).filterNot { it in optionLabels }.joinToString(",")

/** Merges free text back into the value, keeping selected options intact. */
internal fun nativeApplyOtherValue(previous: String, optionLabels: Set<String>, other: String): String =
    (nativeSelectedLabels(previous).filter { it in optionLabels } + listOf(other.trim()))
        .filter { it.isNotBlank() }
        .joinToString(",")

/** A question is complete once it has any non-blank value (or is not required). */
internal fun nativeQuestionComplete(question: JSONObject, value: String): Boolean {
    if (!question.optBoolean("required", true)) return true
    return value.trim().isNotBlank()
}
