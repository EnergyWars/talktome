package com.wafflehq.talktome.data.db

import androidx.room.TypeConverter

class TalkToMeTypeConverters {

    @TypeConverter
    fun fromMediatorRole(role: MediatorAgentRole): String = role.name

    @TypeConverter
    fun toMediatorRole(value: String): MediatorAgentRole = MediatorAgentRole.valueOf(value)

    @TypeConverter
    fun fromOutgoingStatus(status: OutgoingMessageStatus): String = status.name

    @TypeConverter
    fun toOutgoingStatus(value: String): OutgoingMessageStatus = OutgoingMessageStatus.valueOf(value)

    @TypeConverter
    fun fromInboxStatus(status: InboxMessageStatus): String = status.name

    @TypeConverter
    fun toInboxStatus(value: String): InboxMessageStatus = InboxMessageStatus.valueOf(value)

    @TypeConverter
    fun fromNegotiationTurnSender(sender: NegotiationTurnSender): String = sender.name

    @TypeConverter
    fun toNegotiationTurnSender(value: String): NegotiationTurnSender = NegotiationTurnSender.valueOf(value)
}
