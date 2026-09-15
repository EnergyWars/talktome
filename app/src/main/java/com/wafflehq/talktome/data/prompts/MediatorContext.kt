package com.wafflehq.talktome.data.prompts

import kotlinx.serialization.Serializable

data class MediatorContext(
    val selfDescription: String = "",
    val partnerDescription: String = "",
) {
    val isEmpty: Boolean get() = selfDescription.isBlank() && partnerDescription.isBlank()
}

@Serializable
data class FriendOpinion(val label: String, val opinion: String)
