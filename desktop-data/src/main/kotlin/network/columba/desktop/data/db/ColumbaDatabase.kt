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
    private val connectionPool = ConcurrentHashMap<String, Connection>()

    init {
        ensureTablesExist()
    }

    /**
     * Get or create a database connection.
     * Thread-safe connection pooling.
     */
    fun getConnection(): Connection {
        val threadId = Thread.currentThread().name
        return connectionPool.getOrPut(threadId) {
            createConnection().also { conn ->
                // Configure connection for better performance
                conn.autoCommit = false
                conn.createStatement().execute("PRAGMA journal_mode=WAL")
                conn.createStatement().execute("PRAGMA synchronous=NORMAL")
                conn.createStatement().execute("PRAGMA foreign_keys=ON")
                conn.createStatement().execute("PRAGMA busy_timeout=5000")
            }
        }
    }

    private fun createConnection(): Connection {
        val url = "jdbc:sqlite:${databaseFile.absolutePath}"
        logger.info("Creating database connection: $url")
        return DriverManager.getConnection(url)
    }

    /**
     * Close all open connections.
     * Call this when the application shuts down.
     */
    fun close() {
        connectionPool.values.forEach { conn ->
            try {
                conn.close()
            } catch (e: Exception) {
                logger.error("Error closing database connection", e)
            }
        }
        connectionPool.clear()
    }

    private fun ensureTablesExist() {
        val conn = getConnection()
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
        try {
            val result = block(conn)
            conn.commit()
            result
        } catch (e: Exception) {
            conn.rollback()
            throw e
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
