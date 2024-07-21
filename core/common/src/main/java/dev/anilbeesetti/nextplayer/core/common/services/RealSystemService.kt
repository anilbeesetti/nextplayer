package dev.anilbeesetti.nextplayer.core.common.services

import android.app.Activity
import android.content.Context
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class RealSystemService @Inject constructor(
    @ApplicationContext private val applicationContext: Context
) : SystemService {

    private lateinit var activity: Activity

    override fun initialize(activity: Activity) {
        this.activity = activity
    }

    override fun getString(id: Int): String {
        return applicationContext.getString(id)
    }

    override fun getString(id: Int, vararg formatArgs: Any): String {
        return applicationContext.getString(id, *formatArgs)
    }

    override fun showToast(message: String, showLong: Boolean) {
        Toast.makeText(activity, message, if (showLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    override fun versionName(): String {
        return applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
    }
}