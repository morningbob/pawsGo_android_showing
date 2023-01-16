package com.bitpunchlab.android.pawsgo.modelsRoom

import androidx.room.Embedded
import androidx.room.Relation


data class UserWithMessages (
    @Embedded
    val user : UserRoom,
    @Relation(
        parentColumn = "userID",
        entityColumn = "userCreatorID",
        entity = MessageRoom::class
    )
    var messages : List<MessageRoom>
)
/*
class BookWithCounts(book: java.awt.print.Book, counts: List<Count>) {
    @Embedded
    var book: java.awt.print.Book

    @Relation(parentColumn = "book_id", entityColumn = "book_id")
    var counts: List<Count>

    init {
        this.book = book
        this.counts = counts
    }
}

 */