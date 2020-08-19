/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.parser

import androidx.room.ColumnInfo
import androidx.room.compiler.processing.XProcessingEnv
import androidx.room.compiler.processing.XType
import com.squareup.javapoet.TypeName
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

@Suppress("FunctionName")
class QueryVisitor(
    private val original: String,
    private val syntaxErrors: List<String>,
    statement: ParseTree,
    private val forRuntimeQuery: Boolean
) : SQLiteBaseVisitor<Void?>() {
    private val bindingExpressions = arrayListOf<TerminalNode>()
    // table name alias mappings
    private val tableNames = mutableSetOf<Table>()
    private val withClauseNames = mutableSetOf<String>()
    private val queryType: QueryType

    init {
        queryType = (0 until statement.childCount).map {
            findQueryType(statement.getChild(it))
        }.filterNot { it == QueryType.UNKNOWN }.firstOrNull() ?: QueryType.UNKNOWN
        statement.accept(this)
    }

    private fun findQueryType(statement: ParseTree): QueryType {
        return when (statement) {
            is SQLiteParser.Select_stmtContext ->
                QueryType.SELECT
            is SQLiteParser.Delete_stmt_limitedContext,
            is SQLiteParser.Delete_stmtContext ->
                QueryType.DELETE
            is SQLiteParser.Insert_stmtContext ->
                QueryType.INSERT
            is SQLiteParser.Update_stmtContext,
            is SQLiteParser.Update_stmt_limitedContext ->
                QueryType.UPDATE
            is TerminalNode -> when (statement.text) {
                "EXPLAIN" -> QueryType.EXPLAIN
                else -> QueryType.UNKNOWN
            }
            else -> QueryType.UNKNOWN
        }
    }

    override fun visitExpr(ctx: SQLiteParser.ExprContext): Void? {
        val bindParameter = ctx.BIND_PARAMETER()
        if (bindParameter != null) {
            bindingExpressions.add(bindParameter)
        }
        return super.visitExpr(ctx)
    }

    fun createParsedQuery(): ParsedQuery {
        return ParsedQuery(
            original = original,
            type = queryType,
            inputs = bindingExpressions.sortedBy { it.sourceInterval.a },
            tables = tableNames,
            syntaxErrors = syntaxErrors,
            runtimeQueryPlaceholder = forRuntimeQuery
        )
    }

    override fun visitCommon_table_expression(
        ctx: SQLiteParser.Common_table_expressionContext
    ): Void? {
        val tableName = ctx.table_name()?.text
        if (tableName != null) {
            withClauseNames.add(unescapeIdentifier(tableName))
        }
        return super.visitCommon_table_expression(ctx)
    }

    override fun visitTable_or_subquery(ctx: SQLiteParser.Table_or_subqueryContext): Void? {
        val tableName = ctx.table_name()?.text
        if (tableName != null) {
            val tableAlias = ctx.table_alias()?.text
            if (tableName !in withClauseNames) {
                tableNames.add(
                    Table(
                        unescapeIdentifier(tableName),
                        unescapeIdentifier(tableAlias ?: tableName)
                    )
                )
            }
        }
        return super.visitTable_or_subquery(ctx)
    }

    private fun unescapeIdentifier(text: String): String {
        val trimmed = text.trim()
        ESCAPE_LITERALS.forEach {
            if (trimmed.startsWith(it) && trimmed.endsWith(it)) {
                return unescapeIdentifier(trimmed.substring(1, trimmed.length - 1))
            }
        }
        return trimmed
    }

    companion object {
        private val ESCAPE_LITERALS = listOf("\"", "'", "`")
    }
}

class SqlParser {
    companion object {
        private val INVALID_IDENTIFIER_CHARS = arrayOf('`', '\"')

        fun parse(input: String) = SingleQuerySqlParser.parse(
            input = input,
            visit = { statement, syntaxErrors ->
                QueryVisitor(
                    original = input,
                    syntaxErrors = syntaxErrors,
                    statement = statement,
                    forRuntimeQuery = false
                ).createParsedQuery()
            },
            fallback = { syntaxErrors ->
                ParsedQuery(
                    original = input,
                    type = QueryType.UNKNOWN,
                    inputs = emptyList(),
                    tables = emptySet(),
                    syntaxErrors = syntaxErrors,
                    runtimeQueryPlaceholder = false
                )
            })

        fun isValidIdentifier(input: String): Boolean =
            input.isNotBlank() && INVALID_IDENTIFIER_CHARS.none { input.contains(it) }

        /**
         * creates a no-op select query for raw queries that queries the given list of tables.
         */
        fun rawQueryForTables(tableNames: Set<String>): ParsedQuery {
            return ParsedQuery(
                original = "raw query",
                type = QueryType.UNKNOWN,
                inputs = emptyList(),
                tables = tableNames.map { Table(name = it, alias = it) }.toSet(),
                syntaxErrors = emptyList(),
                runtimeQueryPlaceholder = true
            )
        }
    }
}

enum class QueryType {
    UNKNOWN,
    SELECT,
    DELETE,
    UPDATE,
    EXPLAIN,
    INSERT;

    companion object {
        // IF you change this, don't forget to update @Query documentation.
        val SUPPORTED = hashSetOf(SELECT, DELETE, UPDATE, INSERT)
    }
}

enum class SQLTypeAffinity {
    NULL,
    TEXT,
    INTEGER,
    REAL,
    BLOB;

    fun getTypeMirrors(env: XProcessingEnv): List<XType>? {
        return when (this) {
            TEXT -> listOf(env.requireType("java.lang.String"))
            INTEGER -> withBoxedTypes(
                env, TypeName.INT, TypeName.BYTE, TypeName.CHAR,
                TypeName.LONG, TypeName.SHORT
            )
            REAL -> withBoxedTypes(env, TypeName.DOUBLE, TypeName.FLOAT)
            BLOB -> listOf(
                env.getArrayType(TypeName.BYTE)
            )
            else -> emptyList()
        }
    }

    private fun withBoxedTypes(env: XProcessingEnv, vararg primitives: TypeName):
            List<XType> {
        return primitives.flatMap {
            val primitiveType = env.requireType(it)
            listOf(primitiveType, primitiveType.boxed())
        }
    }

    companion object {
        fun fromAnnotationValue(value: Int?): SQLTypeAffinity? {
            return when (value) {
                ColumnInfo.BLOB -> BLOB
                ColumnInfo.INTEGER -> INTEGER
                ColumnInfo.REAL -> REAL
                ColumnInfo.TEXT -> TEXT
                else -> null
            }
        }
    }
}

enum class Collate {
    BINARY,
    NOCASE,
    RTRIM,
    LOCALIZED,
    UNICODE;

    companion object {
        fun fromAnnotationValue(value: Int?): Collate? {
            return when (value) {
                ColumnInfo.BINARY -> BINARY
                ColumnInfo.NOCASE -> NOCASE
                ColumnInfo.RTRIM -> RTRIM
                ColumnInfo.LOCALIZED -> LOCALIZED
                ColumnInfo.UNICODE -> UNICODE
                else -> null
            }
        }
    }
}

enum class FtsVersion {
    FTS3,
    FTS4;
}