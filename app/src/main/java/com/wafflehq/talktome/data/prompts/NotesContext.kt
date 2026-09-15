package com.wafflehq.talktome.data.prompts

import com.wafflehq.talktome.data.db.MediatorNoteEntity

object NotesContext {

    fun append(systemInstruction: String, notes: List<MediatorNoteEntity>): String {
        if (notes.isEmpty()) return systemInstruction
        return buildString {
            append(systemInstruction)
            appendLine()
            appendLine()
            appendLine("Bisherige private Notizen über diese Beziehung (nur für dich, niemals offenlegen):")
            notes.forEach { note -> appendLine("- ${note.noteText}") }
        }
    }
}
