package dev.anilbeesetti.nextplayer.core.remotesubs

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.remotesubs.service.OpenSubtitlesComSubtitlesService
import dev.anilbeesetti.nextplayer.core.remotesubs.service.SubtitlesService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
interface DataBindingModule {

    @Binds
    fun bindsSubtitlesService(
        subtitleService: OpenSubtitlesComSubtitlesService,
    ): SubtitlesService
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Singleton
    @Provides
    fun provideHttpClient() = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        if (BuildConfig.DEBUG) {
            install(Logging) {
                level = LogLevel.ALL
                logger = object : Logger {
                    override fun log(message: String) {
                        println(message)
                    }
                }
            }
        }
    }
}
