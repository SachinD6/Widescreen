package dev.sxch.tvweb.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HistoryEntry(val id: Long, val url: String, val title: String, val visitedAt: Long)

data class Bookmark(val id: Long, val url: String, val title: String, val createdAt: Long)

data class Shortcut(val id: Long, val title: String, val url: String, val position: Int)

class BrowserStore(context: Context) {

    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "store-writer").apply { isDaemon = true }
    }

    private val helper = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, " +
                    "title TEXT NOT NULL, visited_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX history_visited ON history(visited_at DESC)")
            db.execSQL(
                "CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL UNIQUE, " +
                    "title TEXT NOT NULL, created_at INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE shortcuts (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, " +
                    "url TEXT NOT NULL, position INTEGER NOT NULL)"
            )
            seedShortcuts(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun seedShortcuts(db: SQLiteDatabase) {
        DEFAULT_SHORTCUTS.forEachIndexed { index, (title, url) ->
            db.insert("shortcuts", null, ContentValues().apply {
                put("title", title)
                put("url", url)
                put("position", index)
            })
        }
    }

    fun recordVisit(url: String, title: String) {
        if (url.startsWith("about:")) return
        writer.execute {
            helper.writableDatabase.insert("history", null, ContentValues().apply {
                put("url", url)
                put("title", title)
                put("visited_at", System.currentTimeMillis())
            })
            helper.writableDatabase.execSQL(
                "DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visited_at DESC LIMIT 3000)"
            )
        }
    }

    fun history(limit: Int = 200): List<HistoryEntry> =
        helper.readableDatabase.rawQuery(
            "SELECT id, url, title, visited_at FROM history GROUP BY url " +
                "ORDER BY MAX(visited_at) DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(HistoryEntry(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
                }
            }
        }

    fun clearHistory() {
        helper.writableDatabase.delete("history", null, null)
    }

    fun removeHistory(url: String) {
        helper.writableDatabase.delete("history", "url = ?", arrayOf(url))
    }

    fun bookmarks(): List<Bookmark> =
        helper.readableDatabase.rawQuery(
            "SELECT id, url, title, created_at FROM bookmarks ORDER BY created_at DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Bookmark(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
                }
            }
        }

    fun isBookmarked(url: String): Boolean =
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM bookmarks WHERE url = ? LIMIT 1",
            arrayOf(url),
        ).use { it.moveToFirst() }

    fun addBookmark(url: String, title: String) {
        helper.writableDatabase.insertWithOnConflict(
            "bookmarks",
            null,
            ContentValues().apply {
                put("url", url)
                put("title", title.ifBlank { url })
                put("created_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun removeBookmark(url: String) {
        helper.writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
    }

    fun shortcuts(): List<Shortcut> =
        helper.readableDatabase.rawQuery(
            "SELECT id, title, url, position FROM shortcuts ORDER BY position ASC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Shortcut(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3)))
                }
            }
        }

    fun addShortcut(title: String, url: String) {
        val position = shortcuts().size
        helper.writableDatabase.insert("shortcuts", null, ContentValues().apply {
            put("title", title.ifBlank { hostOf(url) })
            put("url", url)
            put("position", position)
        })
    }

    fun removeShortcut(id: Long) {
        helper.writableDatabase.delete("shortcuts", "id = ?", arrayOf(id.toString()))
    }


    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host ?: url }.getOrDefault(url).removePrefix("www.")

    companion object {
        private const val DB_NAME = "browser.db"
        private const val DB_VERSION = 1

        val DEFAULT_SHORTCUTS: List<Pair<String, String>> = listOf(
            "YouTube" to "https://www.youtube.com/",
            "Twitch" to "https://www.twitch.tv/",
            "Wikipedia" to "https://en.wikipedia.org/",
            "Internet Archive" to "https://archive.org/",
            "Reddit" to "https://www.reddit.com/",
        )
    }
}
