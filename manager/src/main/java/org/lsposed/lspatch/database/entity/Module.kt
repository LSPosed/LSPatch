package org.lsposed.lspatch.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Module(
    @PrimaryKey val pkgName: String,
    var apkPath: String
)
