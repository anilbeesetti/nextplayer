package com.nextplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.nextplayer.ThumbnailUtils
import com.nextplayer.model.Video
import com.nextplayer.R
import kotlinx.coroutines.launch

class VideoListAdapter(private val videos: List<Video>) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailImageView: ImageView = itemView.findViewById(R.id.thumbnailImageView)
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.video_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        holder.titleTextView.text = video.title
        holder.durationTextView.text = video.duration

        holder.thumbnailImageView.setImageResource(R.drawable.ic_video_placeholder)
        val videoPath = video.path
        val lifecycleOwner = holder.itemView.context as? LifecycleOwner
        lifecycleOwner?.lifecycleScope?.launch {
            val thumbnail = ThumbnailUtils.getVideoThumbnailAsync(videoPath)
            if (thumbnail != null) {
                holder.thumbnailImageView.setImageBitmap(thumbnail)
            }
        }
    }

    override fun getItemCount() = videos.size
}