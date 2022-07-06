package org.lsposed.lspatch.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.lspApp
import java.io.File

object MyKeyStore {

    val file = File("${lspApp.filesDir}/keystore.bks")
    val tmpFile = File("${lspApp.filesDir}/keystore.bks.tmp")

    var useDefault by mutableStateOf(!file.exists())
        private set

    suspend fun reset() {
        withContext(Dispatchers.IO) {
            file.delete()
            Configs.keyStorePassword = "123456"
            Configs.keyStoreAlias = "key0"
            Configs.keyStoreAliasPassword = "123456"
            useDefault = true
        }
    }

    suspend fun setCustom(password: String, alias: String, aliasPassword: String) {
        withContext(Dispatchers.IO) {
            tmpFile.renameTo(file)
            Configs.keyStorePassword = password
            Configs.keyStoreAlias = alias
            Configs.keyStoreAliasPassword = aliasPassword
            useDefault = false
        }
    }
}
