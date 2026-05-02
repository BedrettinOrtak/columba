package network.columba.desktop.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop database implementation using SQLite JDBC.
 * Matches Android Room database structure for compatibility.
 */
class ColumbaDatabase(
    private val databaseFile: File,
) {
    private val logger = LoggerFactory.getLogger(ColumbaDatabase::class.java)

    init {
        ensureTablesExist()
    }

    /**
     * Get a fresh database connection. Caller is responsible for closing it
     * (DAOs use Connection.use { } so pooling here would double-close).
     */
    fun getConnection(): Connection {
        return createConnection().also { conn ->
            // Pragmas must run with autoCommit=true; SQLite cannot change
            // journal_mode while a transaction is open. Each PRAGMA must
            // be fully consumed (close stmt) before the next runs,
            // otherwise SQLITE_BUSY ("SQL statements in progress").
            conn.autoCommit = true
            listOf(
                "PRAGMA journal_mode=WAL",
                "PRAGMA synchronous=NORMAL",
                "PRAGMA foreign_keys=ON",
                "PRAGMA busy_timeout=5000",
            ).forEach { sql ->
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                    stmt.resultSet?.close()
                }
            }
            // Leave autoCommit=true: DAOs use Connection.use { } which only
            // closes; without auto-commit their writes would silently roll back.
            // withConnection() flips autoCommit off when it needs a transaction.
        }
    }

    private fun createConnection(): Connection {
        val url = "jdbc:sqlite:${databaseFile.absolutePath}"
        return DriverManager.getConnection(url)
    }

    /**
     * No-op: connections are short-lived and closed by callers via Connection.use.
     */
    fun close() {
        // intentionally empty
    }

    private fun ensureTablesExist() {
        val conn = getConnection()
        conn.autoCommit = false
        try {
            // Create local_identities table
            conn.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS local_identities (
                    identityHash TEXT PRIMARY KEY NOT NULL,
                    displayName TEXT NOT NULL,
                    publicKey TEXT NOT NULL,
                    privateKey TEXT NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // Create conversations table
            conn.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS conversations (
                    peerHash TEXT NOT NULL,
                    identityHash TEXT NOT NULL,
                    peerName TEXT NOT NULL,
                    peerPublicKey TEXT,
                    lastMessage TEXT NOT NULL,
                    lastMessageTimestamp INTEGER NOT NULL,
                    unreadCount INTEGER NOT NULL DEFAULT 0,
                    lastSeenTimestamp INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (peerHash, identityHash),
                    FOREIGN KEY (identityHash) REFERENCES local_identities(identityHash) ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            // Create messages table
            conn.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT NOT NULL,
                    conversationHash TEXT NOT NULL,
                    identityHash TEXT NOT NULL,
                    content TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    isFromMe INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'sent',
                    isRead INTEGER NOT NULL DEFAULT 0,
                    fieldsJson TEXT,
                    deliveryMethod TEXT,
                    errorMessage TEXT,
                    replyToMessageId TEXT,
                    receivedHopCount INTEGER,
                    receivedInterface TEXT,
                    receivedRssi INTEGER,
                    receivedSnr REAL,
                    receivedAt INTEGER,
                    sentInterface TEXT,
                    PRIMARY KEY (id, identityHash),
                    FOREIGN KEY (conversationHash, identityHash) REFERENCES conversations(peerHash, identityHash) ON DELETE CASCADE,
                    FOREIGN KEY (identityHash) REFERENCES local_identities(identityHash) ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            // Create indexes for better query performance
            conn.createStatement().execute(
                """
                CREATE INDEX IF NOT EXISTS idx_conversations_identity
                ON conversations(identityHash)
                """.trimIndent(),
            )

            conn.createStatement().execute(
                """
                CREATE INDEX IF NOT EXISTS idx_conversations_timestamp
                ON conversations(identityHash, lastMessageTimestamp DESC)
                """.trimIndent(),
            )

            conn.createStatement().execute(
                """
                CREATE INDEX IF NOT EXISTS idx_messages_conversation
                ON messages(conversationHash, identityHash)
                """.trimIndent(),
            )

            conn.createStatement().execute(
                """
                CREATE INDEX IF NOT EXISTS idx_messages_timestamp
                ON messages(timestamp DESC)
                """.trimIndent(),
            )

            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.close()
        }
        logger.info("Database tables created/verified")
    }

    /**
     * Execute a block of code with a database connection.
     * Handles transaction management automatically.
     */
    suspend fun <T> withConnection(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        val conn = getConnection()
        conn.autoCommit = false
        try {
            val result = block(conn)
            conn.commit()
            result
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.close()
        }
    }

    companion object {
        /**
         * Get the database instance.
         * Database file is stored in the application's data directory.
         */
        fun getInstance(dataDir: File = File(System.getProperty("user.home"), ".columba")): ColumbaDatabase {
            val dbDir = File(dataDir, "data")
            if (!dbDir.exists()) {
                dbDir.mkdirs()
            }
            val dbFile = File(dbDir, "columba.db")
            return ColumbaDatabase(dbFile)
        }
    }
}
