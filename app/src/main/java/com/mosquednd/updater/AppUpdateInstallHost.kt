package com.mosquednd.updater

import java.io.File

interface AppUpdateInstallHost {
    fun installUpdate(apkFile: File)
}
