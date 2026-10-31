package app.nacre

import android.app.Application
import app.nacre.data.*
import kotlinx.coroutines.*

class NacreApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { LibraryDb.open(this) }
    val preferences by lazy { Preferences(this) }
    val library by lazy { LibraryRepository(this, database.library(), preferences, applicationScope) }
}
