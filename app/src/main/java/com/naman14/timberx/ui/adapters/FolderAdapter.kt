/*
 * Copyright (c) 2019 Naman Dwivedi.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package com.naman14.timberx.ui.adapters

import android.app.Activity
import android.os.AsyncTask
import android.os.Environment.DIRECTORY_MUSIC
import android.os.Environment.getExternalStoragePublicDirectory
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import com.naman14.timberx.R
import com.naman14.timberx.models.Song
import com.naman14.timberx.repository.FoldersRepository
import com.naman14.timberx.repository.SongsRepository
import com.naman14.timberx.util.Utils.getAlbumArtUri
import com.naman14.timberx.util.extensions.defaultPrefs
import com.naman14.timberx.util.extensions.inflate
import com.naman14.timberx.util.extensions.toSongIds
import com.squareup.picasso.Picasso
import java.io.File

private const val KEY_LAST_FOLDER = "last_folder"
private const val GO_UP = ".."

class FolderAdapter(
    private val context: Activity
) : RecyclerView.Adapter<FolderAdapter.ItemHolder>() {
    private val icons = arrayOf(
            getDrawable(context, R.drawable.ic_folder_open_black_24dp)!!,
            getDrawable(context, R.drawable.ic_folder_parent_dark)!!,
            getDrawable(context, R.drawable.ic_file_music_dark)!!,
            getDrawable(context, R.drawable.ic_timer_wait)!!
    )

    private val songsList = mutableListOf<Song>()
    private val prefs = context.defaultPrefs()
    private val root = prefs.getString(KEY_LAST_FOLDER, getExternalStoragePublicDirectory(DIRECTORY_MUSIC).path)

    private var files = emptyList<File>()
    private var rootFolder: File? = null
    private var isBusy = false

    private lateinit var callback: (song: Song, queueIds: LongArray, title: String) -> Unit

    fun init(callback: (song: Song, queueIds: LongArray, title: String) -> Unit) {
        this.callback = callback
        updateDataSetAsync(File(root))
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ItemHolder {
        val v = viewGroup.inflate<View>(R.layout.item_folder_list)
        return ItemHolder(v)
    }

    override fun onBindViewHolder(itemHolder: ItemHolder, i: Int) {
        val localItem = files[i]
        val song = songsList[i]
        itemHolder.title.text = localItem.name

        if (localItem.isDirectory) {
            val icon = if (GO_UP == localItem.name) {
                icons[1]
            } else {
                icons[0]
            }
            itemHolder.albumArt.setImageDrawable(icon)
        } else {
            Picasso.get()
                    .load(getAlbumArtUri(song.albumId))
                    .error(R.drawable.ic_music_note)
                    .into(itemHolder.albumArt)
        }
    }

    override fun getItemCount() = files.size

    fun updateDataSetAsync(newRoot: File): Boolean {
        if (isBusy) {
            return false
        } else if (GO_UP == newRoot.name) {
            goUpAsync()
            return false
        }
        rootFolder = newRoot
        NavigateTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, rootFolder)
        return true
    }

    private fun goUpAsync(): Boolean {
        if (isBusy) {
            return false
        }
        val parent = rootFolder?.parentFile
        return if (parent != null && parent.canRead()) {
            updateDataSetAsync(parent)
        } else {
            false
        }
    }

    private fun getSongsForFiles(files: List<File>) {
        songsList.clear()
        val newSongs = files.map {
            SongsRepository.getSongFromPath(it.absolutePath, context)
        }
        songsList.addAll(newSongs)
    }

    // TODO don't use AsyncTasks
    private inner class NavigateTask : AsyncTask<File, Void, List<File>>() {

        override fun onPreExecute() {
            super.onPreExecute()
            isBusy = true
        }

        override fun doInBackground(vararg params: File): List<File> {
            val files = FoldersRepository.getMediaFiles(params[0], true)
            getSongsForFiles(files)
            return files
        }

        override fun onPostExecute(files: List<File>) {
            super.onPostExecute(files)
            this@FolderAdapter.files = files
            notifyDataSetChanged()
            isBusy = false
            prefs.edit {
                putString(KEY_LAST_FOLDER, rootFolder!!.path)
            }
        }
    }

    inner class ItemHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        val title: TextView = view.findViewById(R.id.folder_title)
        val albumArt: ImageView = view.findViewById(R.id.album_art)

        init {
            view.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            if (isBusy) return
            val f = files[adapterPosition]

            if (f.isDirectory && updateDataSetAsync(f)) {
                albumArt.setImageDrawable(icons[3])
            } else if (f.isFile) {
                val song = SongsRepository.getSongFromPath(files[adapterPosition].absolutePath, context)
                val listWithoutFirstItem = songsList.subList(1, songsList.size).toSongIds()
                callback(song, listWithoutFirstItem, rootFolder?.name ?: "Folder")
            }
        }
    }
}
