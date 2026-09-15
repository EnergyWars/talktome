package com.wafflehq.talktome.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PartnerEntity::class,
        ProfileEntity::class,
        MediatorNoteEntity::class,
        OutgoingMessageEntity::class,
        InboxMessageEntity::class,
        NegotiationTurnEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(TalkToMeTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun partnerDao(): PartnerDao
    abstract fun profileDao(): ProfileDao
    abstract fun mediatorNoteDao(): MediatorNoteDao
    abstract fun outgoingMessageDao(): OutgoingMessageDao
    abstract fun inboxMessageDao(): InboxMessageDao
    abstract fun negotiationTurnDao(): NegotiationTurnDao
}
