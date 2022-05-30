package org.lsposed.lspatch.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import org.lsposed.lspatch.database.entity.Module
import org.lsposed.lspatch.database.entity.Scope

@Dao
interface ScopeDao {

    @Query("SELECT * FROM module INNER JOIN scope ON module.pkgName = scope.modulePkgName WHERE scope.appPkgName = :appPkgName")
    suspend fun getModulesForApp(appPkgName: String): List<Module>

    @Insert
    suspend fun insert(scope: Scope)

    @Delete
    suspend fun delete(scope: Scope)
}
