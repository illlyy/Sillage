package com.termux.app;

import android.app.Activity;

/**
 * Common runtime surface shared by every agent backend (Codex app-server, Claude Code).
 *
 * A bridge owns a child process, decodes its stream into normalized {@code function}/{@code value}
 * events (the same namespace CodexChatActivity already dispatches) and forwards outbound UI
 * actions back into the protocol. Keeping this surface identical for both backends means the
 * chat host, reducers and Compose UI never need to know which CLI is underneath.
 *
 * Abstract class (not interface) on purpose: implementation methods stay package-private,
 * matching the module's existing convention for bridge internals.
 */
abstract class NativeBackendBridge {

    /** Listener contract for normalized events; mirrors CodexAppServerBridge.EventListener. */
    interface EventListener {
        void onEvent(String function, String value);
        void onHistoryPrepared(String threadId, int generation, NativeHistorySnapshot snapshot);
    }

    abstract void rebind(Activity activity, EventListener listener);

    abstract void detach(EventListener listener);

    abstract void stop();

    abstract boolean isRunning();

    abstract String currentVisibleThreadId();

    abstract void refreshMcpStatus();

    // ---- turn control ----

    abstract void sendMessage(String text, String model, String effort);

    abstract void sendMessage(String text, String model, String effort, String attachmentsJson);

    abstract void sendMessage(String text, String model, String effort, String attachmentsJson, String collaborationMode);

    abstract void sendMessage(String text, String model, String effort, String attachmentsJson, String collaborationMode, String skillsJson);

    abstract void editTurn(String text, String model, String effort, int rollbackTurns);

    abstract int steerMessage(String text, String attachmentsJson, String skillsJson);

    abstract void interruptCurrentTurn();

    /** Client-side continuation hint (queued follow-up / goal retry); backend specific. */
    abstract void setNativeContinuationPending(String sourceThreadId, boolean pending);

    // ---- conversation/session ----

    abstract int resumeConversation(String resumeThreadId);

    abstract int restoreRetainedConversation(String resumeThreadId);

    abstract void newConversationAtCwd(String requestedCwd);

    abstract void loadSubagentHistory(String subagentThreadId, int generation);

    abstract void loadSkills();

    // ---- goal (client-side for backends without native goals) ----

    abstract void setThreadGoal(String objective);

    abstract void getThreadGoal();

    abstract void setThreadGoalStatus(String status);

    abstract void clearThreadGoal();

    // ---- compaction / approval / user input ----

    abstract void compactThread();

    abstract void compactThread(String nativeRequestId);

    abstract void respondUserInput(String rawRequest, String answersJson);

    abstract void respondApprovalRequest(String rawRequest, String decision);
}
