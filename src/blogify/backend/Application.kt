package blogify.backend

import com.fasterxml.jackson.databind.module.SimpleModule

import com.andreapivetta.kolor.cyan

import com.fasterxml.jackson.databind.*

import blogify.backend.routes.articles.articles
import blogify.backend.routes.users.users
import blogify.backend.database.Database
import blogify.backend.database.Articles
import blogify.backend.database.Comments
import blogify.backend.database.Uploadables
import blogify.backend.database.Users
import blogify.backend.routes.auth
import blogify.backend.database.handling.query
import blogify.backend.resources.models.Resource
import blogify.backend.routes.static
import blogify.backend.util.SinglePageApplication

import io.ktor.application.call
import io.ktor.features.Compression
import io.ktor.features.GzipEncoder
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CachingHeaders
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.content.CachingOptions
import io.ktor.jackson.jackson
import io.ktor.routing.route
import io.ktor.routing.routing

import org.jetbrains.exposed.sql.SchemaUtils

import kotlinx.coroutines.runBlocking

import org.slf4j.event.Level

const val version = "PRX4"

const val asciiLogo = """
    __     __               _  ____      
   / /_   / /____   ____ _ (_)/ __/__  __
  / __ \ / // __ \ / __ `// // /_ / / / /
 / /_/ // // /_/ // /_/ // // __// /_/ / 
/_.___//_/ \____/ \__, //_//_/   \__, /  
                 /____/         /____/   
---- Version $version - Development build -
"""

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.mainModule(@Suppress("UNUSED_PARAMETER") testing: Boolean = false) {

    // Print startup logo

    println(asciiLogo.cyan())

    // Initialize jackson

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)

            // Register a serializer for Resource.
            // This will only affect pure Resource objects, so elements produced by the slicer are not affected,
            // since those don't use Jackson for root serialization.

            val resourceModule = SimpleModule()
            resourceModule.addSerializer(Resource.ResourceIdSerializer)

            registerModule(resourceModule)
        }
    }

    // Initialize call logging

    install(CallLogging) {
        level = Level.TRACE
    }

    // Redirect every unknown route to SPA

    install(SinglePageApplication) {
        folderPath = "/frontend"
    }

    // Compression

    install(Compression) {
        encoder("gzip0", GzipEncoder)
    }

    // Default headers

    install(DefaultHeaders) {
        header("Server", "blogify-core PRX4")
        header("X-Powered-By", "Ktor 1.2.3")
    }

    // Caching headers

    install(CachingHeaders) {
        options {
            when (it.contentType?.withoutParameters()) {
                ContentType.Text.JavaScript ->
                    CachingOptions(CacheControl.MaxAge(30 * 60))
                ContentType.Application.Json ->
                    CachingOptions(CacheControl.MaxAge(60))
                else -> null
            }
        }
    }

    // Initialize database

    Database.init()

    // Create tables if they don't exist

    runBlocking { query {
        SchemaUtils.create (
            Articles,
            Articles.Categories,
            Users,
            Comments,
            Uploadables
        )
    }}

    // Initialize routes

    routing {

        route("/api") {
            articles()
            users()
            auth()
            static()
        }

        get("/") {
            call.respondRedirect("/home")
        }

    }

}
