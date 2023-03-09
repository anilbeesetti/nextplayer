package dev.anilbeesetti.libs.ffcodecs

class NativeLib {

    /**
     * A native method that is implemented by the 'ffcodecs' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'ffcodecs' library on application startup.
        init {
            System.loadLibrary("ffcodecs")
        }
    }
}