package blogify.backend.routing.pipelines

import blogify.backend.annotations.PipelinesDsl
import blogify.backend.auth.handling.UserAuthPredicate
import blogify.backend.auth.handling.runAuthenticated
import blogify.backend.pipelines.ApplicationContext
import blogify.backend.pipelines.RequestContext
import blogify.backend.pipelines.RequestContextFunction
import blogify.backend.resources.models.Resource
import blogify.backend.routing.handling.defaultResourceLessPredicateLambda
import blogify.backend.routing.handling.logUnusedAuth
import blogify.backend.util.getOrPipelineError
import blogify.backend.util.reason
import blogify.backend.util.repository

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelineInterceptor

import com.andreapivetta.kolor.red
import io.ktor.util.pipeline.pipelineExecutorFor

import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("blogify-pipeline-manager")

/**
 * Represents a server call pipeline
 *
 * @author Benjozork
 */
typealias CallPipeline = PipelineContext<ApplicationContext, ApplicationCall>

/**
 * Represents a server call pipeline
 *
 * @author Benjozork
 */
typealias GenericCallPipeline = PipelineContext<*, ApplicationCall>

/**
 * Represents a server call handler function
 *
 * @author Benjozork
 */
typealias CallPipeLineFunction = PipelineInterceptor<ApplicationContext, ApplicationCall>

/**
 * Represents an error that occurs while a [CallPipeline] is running. Interrupts the pipeline and responds with the given [status code][code] and
 * [error message][message]
 *
 * @property code    the [HttpStatusCode] to respond to the originating request with
 * @property message the message body, which will be wrapped in [reason]
 *
 * @author Benjozork
 */
class PipelineException(val code: HttpStatusCode, override val message: String) : Exception(message)

/**
 * TEMPORARY. DO NOT USE. Used to generate a [CallPipeline] from a normal Ktor PipelineContext.
 */
suspend fun PipelineContext<Unit, ApplicationCall>.wrapRequest (
    applicationContext: ApplicationContext,
    block:              suspend CallPipeline.(ApplicationContext) -> Unit
) {
    pipelineExecutorFor<ApplicationContext, ApplicationCall>(call, listOf(block), applicationContext)
        .execute(applicationContext)
}

/**
 * Handles a request by creating a [RequestContext] inside an [ApplicationContext]
 *
 * @receiver a [PipelineContext] with an [ApplicationContext] as subject and [ApplicationCall] as context
 *
 * @param block function to be run inside the request context
 */
@PipelinesDsl
suspend fun CallPipeline.request(block: RequestContextFunction<Unit>) {
    val reqCtx = RequestContext(subject, call)

    try {
        block(reqCtx, Unit)
    } catch (e: PipelineException) {
        call.respond(e.code, reason(e.message))
    } catch (e: Exception) {
        logger.error (
            """
            |unhandled exception in pipeline - ${e::class.simpleName} - ${e.message}
            |${e.stackTrace.joinToString(prefix = "\t", separator = "\n\t")}
            """.trimMargin()
        )
        call.respond(HttpStatusCode.InternalServerError, reason("unhandled exception in pipeline"))
    }
}

///**
// * Starts a child [CallPipeline] from another [pipeline][CallPipeline]
// *
// * @param wantedParams an array of wanted query parameters, empty by default. The absence of any of them will yield a 400 Bad Request detailing the missing parameter.
// * @param block        the actual pipeline code
// *
// * @author Benjozork
// */
//@PipelinesDsl
//suspend fun CallPipeline.pipeline(vararg wantedParams: String = emptyArray(), block: suspend CallPipeline.(Array<String>) -> Unit) {
//    try {
//        block(this, wantedParams.map { param -> call.parameters[param] ?: pipelineError(message = "query parameter $param is null") }.toTypedArray())
//    } catch (e: PipelineException) {
//        call.respond(e.code, reason(e.message))
//    } catch (e: Exception) {
//        logger.error (
//            """
//            |unhandled exception in pipeline - ${e::class.simpleName} - ${e.message}
//            |${e.stackTrace.joinToString(prefix = "\t", separator = "\n\t")}
//            """.trimMargin()
//        )
//        call.respond(HttpStatusCode.InternalServerError, reason("unhandled exception in pipeline"))
//    }
//}

/**
 * Returns a query parameter that must exist
 */
@PipelinesDsl
fun RequestContext.param(name: String) = call.parameters[name] ?: pipelineError(message = "query parameter $name is null")

/**
 * Returns a query parameter that may or may not exist
 */
@PipelinesDsl
fun RequestContext.optionalParam(name: String): String? = call.parameters[name]

/**
 * A default [CallPipeline] that handles client authentication.
 *
 * @param funcName  the name of the pipeline using this pipeline. Only for logging purposes.
 * @param predicate the [UserAuthPredicate] to run as authentication
 * @param block     the actual pipeline code
 *
 * @author Benjozork
 */
@PipelinesDsl
suspend fun CallPipeline.handleAuthentication(funcName: String = "<unspecified>", predicate: UserAuthPredicate, block: CallPipeLineFunction) {
    if (predicate != defaultResourceLessPredicateLambda) { // Don't authenticate if the endpoint doesn't authenticate
        runAuthenticated(predicate, { block(this@handleAuthentication, this@handleAuthentication.subject) })
    } else {
        logUnusedAuth(funcName)
        block(this, this.subject)
    }
}

/**
 * Simplifies fetching a resource from a [CallPipeline]
 */
@PipelinesDsl
suspend inline fun <reified R : Resource> GenericCallPipeline.obtainResource(id: UUID): R {
    return (service<R>()::get)(call, id)
        .getOrPipelineError(HttpStatusCode.InternalServerError, "couldn't fetch resource")
}

/**
 * Simplifies fetching resources from a [CallPipeline]
 */
@PipelinesDsl
suspend inline fun <reified R : Resource> GenericCallPipeline.obtainResources(limit: Int = 25): List<R> {
    return (service<R>()::getAll)(call, limit)
        .getOrPipelineError(HttpStatusCode.InternalServerError, "couldn't fetch resource")
}

/**
 * Get a [blogify.backend.services.models.Repository] from a reified Resource type parameter
 */
inline fun <reified R : Resource> service() = R::class.repository

/**
 * Signals that a [CallPipeline] has encountered an error, and will stop being executed.
 * This function throws a [PipelineException], and therefore stops the entire pipeline call chain, entering its own request handler.
 *
 * @param code          the [HttpStatusCode] to respond to the originating request with. Defaults to 400 Bad Request.
 * @param message       the message body, which will be wrapped in [reason]
 * @param rootException the exception, if it exists, that caused this error
 *
 * @throws PipelineException to signal the end of the pipeline chain and trigger the pipeline error request handler
 *
 * @author Benjozork
 */
fun pipelineError(code: HttpStatusCode = HttpStatusCode.BadRequest, message: String, rootException: Exception? = null): Nothing {
    logger.debug (
        "pipeline error - $message".red()
                + (rootException?.let { " - ${it::class.simpleName} - ${it.message}".red() } ?: "")
    )
    rootException?.printStackTrace()
    throw PipelineException(code, message)
}
