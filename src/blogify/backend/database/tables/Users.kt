package blogify.backend.database.tables

import blogify.backend.database.extensions.keyOf
import blogify.backend.database.extensions.weakKeyFrom
import blogify.backend.database.handling.query
import blogify.backend.database.models.ResourceTable
import blogify.backend.persistence.models.Repository
import blogify.backend.pipelines.wrapping.RequestContext
import blogify.backend.resources.User
import blogify.backend.resources.static.models.StaticResourceHandle
import blogify.backend.util.Sr
import blogify.backend.util.Wrap

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

import com.github.kittinunf.result.coroutines.SuspendableResult

import io.ktor.http.ContentType

@Suppress("DuplicatedCode")
object Users : ResourceTable<User>() {

    val username       = varchar ("username", 255)
    val password       = varchar ("password", 255)
    val email          = varchar ("email", 255)
    val name           = varchar ("name", 255)
    val profilePicture = varchar ("profile_picture", 32).nullable() weakKeyFrom Uploadables.fileId
    val coverPicture   = varchar ("cover_picture", 32).nullable() weakKeyFrom Uploadables.fileId
    val isAdmin        = bool    ("is_admin")

    init {
        index(true, username)
    }

    object Follows : Table() {

        val following = uuid("following") keyOf Users
        val follower  = uuid("follower") keyOf Users

        override val primaryKey = PrimaryKey(
            following,
            follower
        )

    }

    override suspend fun insert(resource: User): Sr<User> {
        return Wrap {
            query {
                insert {
                    it[uuid] = resource.uuid
                    it[username] = resource.username
                    it[password] = resource.password
                    it[email] = resource.email
                    it[name] = resource.name
                    it[profilePicture] =
                        if (resource.profilePicture is StaticResourceHandle.Ok) resource.profilePicture.fileId else null
                    it[isAdmin] = resource.isAdmin
                }
            }
            return@Wrap resource
        }

    }

    override suspend fun update(resource: User): Boolean {
        return query {
            this.update(where = { uuid eq resource.uuid }) {
                it[uuid] = resource.uuid
                it[username] = resource.username
                it[password] = resource.password
                it[email] = resource.email
                it[name] = resource.name
                it[profilePicture] =
                    if (resource.profilePicture is StaticResourceHandle.Ok) resource.profilePicture.fileId else null
                it[coverPicture] =
                    if (resource.coverPicture is StaticResourceHandle.Ok) resource.coverPicture.fileId else null
                it[isAdmin] = resource.isAdmin
            }
        }.get() == 1
    }

    override suspend fun convert(requestContext: RequestContext, source: ResultRow) =
        SuspendableResult.of<User, Repository.Exception.Fetching> {
            User(
                uuid = source[uuid],
                username = source[username],
                password = source[password],
                name = source[name],
                email = source[email],
                isAdmin = source[isAdmin],
                profilePicture = source[profilePicture]?.let {
                    transaction {
                        Uploadables.select { Uploadables.fileId eq source[profilePicture]!! }
                            .limit(1).single()
                    }.let {
                        Uploadables.convert(requestContext, it).get()
                    }
                } ?: StaticResourceHandle.None(ContentType.Any),
                coverPicture = source[coverPicture]?.let {
                    transaction {
                        Uploadables.select { Uploadables.fileId eq source[coverPicture]!! }
                            .limit(1).single()
                    }.let {
                        Uploadables.convert(requestContext, it).get()
                    }
                } ?: StaticResourceHandle.None(ContentType.Any)
            )
        }

}
