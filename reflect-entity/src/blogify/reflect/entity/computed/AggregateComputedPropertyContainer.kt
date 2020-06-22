package blogify.reflect.entity.computed

import blogify.reflect.computed.models.ComputedPropContainer
import blogify.reflect.entity.Entity
import blogify.reflect.entity.database.annotations.table

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

import java.util.*

class AggregateComputedPropertyContainer<TEntity : Entity, TProperty : Any?> (
    override val obj: TEntity,
    val aggregateExpr: Expression<TProperty>,
    val rightTable: Table,
    val rightColumn: Column<UUID>
) : ComputedPropContainer<TEntity, TProperty>()  {

    init {
        if (aggregateExpr !is Avg<*, *>)
            error("only Avg() aggregate functions are currently supported")

        if (rightColumn.referee != obj::class.table.uuid)
            error("rightColumn must be a FK to the PK of ${obj::class.simpleName}'s EntityTable")
    }

    fun computeValue(): TProperty = transaction {
        rightTable.slice(aggregateExpr).select { rightColumn eq obj.uuid }
            .singleOrNull()?.let { it[aggregateExpr] } ?: error("fuck")
    }

}
