package network.columba.desktop.data.db.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import network.columba.desktop.data.db.entity.MessageEntity
import java.sql.Connection
import java.sql.ResultSet

/**
 * Desktop DAO for messages using SQLite JDBC.
 * Matches Android MessageDao interface for compatibility.
 */
class MessageDao(private val connectionProvider: () -> Connection) {

    fun getMessagesForConversation(peerHash: String, identityHash: String): List<MessageEntity> {
        val query = """
            SELECT id, conversationHash, identityHash, content, timestamp,
                   isFromMe, status, isRead, fieldsJson, deliveryMethod,
                   errorMessage, replyToMessageId, receivedHopCount,
                   receivedInterface, receivedRssi, receivedSnr,
                   receivedAt, sentInterface
            FROM messages
            WHERE conversationHash = ? AND identityHash = ?
            ORDER BY COALESCE(receivedAt, timestamp) ASC
        """.trimIndent()

        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, peerHash)
                stmt.setString(2, identityHash)
                val rs = stmt.executeQuery()
                val result = mutableListOf<MessageEntity>()
                while (rs.next()) {
                    result.add(rsToMessageEntity(rs))
                }
                result
            }
        }
    }

    fun getMessagesForConversationFlow(peerHash: String, identityHash: String): Flow<List<MessageEntity>> {
        // Note: For desktop, we'll use polling for now
        return flowOf(emptyList()) // TODO: Implement proper Flow with polling
    }

    fun getLastMessage(peerHash: String, identityHash: String): MessageEntity? {
        val query = """
            SELECT id, conversationHash, identityHash, content, timestamp,
                   isFromMe, status, isRead, fieldsJson, deliveryMethod,
                   errorMessage, replyToMessageId, receivedHopCount,
                   receivedInterface, receivedRssi, receivedSnr,
                   receivedAt, sentInterface
            FROM messages
            WHERE conversationHash = ? AND identityHash = ?
            ORDER BY COALESCE(receivedAt, timestamp) DESC
            LIMIT 1
        """.trimIndent()

        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, peerHash)
                stmt.setString(2, identityHash)
                val rs = stmt.executeQuery()
                if (rs.next()) rsToMessageEntity(rs) else null
            }
        }
    }

    fun insertMessage(message: MessageEntity) {
        val query = """
            INSERT OR REPLACE INTO messages
            (id, conversationHash, identityHash, content, timestamp, isFromMe,
             status, isRead, fieldsJson, deliveryMethod, errorMessage,
             replyToMessageId, receivedHopCount, receivedInterface, receivedRssi,
             receivedSnr, receivedAt, sentInterface)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, message.id)
                stmt.setString(2, message.conversationHash)
                stmt.setString(3, message.identityHash)
                stmt.setString(4, message.content)
                stmt.setLong(5, message.timestamp)
                stmt.setBoolean(6, message.isFromMe)
                stmt.setString(7, message.status)
                stmt.setBoolean(8, message.isRead)
                stmt.setString(9, message.fieldsJson)
                stmt.setString(10, message.deliveryMethod)
                stmt.setString(11, message.errorMessage)
                stmt.setString(12, message.replyToMessageId)
                message.receivedHopCount?.let { stmt.setInt(13, it) } ?: stmt.setNull(13, java.sql.Types.INTEGER)
                stmt.setString(14, message.receivedInterface)
                message.receivedRssi?.let { stmt.setInt(15, it) } ?: stmt.setNull(15, java.sql.Types.INTEGER)
                message.receivedSnr?.let { stmt.setFloat(16, it) } ?: stmt.setNull(16, java.sql.Types.FLOAT)
                message.receivedAt?.let { stmt.setLong(17, it) } ?: stmt.setNull(17, java.sql.Types.BIGINT)
                stmt.setString(18, message.sentInterface)
                stmt.executeUpdate()
            }
        }
    }

    fun updateMessage(message: MessageEntity) {
        insertMessage(message) // Same as insert due to OR REPLACE
    }

    fun deleteMessage(message: MessageEntity) {
        deleteMessageById(message.id, message.identityHash)
    }

    fun deleteMessageById(messageId: String, identityHash: String) {
        val query = "DELETE FROM messages WHERE id = ? AND identityHash = ?"
        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, messageId)
                stmt.setString(2, identityHash)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteMessagesForConversation(peerHash: String, identityHash: String) {
        val query = "DELETE FROM messages WHERE conversationHash = ? AND identityHash = ?"
        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, peerHash)
                stmt.setString(2, identityHash)
                stmt.executeUpdate()
            }
        }
    }

    fun markMessagesAsRead(peerHash: String, identityHash: String) {
        val query = """
            UPDATE messages
            SET isRead = 1
            WHERE conversationHash = ? AND identityHash = ? AND isFromMe = 0
        """.trimIndent()

        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, peerHash)
                stmt.setString(2, identityHash)
                stmt.executeUpdate()
            }
        }
    }

    fun getUnreadCount(peerHash: String, identityHash: String): Int {
        val query = """
            SELECT COUNT(*) as count
            FROM messages
            WHERE conversationHash = ? AND identityHash = ?
            AND isFromMe = 0 AND isRead = 0
        """.trimIndent()

        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, peerHash)
                stmt.setString(2, identityHash)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getInt("count") else 0
            }
        }
    }

    fun messageExists(messageId: String, identityHash: String): Boolean {
        val query = "SELECT 1 FROM messages WHERE id = ? AND identityHash = ? LIMIT 1"
        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, messageId)
                stmt.setString(2, identityHash)
                stmt.executeQuery().next()
            }
        }
    }

    fun getMessageById(messageId: String, identityHash: String): MessageEntity? {
        val query = """
            SELECT id, conversationHash, identityHash, content, timestamp,
                   isFromMe, status, isRead, fieldsJson, deliveryMethod,
                   errorMessage, replyToMessageId, receivedHopCount,
                   receivedInterface, receivedRssi, receivedSnr,
                   receivedAt, sentInterface
            FROM messages
            WHERE id = ? AND identityHash = ?
            LIMIT 1
        """.trimIndent()

        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, messageId)
                stmt.setString(2, identityHash)
                val rs = stmt.executeQuery()
                if (rs.next()) rsToMessageEntity(rs) else null
            }
        }
    }

    fun updateMessageStatus(messageId: String, identityHash: String, status: String) {
        val query = "UPDATE messages SET status = ? WHERE id = ? AND identityHash = ?"
        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, status)
                stmt.setString(2, messageId)
                stmt.setString(3, identityHash)
                stmt.executeUpdate()
            }
        }
    }

    fun observeMessageById(messageId: String): Flow<MessageEntity?> {
        return flowOf(null) // TODO: Implement polling
    }

    private fun rsToMessageEntity(rs: ResultSet): MessageEntity {
        return MessageEntity(
            id = rs.getString("id"),
            conversationHash = rs.getString("conversationHash"),
            identityHash = rs.getString("identityHash"),
            content = rs.getString("content"),
            timestamp = rs.getLong("timestamp"),
            isFromMe = rs.getBoolean("isFromMe"),
            status = rs.getString("status"),
            isRead = rs.getBoolean("isRead"),
            fieldsJson = rs.getString("fieldsJson"),
            deliveryMethod = rs.getString("deliveryMethod"),
            errorMessage = rs.getString("errorMessage"),
            replyToMessageId = rs.getString("replyToMessageId"),
            receivedHopCount = rs.getInt("receivedHopCount").takeIf { !rs.wasNull() },
            receivedInterface = rs.getString("receivedInterface"),
            receivedRssi = rs.getInt("receivedRssi").takeIf { !rs.wasNull() },
            receivedSnr = rs.getFloat("receivedSnr").takeIf { !rs.wasNull() },
            receivedAt = rs.getLong("receivedAt").takeIf { !rs.wasNull() },
            sentInterface = rs.getString("sentInterface"),
        )
    }
}
