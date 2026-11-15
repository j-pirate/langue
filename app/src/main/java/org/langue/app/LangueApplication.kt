package org.langue.app

import android.app.Application
import org.langue.app.data.AppRepository

class LangueApplication : Application() {
    val repository by lazy { AppRepository(this) }
}
