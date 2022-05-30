package org.lsposed.lspatch.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["appPkgName", "modulePkgName"],
    foreignKeys = [ForeignKey(entity = Module::class, parentColumns = ["pkgName"], childColumns = ["modulePkgName"], onDelete = ForeignKey.CASCADE)]
)
data class Scope(
    val appPkgName: String,
    val modulePkgName: String
)
