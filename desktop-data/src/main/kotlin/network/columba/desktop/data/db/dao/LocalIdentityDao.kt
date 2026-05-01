package network.columba.desktop.data.db.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import network.columba.desktop.data.db.entity.LocalIdentityEntity
import java.sql.Connection
import java.sql.ResultSet

/**
 * Desktop DAO for local identities using SQLite JDBC.
 * Matches Android LocalIdentityDao interface for compatibility.
 */
class LocalIdentityDao(private val connectionProvider: () -> Connection) {

    fun getAllIdentities(): List<LocalIdentityEntity> {
        val query = """
            SELECT identityHash, displayName, publicKey, privateKey, isActive, createdAt
            FROM local_identities
            ORDER BY createdAt DESC
        """.trimIndent()

        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                val rs = stmt.executeQuery()
                val result = mutableListOf<LocalIdentityEntity>()
                while (rs.next()) {
                    result.add(rsToLocalIdentityEntity(rs))
                }
                result
            }
        }
    }

    fun getActiveIdentity(): LocalIdentityEntity? {
        val query = """
            SELECT identityHash, displayName, publicKey, privateKey, isActive, createdAt
            FROM local_identities
            WHERE isActive = 1
            LIMIT 1
        """.trimIndent()

        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                val rs = stmt.executeQuery()
                if (rs.next()) rsToLocalIdentityEntity(rs) else null
            }
        }
    }

    fun getActiveIdentityFlow(): Flow<LocalIdentityEntity?> {
        return flowOf(null) // TODO: Implement polling
    }

    fun getIdentity(identityHash: String): LocalIdentityEntity? {
        val query = """
            SELECT identityHash, displayName, publicKey, privateKey, isActive, createdAt
            FROM local_identities
            WHERE identityHash = ?
            LIMIT 1
        """.trimIndent()

        return connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, identityHash)
                val rs = stmt.executeQuery()
                if (rs.next()) rsToLocalIdentityEntity(rs) else null
            }
        }
    }

    fun insertIdentity(identity: LocalIdentityEntity) {
        val query = """
            INSERT OR REPLACE INTO local_identities
            (identityHash, displayName, publicKey, privateKey, isActive, createdAt)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, identity.identityHash)
                stmt.setString(2, identity.displayName)
                stmt.setString(3, identity.publicKey)
                stmt.setString(4, identity.privateKey)
                stmt.setBoolean(5, identity.isActive)
                stmt.setLong(6, identity.createdAt)
                stmt.executeUpdate()
            }
        }
    }

    fun updateIdentity(identity: LocalIdentityEntity) {
        insertIdentity(identity) // Same as insert due to OR REPLACE
    }

    fun deleteIdentity(identityHash: String) {
        val query = "DELETE FROM local_identities WHERE identityHash = ?"
        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, identityHash)
                stmt.executeUpdate()
            }
        }
    }

    fun setActiveIdentity(identityHash: String) {
        // First, deactivate all identities
        connectionProvider().use { conn ->
            conn.prepareStatement("UPDATE local_identities SET isActive = 0").use { stmt ->
                stmt.executeUpdate()
            }
        }

        // Then, activate the specified identity
        val query = "UPDATE local_identities SET isActive = 1 WHERE identityHash = ?"
        connectionProvider().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, identityHash)
                stmt.executeUpdate()
            }
        }
    }

    private fun rsToLocalIdentityEntity(rs: ResultSet): LocalIdentityEntity {
        return LocalIdentityEntity(
            identityHash = rs.getString("identityHash"),
            displayName = rs.getString("displayName"),
            publicKey = rs.getString("publicKey"),
            privateKey = rs.getString("privateKey"),
            isActive = rs.getBoolean("isActive"),
            createdAt = rs.getLong("createdAt"),
        )
    }
}
