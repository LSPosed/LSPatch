package org.lsposed.lspatch.config

import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.database.LSPDatabase
import org.lsposed.lspatch.database.entity.Module
import org.lsposed.lspatch.database.entity.Scope
import org.lsposed.lspatch.lspApp
import org.lsposed.lspatch.util.ModuleLoader

object ConfigManager {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)

    private val db: LSPDatabase = Room.databaseBuilder(
        lspApp, LSPDatabase::class.java, "modules_config.db"
    ).build()

    private val moduleDao = db.moduleDao()
    private val scopeDao = db.scopeDao()

    private val loadedModules = mutableMapOf<Module, org.lsposed.lspd.models.Module>()

    suspend fun updateModules(newModules: Map<String, String>) =
        withContext(dispatcher) {
            for (module in moduleDao.getAll()) {
                val apkPath = newModules[module.pkgName]
                if (apkPath == null) {
                    moduleDao.delete(module)
                    loadedModules.remove(module)
                } else if (module.apkPath != apkPath) {
                    module.apkPath = apkPath
                    loadedModules.remove(module)
                }
            }
            for ((pkgName, apkPath) in newModules) {
                moduleDao.insert(Module(pkgName, apkPath))
            }
        }

    suspend fun activateModule(pkgName: String, module: Module) =
        withContext(dispatcher) {
            scopeDao.insert(Scope(appPkgName = pkgName, modulePkgName = module.pkgName))
        }

    suspend fun deactivateModule(pkgName: String, module: Module) =
        withContext(dispatcher) {
            scopeDao.delete(Scope(appPkgName = pkgName, modulePkgName = module.pkgName))
        }

    suspend fun getModulesForApp(pkgName: String): List<Module> =
        withContext(dispatcher) {
            return@withContext scopeDao.getModulesForApp(pkgName)
        }

    suspend fun getModuleFilesForApp(pkgName: String): List<org.lsposed.lspd.models.Module> =
        withContext(dispatcher) {
            val modules = scopeDao.getModulesForApp(pkgName)
            return@withContext modules.map {
                loadedModules.getOrPut(it) {
                    org.lsposed.lspd.models.Module().apply {
                        packageName = it.pkgName
                        apkPath = it.apkPath
                        file = ModuleLoader.loadModule(it.apkPath)
                    }
                }
            }
        }
}
