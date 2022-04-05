package org.lsposed.lspatch.config

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.LSPApplication.Companion.appContext
import org.lsposed.lspatch.LSPApplication.Companion.prefs
import java.io.File

object MyKeyStore {

    val file = File("${appContext.filesDir}/keystore.bks")

    val tmpFile = File("${appContext.filesDir}/keystore.bks.tmp")

    val password: String
        get() = prefs.getString("keystore_password", "123456")!!

    val alias: String
        get() = prefs.getString("keystore_alias", "key0")!!

    val aliasPassword: String
        get() = prefs.getString("keystore_alias_password", "123456")!!

    private var mUseDefault by mutableStateOf(!file.exists())
    val useDefault by derivedStateOf { mUseDefault }

    suspend fun reset() {
        withContext(Dispatchers.IO) {
            file.delete()
            prefs.edit()
                .putString("keystore_password", "123456")
                .putString("keystore_alias", "key0")
                .putString("keystore_alias_password", "123456")
                .apply()
            mUseDefault = true
        }
    }

    suspend fun setCustom(password: String, alias: String, aliasPassword: String) {
        withContext(Dispatchers.IO) {
            tmpFile.renameTo(file)
            prefs.edit()
                .putString("keystore_password", password)
                .putString("keystore_alias", alias)
                .putString("keystore_alias_password", aliasPassword)
                .apply()
            mUseDefault = false
        }
    }
}
