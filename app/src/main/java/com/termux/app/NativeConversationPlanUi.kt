package com.termux.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Tick02
import org.json.JSONArray
import org.json.JSONObject

internal fun isNativePlanActivity(content: String): Boolean =
    content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) || content.startsWith("PLAN_PANEL|")

/** Dedicated plan presentation kept outside the general message renderer. */
@Composable
internal fun NativePlanActivityMessage(
    message: NativeChatMessage,
    planJson: String,
    planExplanation: String,
    renderPlanText: @Composable (text: String, streaming: Boolean) -> Unit,
) {
    val language = LocalNativeLanguage.current
    val text = message.content
    if (text.startsWith(NATIVE_PROPOSED_PLAN_PREFIX)) {
        val planText = remember(text) { decodeNativeProposedPlan(text) }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HugeIcons.LeftToRightListBullet, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nativeText(language, "建议计划", "Proposed plan"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (message.streaming) nativeText(language, "正在整理计划…", "Drafting the plan...")
                            else nativeText(language, "计划已准备好", "Plan ready"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        )
                    }
                    if (message.streaming) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(HugeIcons.Tick02, null, Modifier.size(18.dp), tint = Color(0xFF5E8B68))
                }
                if (planText.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        renderPlanText(planText, message.streaming)
                    }
                }
            }
        }
        return
    }

    val parts = text.split('|')
    val completed = parts.getOrNull(1) == "complete"
    val count = parts.getOrNull(2)?.toIntOrNull() ?: 0
    var expanded by remember(message.id) { mutableStateOf(true) }
    val planSteps = remember(planJson) { parseNativePlanItems(planJson) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HugeIcons.LeftToRightListBullet, null, Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(nativeText(language, "计划", "Plan"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (completed) nativeText(
                        language,
                        "计划已准备好${if (count > 0) " · $count 步" else ""}",
                        "Plan ready${if (count > 0) " · $count steps" else ""}",
                    ) else nativeText(language, "正在准备计划…", "Preparing the plan…"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (completed) Icon(HugeIcons.Tick02, null, Modifier.size(18.dp))
            else CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
        }
        AnimatedVisibility(
            visible = expanded && (planSteps.isNotEmpty() || planExplanation.isNotBlank()),
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(180)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
        ) {
            Column(Modifier.padding(start = 42.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (planExplanation.isNotBlank()) {
                    renderPlanText(planExplanation, false)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f))
                }
                planSteps.forEachIndexed { index, step ->
                    val label = step.optString("step")
                        .ifBlank { step.optString("title") }
                        .ifBlank { step.optString("description") }
                    val status = step.optString("status", "pending")
                    if (label.isNotBlank()) Row(verticalAlignment = Alignment.Top) {
                        val mark = if (status == "completed") "✓" else "${index + 1}."
                        Text(
                            mark,
                            modifier = Modifier.width(24.dp),
                            color = if (status == "completed") Color(0xFF5E8B68) else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Box(Modifier.weight(1f)) { renderPlanText(label, false) }
                    }
                }
            }
        }
    }
}

internal fun parseNativePlanItems(raw: String): List<JSONObject> = runCatching {
    val array = JSONArray(raw)
    buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add) }
}.getOrDefault(emptyList())
