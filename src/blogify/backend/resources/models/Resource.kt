package blogify.backend.resources.models

import blogify.backend.appContext
import blogify.backend.pipelines.wrapping.RequestContext
import blogify.backend.resources.reflect.models.Mapped

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.http.Parameters
import io.ktor.request.ApplicationRequest
import io.ktor.response.ApplicationResponse
import io.ktor.util.Attributes
import kotlinx.coroutines.GlobalScope

import com.fasterxml.jackson.annotation.ObjectIdGenerator
import com.fasterxml.jackson.annotation.ObjectIdResolver
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

import java.lang.IllegalStateException
import java.util.*

open class Resource(open val uuid: UUID = UUID.randomUUID()) : Mapped() {

    object ObjectResolver : ObjectIdResolver {

        object FakeApplicationCall : ApplicationCall {

            override val application: Application
                get() = TODO("cannot be accessed in a fake ApplicationCall")
            override val attributes: Attributes
                get() = TODO("cannot be accessed in a fake ApplicationCall")
            override val parameters: Parameters
                get() = TODO("cannot be accessed in a fake ApplicationCall")
            override val request: ApplicationRequest
                get() = TODO("cannot be accessed in a fake ApplicationCall")
            override val response: ApplicationResponse
                get() = TODO("cannot be accessed in a fake ApplicationCall")

        }

        val FakeRequestContext = RequestContext(appContext, GlobalScope, FakeApplicationCall)

        override fun resolveId(id: ObjectIdGenerator.IdKey?): Any? {

            val uuid = id?.key as UUID

            fun genException(scope: Class<*>, ex: Exception)
                    = IllegalStateException("exception during resource (type: ${scope.simpleName}) resolve with UUID $uuid : ${ex.message}", ex)

            return runBlocking {

                try {
                    @Suppress("UNCHECKED_CAST")
                    FakeRequestContext.repository(id.scope.kotlin as KClass<Resource>).get(id = uuid).get()
                } catch (e: Exception) {
                    throw genException(id.scope, e)
                }

            }

        }

        override fun newForDeserialization(context: Any?): ObjectIdResolver {
           return this
        }

        override fun bindItem(id: ObjectIdGenerator.IdKey?, pojo: Any?) {
            return
        }

        override fun canUseFor(resolverType: ObjectIdResolver?): Boolean {
            return resolverType!!::class == this::class
        }

    }

    /**
     * Used to serialize [resources][Resource] by only printing their [uuid][Resource.uuid].
     */
    object ResourceIdSerializer : StdSerializer<Resource>(Resource::class.java) {

        override fun serialize(value: Resource, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(value.uuid.toString())
        }

    }

}

infix fun <T : Resource> T.eqr(other: T) = this.uuid == other.uuid
