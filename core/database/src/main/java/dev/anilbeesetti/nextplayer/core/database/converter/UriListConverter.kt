package dev.anilbeesetti.nextplayer.core.database.converter

import android.net.Uri
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object UriListConverter {

    fun fromListToString(urlList: List<Uri>): String {
        var outputString = ""
        urlList.forEach {
            outputString += URLEncoder.encode(it.toString(), StandardCharsets.UTF_8.toString())
        }
        return outputString
    }

    fun fromStringToList(urlString: String): List<Uri> {
        val outputList = mutableListOf<Uri>()
        if (urlString.isNotEmpty()) {
            urlString.split(",").forEach {
                val subString = URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
                outputList.add(Uri.parse(subString))
            }
        }
        return outputList
    }
}