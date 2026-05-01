package network.columba.desktop.data.db.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import network.columba.desktop.data.db.entity.ConversationEntity
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet

/**
 * Desktop DAO for conversations using SQLite JDBC.
 * Matches Android ConversationDao interface for compatibility.
 */
class ConversationDao(private val connectionProvider: () -> Connection) {
    private val logger = LoggerFactory.getLogger(ConversationDao::class.java)

    fun getConversations(identityHash: String): List<ConversationEntity> {
        val query = """
            SELECT peerHash, identityHash, peerName, peerPublicKey, lastMessage,
                   lastMessageTimestamp, unreadCount, lastSeenTimestamp
            FROM conversations
            WHERE identityHash = ?
            ORDER BY lastMessageTimestamp DESC
        """.trimIndent()

        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, identityHash)
                val rs = stmt.executeQuery()
                val result = mutableListOf<ConversationEntity>()
                while (rs.next()) {
                    result.add(rsToConversationEntity(rs))
                }
                result
            }
        }
    }

    fun getConversationsFlow(identityHash: String): Flow<List<ConversationEntity>> {
        // Note: For desktop, we'll use polling for now since Flow from JDBC is complex
        return flowOf(emptyList()) // TODO: Implement proper Flow with polling
    }

    fun getConversation(peerHash: String, identityHash: String): ConversationEntity? {
        val query = """
            SELECT peerHash, identityHash, peerName, peerPublicKey, lastMessage,
                   lastMessageTimestamp, unreadCount, lastSeenTimestamp
            FROM conversations
            WHERE peerHash = ? AND identityHash = ?
        """.trimIndent()

        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, peerHash)
                stmt.setString(2, identityHash)
                val rs = stmt.executeQuery()
                if (rs.next()) rsToConversationEntity(rs) else null
            }
        }
    }

    fun insertConversation(conversation: ConversationEntity) {
        val query = """
            INSERT OR REPLACE INTO conversations
            (peerHash, identityHash, peerName, peerPublicKey, lastMessage,
             lastMessageTimestamp, unreadCount, lastSeenTimestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, conversation.peerHash)
                stmt.setString(2, conversation.identityHash)
                stmt.setString(3, conversation.peerName)
                stmt.setString(4, conversation.peerPublicKey)
                stmt.setString(5, conversation.lastMessage)
                stmt.setLong(6, conversation.lastMessageTimestamp)
                stmt.setInt(7, conversation.unreadCount)
                stmt.setLong(8, conversation.lastSeenTimestamp)
                stmt.executeUpdate()
            }
        }
    }

    fun updateConversation(conversation: ConversationEntity) {
        insertConversation(conversation) // Same as insert due to OR REPLACE
    }

    fun deleteConversation(conversation: ConversationEntity) {
        val query = "DELETE FROM conversations WHERE peerHash = ? AND identityHash = ?"
        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, conversation.peerHash)
                stmt.setString(2, conversation.identityHash)
                stmt.executeUpdate()
            }
        }
    }

    fun markAsRead(peerHash: String, identityHash: String) {
        val query = """
            UPDATE conversations
            SET unreadCount = 0, lastSeenTimestamp = ?
            WHERE peerHash = ? AND identityHash = ?
        """.trimIndent()

        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setLong(1, System.currentTimeMillis())
                stmt.setString(2, peerHash)
                stmt.setString(3, identityHash)
                stmt.executeUpdate()
            }
        }
    }

    fun updatePeerName(peerHash: String, identityHash: String, peerName: String) {
        val query = """
            UPDATE conversations
            SET peerName = ?
            WHERE peerHash = ? AND identityHash = ?
        """.trimIndent()

        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, peerName)
                stmt.setString(2, peerHash)
                stmt.setString(3, identityHash)
                stmt.executeUpdate()
            }
        }
    }

    private fun rsToConversationEntity(rs: ResultSet): ConversationEntity {
        return ConversationEntity(
            peerHash = rs.getString("peerHash"),
            identityHash = rs.getString("identityHash"),
            peerName = rs.getString("peerName"),
            peerPublicKey = rs.getString("peerPublicKey"),
            lastMessage = rs.getString("lastMessage"),
            lastMessageTimestamp = rs.getLong("lastMessageTimestamp"),
            unreadCount = rs.getInt("unreadCount"),
            lastSeenTimestamp = rs.getLong("lastSeenTimestamp"),
        )
    }
}
