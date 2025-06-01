package org.cheek.wakeitup

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "devices")
data class Device (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val macAddress: String,
    val ipAddress: String,
    val port: Int = 9,
    val groupName: String = "Bookmarked",
    val color: Int = 0
)

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices")
    fun getAllDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices where groupName = :group")
    fun getDevicesByGroup(group: String): Flow<List<Device>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: Device)

    @Delete
    suspend fun delete(device: Device)
}

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // Ignore if group name exists
    suspend fun insertGroup(group: Group)

    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<Group>>

    @Query("SELECT * FROM groups WHERE name = :name LIMIT 1")
    suspend fun getGroupByName(name: String): Group?
}

data class NetworkDevice (
    val name: String,
    val ip: String,
    val port: Int,
    val serviceType: String,

    val macAddress: String? = null,
    val broadcastAddress: String = ""
)