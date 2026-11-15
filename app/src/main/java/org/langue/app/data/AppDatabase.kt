package org.langue.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "packs")
internal data class PackEntity(
    @PrimaryKey val id: String,
    val json: String,
    val directory: String,
)

@Entity(tableName = "categories", primaryKeys = ["packId", "categoryId"])
internal data class CategoryEntity(val packId: String, val categoryId: String, val position: Int, val json: String)

@Entity(tableName = "entries", primaryKeys = ["packId", "entryId"])
internal data class EntryEntity(val packId: String, val entryId: String, val position: Int, val json: String)

internal data class StoredPack(
    @Embedded val pack: PackEntity,
    @Relation(parentColumn = "id", entityColumn = "packId") val categories: List<CategoryEntity>,
    @Relation(parentColumn = "id", entityColumn = "packId") val entries: List<EntryEntity>,
)

internal val StoredPack.id: String get() = pack.id
internal val StoredPack.directory: String get() = pack.directory
internal val StoredPack.json: String get() = pack.json

@Entity(tableName = "quiz_progress")
internal data class QuizEntity(
    @PrimaryKey val packId: String,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
)

@Entity(tableName = "viewed_entries", primaryKeys = ["packId", "entryId"])
internal data class ViewedEntity(val packId: String, val entryId: String)

@Dao
internal interface PackDao {
    @Transaction @Query("SELECT * FROM packs ORDER BY id") fun observePacks(): Flow<List<StoredPack>>
    @Transaction @Query("SELECT * FROM packs ORDER BY id") suspend fun packs(): List<StoredPack>
    @Transaction @Query("SELECT * FROM packs WHERE id = :id") suspend fun pack(id: String): StoredPack?
    @Query("SELECT * FROM packs ORDER BY id") suspend fun metadata(): List<PackEntity>
    @Query("SELECT * FROM packs WHERE id = :id") suspend fun metadata(id: String): PackEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM entries WHERE packId = :packId AND entryId = :entryId)")
    suspend fun entryExists(packId: String, entryId: String): Boolean
    @Query("SELECT entryId FROM entries WHERE packId = :id") suspend fun entryIds(id: String): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePack(pack: PackEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCategories(categories: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveEntries(entries: List<EntryEntity>)
    @Query("DELETE FROM categories WHERE packId = :id") suspend fun deleteCategories(id: String)
    @Query("DELETE FROM entries WHERE packId = :id") suspend fun deleteEntries(id: String)
    @Query("DELETE FROM packs WHERE id = :id") suspend fun deletePack(id: String)

    @Query("SELECT * FROM quiz_progress") fun observeQuiz(): Flow<List<QuizEntity>>
    @Query("SELECT * FROM quiz_progress WHERE packId = :id") suspend fun quiz(id: String): QuizEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveQuiz(quiz: QuizEntity)
    @Query("DELETE FROM quiz_progress WHERE packId = :id") suspend fun deleteQuiz(id: String)

    @Query("SELECT * FROM viewed_entries") fun observeViewed(): Flow<List<ViewedEntity>>
    @Query("SELECT * FROM viewed_entries WHERE packId = :id") suspend fun viewed(id: String): List<ViewedEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun saveViewed(entries: List<ViewedEntity>)
    @Query("DELETE FROM viewed_entries WHERE packId = :id") suspend fun deleteViewed(id: String)
}

@Database(entities = [PackEntity::class, CategoryEntity::class, EntryEntity::class, QuizEntity::class, ViewedEntity::class], version = 1, exportSchema = true)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun packs(): PackDao
}
