package org.lsposed.lspatch.config

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.Constants.PREFS_KEYSTORE_ALIAS
import org.lsposed.lspatch.Constants.PREFS_KEYSTORE_ALIAS_PASSWORD
import org.lsposed.lspatch.Constants.PREFS_KEYSTORE_PASSWORD
import org.lsposed.lspatch.lspApp
import java.io.File

object MyKeyStore {

    val file = File("${lspApp.filesDir}/keystore.bks")

    val tmpFile = File("${lspApp.filesDir}/keystore.bks.tmp")

    val password: String
        get() = lspApp.prefs.getString("keystore_password", "123456")!!

    val alias: String
        get() = lspApp.prefs.getString("keystore_alias", "key0")!!

    val aliasPassword: String
        get() = lspApp.prefs.getString("keystore_alias_password", "123456")!!

    private var mUseDefault by mutableStateOf(!file.exists())
    val useDefault by derivedStateOf { mUseDefault }

    suspend fun reset() {
        withContext(Dispatchers.IO) {
            file.delete()
            lspApp.prefs.edit()
                .putString(PREFS_KEYSTORE_PASSWORD, "123456")
                .putString(PREFS_KEYSTORE_ALIAS, "key0")
                .putString(PREFS_KEYSTORE_ALIAS_PASSWORD, "123456")
                .apply()
            mUseDefault = true
        }
    }

    suspend fun setCustom(password: String, alias: String, aliasPassword: String) {
        withContext(Dispatchers.IO) {
            tmpFile.renameTo(file)
            lspApp.prefs.edit()
                .putString(PREFS_KEYSTORE_PASSWORD, password)
                .putString(PREFS_KEYSTORE_ALIAS, alias)
                .putString(PREFS_KEYSTORE_ALIAS_PASSWORD, aliasPassword)
                .apply()
            mUseDefault = false
        }
    }
}
