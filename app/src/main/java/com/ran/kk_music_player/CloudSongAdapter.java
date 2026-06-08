package com.ran.kk_music_player;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ran.kk_music_player.databinding.ItemSongBinding;
import com.ran.kk_music_player.WebDavClient.CloudFile;

import java.io.File;
import java.util.List;

public class CloudSongAdapter extends RecyclerView.Adapter<CloudSongAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onFileClick(int position);
        void onFolderClick(int position);
    }

    private final List<CloudFile> files;
    private final OnItemClickListener listener;

    public CloudSongAdapter(List<CloudFile> files, OnItemClickListener listener) {
        this.files = files;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongBinding binding = ItemSongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CloudFile file = files.get(position);
        holder.binding.textTitle.setText(file.name);

        if (file.isDirectory) {
            holder.binding.textArtist.setText(R.string.cloud_directory);
            holder.binding.imageAlbumArt.setImageResource(R.drawable.ic_folder_24);
        } else {
            holder.binding.textArtist.setText(file.getFormattedSize());

            // Check if cover art is cached (from a previous play in PlayerActivity)
            if (file.directUrl != null) {
                Context ctx = holder.binding.getRoot().getContext();
                String cacheKey = String.valueOf(file.directUrl.hashCode());
                File cacheFile = new File(ctx.getCacheDir(), "album_covers/" + cacheKey + ".jpg");
                if (cacheFile.exists()) {
                    Glide.with(ctx)
                            .load(Uri.fromFile(cacheFile))
                            .circleCrop()
                            .placeholder(R.drawable.ic_music_note_24)
                            .error(R.drawable.ic_music_note_24)
                            .into(holder.binding.imageAlbumArt);
                } else {
                    holder.binding.imageAlbumArt.setImageResource(R.drawable.ic_music_note_24);
                }
            } else {
                holder.binding.imageAlbumArt.setImageResource(R.drawable.ic_music_note_24);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                if (files.get(pos).isDirectory) {
                    listener.onFolderClick(pos);
                } else {
                    listener.onFileClick(pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemSongBinding binding;

        public ViewHolder(ItemSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
