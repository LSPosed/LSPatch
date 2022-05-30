package org.lsposed.lspatch.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.lsposed.lspatch.database.dao.ModuleDao
import org.lsposed.lspatch.database.dao.ScopeDao

import org.lsposed.lspatch.database.entity.Module
import org.lsposed.lspatch.database.entity.Scope

@Database(entities = [Module::class, Scope::class], version = 1)
abstract class LSPDatabase : RoomDatabase() {
    abstract fun moduleDao(): ModuleDao
    abstract fun scopeDao(): ScopeDao
}
